package org.make.api.proposal

import java.time.ZonedDateTime

import akka.Done
import akka.stream.ActorMaterializer
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.idea.IdeaServiceComponent
import org.make.api.sessionhistory._
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ReadJournalComponent}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.api.userhistory.UserHistoryActor.RequestVoteValues
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.{IndexedProposal, ProposalsSearchResult}
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.reference.ThemeId
import org.make.core.user._
import org.make.core.{CirceFormatters, DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait ProposalServiceComponent {
  def proposalService: ProposalService
}

trait ProposalService {

  def getProposalById(proposalId: ProposalId, requestContext: RequestContext): Future[Option[IndexedProposal]]

  def getModerationProposalById(proposalId: ProposalId): Future[Option[ProposalResponse]]

  def getEventSourcingProposal(proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]]

  def getDuplicates(userId: UserId,
                    proposalId: ProposalId,
                    requestContext: RequestContext): Future[Seq[DuplicateResponse]]

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
              operation: Option[OperationId],
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

  def patchProposal(proposalId: ProposalId,
                    userId: UserId,
                    requestContext: RequestContext,
                    changes: PatchProposalRequest): Future[Option[ProposalResponse]]

  def changeProposalsIdea(proposalIds: Seq[ProposalId], moderatorId: UserId, ideaId: IdeaId): Future[Seq[Proposal]]
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
    with UserServiceComponent
    with IdeaServiceComponent =>

  override lazy val proposalService: ProposalService = new ProposalService {

    implicit private val defaultTimeout: Timeout = new Timeout(5.seconds)

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
      val futureIdeaProposals: Future[Seq[IndexedProposal]] = getIdeaProposals(proposal)

      futureEventsUsers.flatMap { eventsUsers =>
        futureIdeaProposals.map { ideaProposals =>
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
              similarProposals = proposal.similarProposals,
              idea = proposal.idea,
              ideaProposals = ideaProposals,
              operationId = proposal.operation
            )
          )
        }
      }
    }

    private def getIdeaProposals(proposal: Proposal): Future[Seq[IndexedProposal]] = {
      proposal.idea match {
        case Some(ideaId) =>
          elasticsearchProposalAPI
            .countProposals(SearchQuery(filters = Some(SearchFilters(idea = Some(IdeaSearchFilter(ideaId))))))
            .flatMap { countProposals =>
              elasticsearchProposalAPI
                .searchProposals(
                  SearchQuery(
                    filters = Some(SearchFilters(idea = Some(IdeaSearchFilter(ideaId)))),
                    limit = Some(countProposals)
                  )
                )
                .map(_.results.filter(ideaProposal => proposal.proposalId.value != ideaProposal.id.value))
            }
        case None => Future.successful(Seq.empty)
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
                         operation: Option[OperationId],
                         theme: Option[ThemeId]): Future[ProposalId] = {

      proposalCoordinatorService.propose(
        ProposeCommand(
          proposalId = idGenerator.nextProposalId(),
          requestContext = requestContext,
          user = user,
          createdAt = createdAt,
          content = content,
          operation = operation,
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
          similarProposals = request.similarProposals,
          idea = request.idea,
          operation = request.operation
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
          similarProposals = request.similarProposals,
          idea = request.idea,
          operation = request.operation
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
                               requestContext: RequestContext): Future[Seq[DuplicateResponse]] = {
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
              SearchQuery( // TODO add language filter
                filters = Some(
                  SearchFilters(
                    content = Some(ContentSearchFilter(text = indexedProposal.content)),
                    theme = requestContext.currentTheme.map(themeId => ThemeSearchFilter(themeIds = Seq(themeId))),
                    context = requestContext.operationId.map(ope    => ContextSearchFilter(operation = Some(ope)))
                  )
                )
              )
            )
            .flatMap { response =>
              DuplicateAlgorithm
                .getDuplicates(
                  indexedProposal,
                  response.results,
                  (proposalId: ProposalId) => {
                    proposalCoordinatorService.viewProposal(proposalId, requestContext)
                  },
                  duplicateDetectorConfiguration.maxResults
                )
                .flatMap {
                  case duplicates: Seq[DuplicateResult] =>
                    eventBusService.publish(
                      PredictDuplicate(
                        proposalId = proposalId,
                        predictedDuplicates = duplicates.map(_.proposal.proposalId),
                        predictedScores = duplicates.map(_.score),
                        algoLabel = DuplicateAlgorithm.getModelName
                      )
                    )
                    formatDuplicateResults(duplicates)
                }
            }
        case None => Future.successful(Seq.empty)
      }
    }

    private def formatDuplicateResult(duplicateResult: DuplicateResult): Future[DuplicateResponse] = {
      duplicateResult.proposal.idea match {
        case Some(ideaId) =>
          ideaService.fetchOne(ideaId).map {
            case Some(idea) =>
              DuplicateResponse(
                idea.ideaId,
                idea.name,
                duplicateResult.proposal.proposalId,
                duplicateResult.proposal.content,
                duplicateResult.score
              )
            case None =>
              DuplicateResponse(
                ideaId,
                "Not found",
                duplicateResult.proposal.proposalId,
                duplicateResult.proposal.content,
                duplicateResult.score
              )
          }
        case None =>
          Future.successful(
            DuplicateResponse(
              IdeaId("No Idea"),
              "No Idea",
              duplicateResult.proposal.proposalId,
              duplicateResult.proposal.content,
              duplicateResult.score
            )
          )
      }
    }

    private def formatDuplicateResults(duplicates: Seq[DuplicateResult]): Future[Seq[DuplicateResponse]] = {
      Future.traverse(duplicates) {
        case duplicate => formatDuplicateResult(duplicate)
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

    override def patchProposal(proposalId: ProposalId,
                               userId: UserId,
                               requestContext: RequestContext,
                               changes: PatchProposalRequest): Future[Option[ProposalResponse]] = {

      val patchResult: Future[Option[Proposal]] = proposalCoordinatorService
        .patch(
          PatchProposalCommand(
            proposalId = proposalId,
            userId = userId,
            changes = changes,
            requestContext = requestContext
          )
        )

      val futureMaybeProposalAuthor: Future[Option[(Proposal, User)]] = (for {
        proposal: Proposal <- OptionT(patchResult)
        author: User       <- OptionT(userService.getUser(proposal.author))
      } yield (proposal, author)).value

      futureMaybeProposalAuthor.flatMap {
        case Some((proposal, author)) => proposalResponse(proposal, author)
        case None                     => Future.successful(None)
      }

    }

    override def changeProposalsIdea(proposalIds: Seq[ProposalId],
                                     moderatorId: UserId,
                                     ideaId: IdeaId): Future[Seq[Proposal]] = {
      Future
        .sequence(proposalIds.map { proposalId =>
          proposalCoordinatorService.patch(
            PatchProposalCommand(
              proposalId = proposalId,
              userId = moderatorId,
              changes = PatchProposalRequest(ideaId = Some(ideaId)),
              requestContext = RequestContext.empty
            )
          )
        })
        .map(_.flatten)
    }
  }

}
