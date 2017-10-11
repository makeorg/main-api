package org.make.api.proposal

import java.time.ZonedDateTime

import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.sessionhistory._
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.api.userhistory.UserHistoryActor.RequestVoteValues
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.proposal.indexed.{IndexedProposal, ProposalsSearchResult}
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.reference.ThemeId
import org.make.core.user._
import org.make.core.{CirceFormatters, DateHelper, RequestContext}
import org.make.semantic.text.document.Corpus
import org.make.semantic.text.feature.{FeatureExtractor, TokenFT}
import org.make.semantic.text.model.duplicate.{SimilarDocResult, SimpleDuplicateDetector}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait ProposalServiceComponent {
  def proposalService: ProposalService
}

trait ProposalService {

  def getProposalById(proposalId: ProposalId, requestContext: RequestContext): Future[Option[IndexedProposal]]
  def getModerationProposalById(proposalId: ProposalId): Future[Option[ProposalResponse]]
  def getEventSourcingProposal(proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]]
  def getDuplicates(userId: UserId,
                    proposalId: ProposalId,
                    requestContext: RequestContext): Future[Seq[IndexedProposal]]
  def search(userId: Option[UserId], query: SearchQuery, requestContext: RequestContext): Future[ProposalsSearchResult]
  def searchForUser(userId: Option[UserId],
                    query: SearchQuery,
                    requestContext: RequestContext): Future[ProposalsResultResponse]
  def propose(user: User,
              requestContext: RequestContext,
              createdAt: ZonedDateTime,
              content: String,
              theme: Option[ThemeId]): Future[ProposalId]
  // toDo: add theme
  def update(proposalId: ProposalId,
             requestContext: RequestContext,
             updatedAt: ZonedDateTime,
             content: String): Future[Option[Proposal]]
  def validateProposal(proposalId: ProposalId,
                       moderator: UserId,
                       requestContext: RequestContext,
                       request: ValidateProposalRequest): Future[Option[ProposalResponse]]
  def refuseProposal(proposalId: ProposalId,
                     moderator: UserId,
                     requestContext: RequestContext,
                     request: RefuseProposalRequest): Future[Option[ProposalResponse]]
  def voteProposal(proposalId: ProposalId,
                   maybeUserId: Option[UserId],
                   requestContext: RequestContext,
                   voteKey: VoteKey): Future[Option[Vote]]
  def unvoteProposal(proposalId: ProposalId,
                     maybeUserId: Option[UserId],
                     requestContext: RequestContext,
                     voteKey: VoteKey): Future[Option[Vote]]
  def qualifyVote(proposalId: ProposalId,
                  maybeUserId: Option[UserId],
                  requestContext: RequestContext,
                  voteKey: VoteKey,
                  qualificationKey: QualificationKey): Future[Option[Qualification]]
  def unqualifyVote(proposalId: ProposalId,
                    maybeUserId: Option[UserId],
                    requestContext: RequestContext,
                    voteKey: VoteKey,
                    qualificationKey: QualificationKey): Future[Option[Qualification]]
}

trait DefaultProposalServiceComponent extends ProposalServiceComponent with CirceFormatters with StrictLogging {
  this: IdGeneratorComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with ProposalSearchEngineComponent
    with DuplicateDetectorConfigurationComponent
    with EventBusServiceComponent
    with UserServiceComponent =>

  override lazy val proposalService: ProposalService = new ProposalService {

    implicit private val defaultTimeout: Timeout = new Timeout(5.seconds)

    private val duplicateDetector: SimpleDuplicateDetector =
      new SimpleDuplicateDetector(lang = "fr", targetFeature = TokenFT)

    override def getProposalById(proposalId: ProposalId,
                                 requestContext: RequestContext): Future[Option[IndexedProposal]] = {
      proposalCoordinatorService.viewProposal(proposalId, requestContext)
      elasticsearchAPI.findProposalById(proposalId)
    }

    private def proposalResponse(proposal: Proposal, author: User): Future[Option[ProposalResponse]] = {
      val eventsUserIds: Seq[UserId] = proposal.events.map(_.user).distinct
      val futureEventsUsers: Future[Seq[UserResponse]] =
        userService.getUsersByUserIds(eventsUserIds).map(_.map(UserResponse.apply))

      futureEventsUsers.map { eventsUsers =>
        val events: Seq[ProposalActionResponse] = proposal.events.map { action =>
          ProposalActionResponse(
            date = action.date,
            user = eventsUsers.find(_.userId.value == action.user.value),
            actionType = action.actionType,
            arguments = action.arguments
          )
        }
        Some(
          ProposalResponse(
            proposalId = proposal.proposalId,
            slug = proposal.slug,
            content = proposal.content,
            author = UserResponse(author),
            labels = proposal.labels,
            theme = proposal.theme,
            status = proposal.status,
            refusalReason = proposal.refusalReason,
            tags = proposal.tags,
            votes = proposal.votes,
            creationContext = proposal.creationContext,
            createdAt = proposal.createdAt,
            updatedAt = proposal.updatedAt,
            events = events
          )
        )
      }
    }

    override def getModerationProposalById(proposalId: ProposalId): Future[Option[ProposalResponse]] = {
      val futureMaybeProposalAuthor: Future[Option[(Proposal, User)]] = (
        for {
          proposal: Proposal <- OptionT(proposalCoordinatorService.getProposal(proposalId))
          author: User       <- OptionT(userService.getUser(proposal.author))
        } yield (proposal, author)
      ).value

      futureMaybeProposalAuthor.flatMap {
        case Some((proposal, author)) => proposalResponse(proposal, author)
        case None                     => Future.successful(None)
      }
    }

    override def getEventSourcingProposal(proposalId: ProposalId,
                                          requestContext: RequestContext): Future[Option[Proposal]] = {
      proposalCoordinatorService.viewProposal(proposalId, requestContext)
    }

    override def search(maybeUserId: Option[UserId],
                        query: SearchQuery,
                        requestContext: RequestContext): Future[ProposalsSearchResult] = {
      query.filters.foreach(_.content.foreach { content =>
        maybeUserId match {
          case Some(userId) =>
            userHistoryCoordinatorService.logHistory(
              LogUserSearchProposalsEvent(
                userId,
                requestContext,
                UserAction(DateHelper.now(), LogUserSearchProposalsEvent.action, UserSearchParameters(content.text))
              )
            )
          case None =>
            sessionHistoryCoordinatorService.logHistory(
              LogSessionSearchProposalsEvent(
                requestContext.sessionId,
                requestContext,
                SessionAction(
                  DateHelper.now(),
                  LogSessionSearchProposalsEvent.action,
                  SessionSearchParameters(content.text)
                )
              )
            )
        }
      })
      elasticsearchAPI.searchProposals(query)
    }

    def mergeVoteResults(maybeUserId: Option[UserId],
                         searchResult: ProposalsSearchResult,
                         votes: Map[ProposalId, VoteAndQualifications]): ProposalsResultResponse = {
      val proposals = searchResult.results.map { indexedProposal =>
        ProposalResult.apply(
          indexedProposal,
          myProposal = maybeUserId.contains(indexedProposal.userId),
          votes.get(indexedProposal.id)
        )
      }
      ProposalsResultResponse(searchResult.total, proposals)
    }

    override def searchForUser(maybeUserId: Option[UserId],
                               query: SearchQuery,
                               requestContext: RequestContext): Future[ProposalsResultResponse] = {

      search(maybeUserId, query, requestContext).flatMap { searchResult =>
        maybeUserId match {
          case Some(userId) =>
            userHistoryCoordinatorService
              .retrieveVoteAndQualifications(RequestVoteValues(userId, searchResult.results.map(_.id)))
              .map(votes => mergeVoteResults(maybeUserId, searchResult, votes))
          case None =>
            sessionHistoryCoordinatorService
              .retrieveVoteAndQualifications(
                RequestSessionVoteValues(sessionId = requestContext.sessionId, searchResult.results.map(_.id))
              )
              .map(votes => mergeVoteResults(maybeUserId, searchResult, votes))
        }
      }
    }

    override def propose(user: User,
                         requestContext: RequestContext,
                         createdAt: ZonedDateTime,
                         content: String,
                         theme: Option[ThemeId]): Future[ProposalId] = {

      proposalCoordinatorService.propose(
        ProposeCommand(
          proposalId = idGenerator.nextProposalId(),
          requestContext = requestContext,
          user = user,
          createdAt = createdAt,
          content = content,
          theme = theme
        )
      )
    }

    override def update(proposalId: ProposalId,
                        requestContext: RequestContext,
                        updatedAt: ZonedDateTime,
                        content: String): Future[Option[Proposal]] = {

      proposalCoordinatorService.update(
        UpdateProposalCommand(
          proposalId = proposalId,
          requestContext = requestContext,
          updatedAt = updatedAt,
          content = content
        )
      )
    }

    override def validateProposal(proposalId: ProposalId,
                                  moderator: UserId,
                                  requestContext: RequestContext,
                                  request: ValidateProposalRequest): Future[Option[ProposalResponse]] = {

      def acceptedProposal = proposalCoordinatorService.accept(
        AcceptProposalCommand(
          proposalId = proposalId,
          moderator = moderator,
          requestContext = requestContext,
          sendNotificationEmail = request.sendNotificationEmail,
          newContent = request.newContent,
          theme = request.theme,
          labels = request.labels,
          tags = request.tags,
          similarProposals = request.similarProposals
        )
      )
      val futureMaybeProposalAuthor: Future[Option[(Proposal, User)]] = (
        for {
          proposal: Proposal <- OptionT(acceptedProposal)
          author: User       <- OptionT(userService.getUser(proposal.author))
        } yield (proposal, author)
      ).value

      futureMaybeProposalAuthor.flatMap {
        case Some((proposal, author)) => proposalResponse(proposal, author)
        case None                     => Future.successful(None)
      }
    }

    override def refuseProposal(proposalId: ProposalId,
                                moderator: UserId,
                                requestContext: RequestContext,
                                request: RefuseProposalRequest): Future[Option[ProposalResponse]] = {

      def refusedProposal = proposalCoordinatorService.refuse(
        RefuseProposalCommand(
          proposalId = proposalId,
          moderator = moderator,
          requestContext = requestContext,
          sendNotificationEmail = request.sendNotificationEmail,
          refusalReason = request.refusalReason
        )
      )
      val futureMaybeProposalAuthor: Future[Option[(Proposal, User)]] = (
        for {
          proposal: Proposal <- OptionT(refusedProposal)
          author: User       <- OptionT(userService.getUser(proposal.author))
        } yield (proposal, author)
      ).value

      futureMaybeProposalAuthor.flatMap {
        case Some((proposal, author)) => proposalResponse(proposal, author)
        case None                     => Future.successful(None)
      }
    }

    override def getDuplicates(userId: UserId,
                               proposalId: ProposalId,
                               requestContext: RequestContext): Future[Seq[IndexedProposal]] = {
      userHistoryCoordinatorService.logHistory(
        LogGetProposalDuplicatesEvent(
          userId,
          requestContext,
          UserAction(DateHelper.now(), LogGetProposalDuplicatesEvent.action, proposalId)
        )
      )

      elasticsearchAPI.findProposalById(proposalId).flatMap {
        case Some(indexedProposal) =>
          elasticsearchAPI
            .searchProposals(
              SearchQuery(
                // TODO add language filter
                filters = Some(SearchFilters(content = Some(ContentSearchFilter(text = indexedProposal.content))))
              )
            )
            .map({ response =>
              val possibleDuplicates: Seq[IndexedProposal] = response.results
              // do duplicate detection
              val corpus = Corpus(
                rawCorpus = possibleDuplicates.filter(_.id != proposalId).map(_.content),
                extractor = new FeatureExtractor(Seq.empty),
                lang = indexedProposal.language,
                maybeCorpusMeta = Some(possibleDuplicates.filter(_.id != proposalId))
              )
              val predictedResults: Seq[SimilarDocResult[IndexedProposal]] = duplicateDetector
                .getSimilar(indexedProposal.content, corpus, duplicateDetectorConfiguration.maxResults)
              val predictedDuplicates: Seq[IndexedProposal] =
                predictedResults.flatMap(_.targetDoc.document.meta)

              eventBusService.publish(
                PredictDuplicate(
                  proposalId = proposalId,
                  predictedDuplicates = predictedDuplicates.map(_.id),
                  predictedScores = predictedResults.map(_.score),
                  algoLabel = duplicateDetector.getClass.getSimpleName
                )
              )

              predictedDuplicates
            })

        case None => Future.successful(Seq.empty)
      }
    }

    private def retrieveVoteHistory(proposalId: ProposalId,
                                    maybeUserId: Option[UserId],
                                    requestContext: RequestContext) = {
      val votesHistory = maybeUserId match {
        case Some(userId) =>
          userHistoryCoordinatorService
            .retrieveVoteAndQualifications(RequestVoteValues(userId, Seq(proposalId)))
        case None =>
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(
              RequestSessionVoteValues(sessionId = requestContext.sessionId, proposalIds = Seq(proposalId))
            )
      }
      votesHistory
    }

    override def voteProposal(proposalId: ProposalId,
                              maybeUserId: Option[UserId],
                              requestContext: RequestContext,
                              voteKey: VoteKey): Future[Option[Vote]] = {

      retrieveVoteHistory(proposalId, maybeUserId, requestContext).flatMap(
        votes =>
          proposalCoordinatorService.vote(
            VoteProposalCommand(
              proposalId = proposalId,
              maybeUserId = maybeUserId,
              requestContext = requestContext,
              voteKey = voteKey,
              vote = votes.get(proposalId)
            )
        )
      )

    }

    override def unvoteProposal(proposalId: ProposalId,
                                maybeUserId: Option[UserId],
                                requestContext: RequestContext,
                                voteKey: VoteKey): Future[Option[Vote]] = {

      retrieveVoteHistory(proposalId, maybeUserId, requestContext).flatMap(
        votes =>
          proposalCoordinatorService.unvote(
            UnvoteProposalCommand(
              proposalId = proposalId,
              maybeUserId = maybeUserId,
              requestContext = requestContext,
              voteKey = voteKey,
              vote = votes.get(proposalId)
            )
        )
      )
    }

    override def qualifyVote(proposalId: ProposalId,
                             maybeUserId: Option[UserId],
                             requestContext: RequestContext,
                             voteKey: VoteKey,
                             qualificationKey: QualificationKey): Future[Option[Qualification]] = {

      retrieveVoteHistory(proposalId, maybeUserId, requestContext).flatMap(
        votes =>
          proposalCoordinatorService.qualification(
            QualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = maybeUserId,
              requestContext = requestContext,
              voteKey = voteKey,
              qualificationKey = qualificationKey,
              vote = votes.get(proposalId)
            )
        )
      )

    }

    override def unqualifyVote(proposalId: ProposalId,
                               maybeUserId: Option[UserId],
                               requestContext: RequestContext,
                               voteKey: VoteKey,
                               qualificationKey: QualificationKey): Future[Option[Qualification]] = {

      retrieveVoteHistory(proposalId, maybeUserId, requestContext).flatMap(
        votes =>
          proposalCoordinatorService.unqualification(
            UnqualifyVoteCommand(
              proposalId = proposalId,
              maybeUserId = maybeUserId,
              requestContext = requestContext,
              voteKey = voteKey,
              qualificationKey = qualificationKey,
              vote = votes.get(proposalId)
            )
        )
      )
    }
  }

}
