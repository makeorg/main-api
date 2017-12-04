package org.make.api.proposal

import java.time.ZonedDateTime

import akka.Done
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.sessionhistory._
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ReadJournalComponent}
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
import org.make.semantic.text.feature.wordvec.WordVecOption
import org.make.semantic.text.feature.{FeatureExtractor, WordVecFT}
import org.make.semantic.text.model.duplicate.{DuplicateDetector, SimilarDocResult}

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
                    requestContext: RequestContext): Future[ProposalsSearchResult]

  def search(userId: Option[UserId],
             query: SearchQuery,
             maybeSeed: Option[Int],
             requestContext: RequestContext): Future[ProposalsSearchResult]

  def searchForUser(userId: Option[UserId],
                    query: SearchQuery,
                    maybeSeed: Option[Int],
                    requestContext: RequestContext): Future[ProposalsResultSeededResponse]

  def propose(user: User,
              requestContext: RequestContext,
              createdAt: ZonedDateTime,
              content: String,
              theme: Option[ThemeId]): Future[ProposalId]

  // toDo: add theme
  def update(proposalId: ProposalId,
             moderator: UserId,
             requestContext: RequestContext,
             updatedAt: ZonedDateTime,
             request: UpdateProposalRequest): Future[Option[ProposalResponse]]

  def validateProposal(proposalId: ProposalId,
                       moderator: UserId,
                       requestContext: RequestContext,
                       request: ValidateProposalRequest): Future[Option[ProposalResponse]]

  def refuseProposal(proposalId: ProposalId,
                     moderator: UserId,
                     requestContext: RequestContext,
                     request: RefuseProposalRequest): Future[Option[ProposalResponse]]

  def postponeProposal(proposalId: ProposalId,
                       moderator: UserId,
                       requestContext: RequestContext): Future[Option[ProposalResponse]]

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

  def lockProposal(proposalId: ProposalId, moderatorId: UserId, requestContext: RequestContext): Future[Option[UserId]]

  def removeProposalFromCluster(proposalId: ProposalId): Future[Done]

  def clearSimilarProposals(): Future[Done]
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
    with ReadJournalComponent
    with ActorSystemComponent
    with UserServiceComponent =>

  override lazy val proposalService: ProposalService = new ProposalService {

    implicit private val defaultTimeout: Timeout = new Timeout(5.seconds)

    private val duplicateDetector: DuplicateDetector[IndexedProposal] =
      new DuplicateDetector(lang = "fr", 0.0)
    private val featureExtractor: FeatureExtractor = new FeatureExtractor(Seq((WordVecFT, Some(WordVecOption))))

    override def removeProposalFromCluster(proposalId: ProposalId): Future[Done] = {
      implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
      readJournal
        .currentPersistenceIds()
        .map { id =>
          proposalCoordinatorService.removeProposalFromCluster(ProposalId(id), proposalId)
          Done
        }
        .runForeach { _ =>
          {}
        }
    }

    override def clearSimilarProposals(): Future[Done] = {
      implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
      readJournal
        .currentPersistenceIds()
        .map { id =>
          proposalCoordinatorService.clearSimilarProposals(ProposalId(id))
          Done
        }
        .runForeach { _ =>
          {}
        }
    }

    override def getProposalById(proposalId: ProposalId,
                                 requestContext: RequestContext): Future[Option[IndexedProposal]] = {
      proposalCoordinatorService.viewProposal(proposalId, requestContext)
      elasticsearchProposalAPI.findProposalById(proposalId)
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
            context = proposal.creationContext,
            createdAt = proposal.createdAt,
            updatedAt = proposal.updatedAt,
            events = events,
            similarProposals = proposal.similarProposals
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
                        maybeSeed: Option[Int],
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
      elasticsearchProposalAPI.searchProposals(query, maybeSeed)
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
                               seed: Option[Int],
                               requestContext: RequestContext): Future[ProposalsResultSeededResponse] = {

      search(maybeUserId, query, seed, requestContext).flatMap { searchResult =>
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
      }.map { proposalResultResponse =>
        ProposalsResultSeededResponse(proposalResultResponse.total, proposalResultResponse.results, seed)
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
                        moderator: UserId,
                        requestContext: RequestContext,
                        updatedAt: ZonedDateTime,
                        request: UpdateProposalRequest): Future[Option[ProposalResponse]] = {

      val updatedProposal = proposalCoordinatorService.update(
        UpdateProposalCommand(
          moderator = moderator,
          proposalId = proposalId,
          requestContext = requestContext,
          updatedAt = updatedAt,
          newContent = request.newContent,
          theme = request.theme,
          labels = request.labels,
          tags = request.tags,
          similarProposals = request.similarProposals
        )
      )
      val futureMaybeProposalAuthor: Future[Option[(Proposal, User)]] = (
        for {
          proposal: Proposal <- OptionT(updatedProposal)
          author: User       <- OptionT(userService.getUser(proposal.author))
        } yield (proposal, author)
      ).value

      futureMaybeProposalAuthor.flatMap {
        case Some((proposal, author)) => proposalResponse(proposal, author)
        case None                     => Future.successful(None)
      }
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

    override def postponeProposal(proposalId: ProposalId,
                                  moderator: UserId,
                                  requestContext: RequestContext): Future[Option[ProposalResponse]] = {

      def postponedProposal = proposalCoordinatorService.postpone(
        PostponeProposalCommand(proposalId = proposalId, moderator = moderator, requestContext = requestContext)
      )

      val futureMaybeProposalAuthor: Future[Option[(Proposal, User)]] = (
        for {
          proposal: Proposal <- OptionT(postponedProposal)
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
                               requestContext: RequestContext): Future[ProposalsSearchResult] = {
      userHistoryCoordinatorService.logHistory(
        LogGetProposalDuplicatesEvent(
          userId,
          requestContext,
          UserAction(DateHelper.now(), LogGetProposalDuplicatesEvent.action, proposalId)
        )
      )

      elasticsearchProposalAPI.findProposalById(proposalId).flatMap {
        case Some(indexedProposal) =>
          elasticsearchProposalAPI
            .searchProposals(
              SearchQuery(
                // TODO add language filter
                filters = Some(
                  SearchFilters(
                    content = Some(ContentSearchFilter(text = indexedProposal.content)),
                    theme = requestContext.currentTheme.map(themeId => ThemeSearchFilter(themeIds = Seq(themeId.value))),
                    context = requestContext.operation.map(ope      => ContextSearchFilter(operation = Some(ope)))
                  )
                )
              )
            )
            .map({ response =>
              // do duplicate detection
              val (
                predictedResults: Seq[SimilarDocResult[IndexedProposal]],
                predictedDuplicates: Seq[IndexedProposal]
              ) = getPredictedDuplicateResults(indexedProposal, response.results)

              eventBusService.publish(
                PredictDuplicate(
                  proposalId = proposalId,
                  predictedDuplicates = predictedDuplicates.map(_.id),
                  predictedScores = predictedResults.map(_.score),
                  algoLabel = duplicateDetector.getClass.getSimpleName
                )
              )

              ProposalsSearchResult(predictedDuplicates.size, predictedDuplicates)
            })
        case None => Future.successful(ProposalsSearchResult.empty)
      }
    }

    private def getPredictedDuplicateResults(
      indexedProposal: IndexedProposal,
      candidates: Seq[IndexedProposal]
    ): (Seq[SimilarDocResult[IndexedProposal]], Seq[IndexedProposal]) = {
      val corpus = Corpus(
        rawCorpus = candidates.filter(_.id != indexedProposal.id).map(_.content),
        extractor = featureExtractor,
        lang = indexedProposal.language,
        maybeCorpusMeta = Some(candidates.filter(_.id != indexedProposal.id))
      )

      val predictedResults: Seq[SimilarDocResult[IndexedProposal]] = duplicateDetector
        .getSimilar(indexedProposal.content, corpus, duplicateDetectorConfiguration.maxResults)

      val predictedDuplicates: Seq[IndexedProposal] =
        predictedResults.flatMap(_.candidateDoc.document.meta)

      (predictedResults, predictedDuplicates)
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

    override def lockProposal(proposalId: ProposalId,
                              moderatorId: UserId,
                              requestContext: RequestContext): Future[Option[UserId]] = {
      userService.getUser(moderatorId).flatMap {
        case None => Future.successful(None)
        case Some(moderator) =>
          proposalCoordinatorService.lock(
            LockProposalCommand(
              proposalId = proposalId,
              moderatorId = moderatorId,
              moderatorName = moderator.firstName,
              requestContext = requestContext
            )
          )
      }
    }
  }

}
