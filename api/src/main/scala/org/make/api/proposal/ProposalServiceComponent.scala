package org.make.api.proposal

import java.time.ZonedDateTime

import akka.util.Timeout
import org.make.api.technical.IdGeneratorComponent
import org.make.api.userhistory.UserHistoryServiceComponent
import org.make.core.proposal.indexed.{IndexedProposal, Vote, VoteKey}
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}
import org.make.semantic.text.document.Corpus
import org.make.semantic.text.feature.{FeatureExtractor, TokenFT}
import org.make.semantic.text.model.duplicate.SimpleDuplicateDetector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait ProposalServiceComponent {
  def proposalService: ProposalService
}

trait ProposalService {

  def getProposalById(proposalId: ProposalId, requestContext: RequestContext): Future[Option[IndexedProposal]]
  def getEventSourcingProposal(proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]]
  def getDuplicates(userId: UserId,
                    proposalId: ProposalId,
                    requestContext: RequestContext): Future[Seq[IndexedProposal]]
  def search(userId: Option[UserId], query: SearchQuery, requestContext: RequestContext): Future[Seq[IndexedProposal]]
  def propose(user: User, requestContext: RequestContext, createdAt: ZonedDateTime, content: String): Future[ProposalId]
  def update(proposalId: ProposalId,
             requestContext: RequestContext,
             updatedAt: ZonedDateTime,
             content: String): Future[Option[Proposal]]
  def validateProposal(proposalId: ProposalId,
                       moderator: UserId,
                       requestContext: RequestContext,
                       request: ValidateProposalRequest): Future[Option[Proposal]]
  def refuseProposal(proposalId: ProposalId,
                     moderator: UserId,
                     requestContext: RequestContext,
                     request: RefuseProposalRequest): Future[Option[Proposal]]
  def voteProposal(proposalId: ProposalId,
                   userId: Option[UserId],
                   requestContext: RequestContext,
                   voteKey: VoteKey): Future[Option[Vote]]
  def unvoteProposal(proposalId: ProposalId,
                     userId: Option[UserId],
                     requestContext: RequestContext,
                     voteKey: VoteKey): Future[Option[Vote]]
}

trait DefaultProposalServiceComponent extends ProposalServiceComponent {
  this: IdGeneratorComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryServiceComponent
    with ProposalSearchEngineComponent
    with DuplicateDetectorConfigurationComponent =>

  override lazy val proposalService = new ProposalService {

    implicit private val defaultTimeout: Timeout = new Timeout(5.seconds)

    private val duplicateDetector: SimpleDuplicateDetector =
      new SimpleDuplicateDetector(lang = "fr", targetFeature = TokenFT)

    override def getProposalById(proposalId: ProposalId,
                                 requestContext: RequestContext): Future[Option[IndexedProposal]] = {
      proposalCoordinatorService.viewProposal(proposalId, requestContext)
      elasticsearchAPI.findProposalById(proposalId)
    }

    override def getEventSourcingProposal(proposalId: ProposalId,
                                          requestContext: RequestContext): Future[Option[Proposal]] = {
      proposalCoordinatorService.viewProposal(proposalId, requestContext)
    }

    override def search(maybeUserId: Option[UserId],
                        query: SearchQuery,
                        requestContext: RequestContext): Future[Seq[IndexedProposal]] = {
      maybeUserId.foreach { userId =>
        userHistoryService.logHistory(
          LogSearchProposalsEvent(
            userId,
            requestContext,
            UserAction(DateHelper.now(), LogSearchProposalsEvent.action, SearchParameters(query))
          )
        )
      }
      elasticsearchAPI.searchProposals(query)
    }

    override def propose(user: User,
                         requestContext: RequestContext,
                         createdAt: ZonedDateTime,
                         content: String): Future[ProposalId] = {

      proposalCoordinatorService.propose(
        ProposeCommand(
          proposalId = idGenerator.nextProposalId(),
          requestContext = requestContext,
          user = user,
          createdAt = createdAt,
          content = content
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
                                  request: ValidateProposalRequest): Future[Option[Proposal]] = {

      proposalCoordinatorService.accept(
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
    }

    override def refuseProposal(proposalId: ProposalId,
                                moderator: UserId,
                                requestContext: RequestContext,
                                request: RefuseProposalRequest): Future[Option[Proposal]] = {

      proposalCoordinatorService.refuse(
        RefuseProposalCommand(
          proposalId = proposalId,
          moderator = moderator,
          requestContext = requestContext,
          sendNotificationEmail = request.sendNotificationEmail,
          refusalReason = request.refusalReason
        )
      )
    }

    override def getDuplicates(userId: UserId,
                               proposalId: ProposalId,
                               requestContext: RequestContext): Future[Seq[IndexedProposal]] = {
      userHistoryService.logHistory(
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
            .map({ possibleDuplicates: Seq[IndexedProposal] =>
              // do duplicate detection
              val corpus = Corpus(
                rawCorpus = possibleDuplicates.filter(_.id != proposalId).map(_.content),
                extractor = new FeatureExtractor(Seq.empty),
                lang = indexedProposal.language,
                maybeCorpusMeta = Some(possibleDuplicates)
              )
              duplicateDetector
                .getSimilar(indexedProposal.content, corpus, duplicateDetectorConfiguration.maxResults)
                .flatMap(_.targetDoc.document.meta)
            })

        case None => Future.successful(Seq.empty)
      }
    }

    override def voteProposal(proposalId: ProposalId,
                              maybeUserId: Option[UserId],
                              requestContext: RequestContext,
                              voteKey: VoteKey): Future[Option[Vote]] = {
      maybeUserId.foreach { userId =>
        userHistoryService.logHistory(
          LogUserVoteEvent(
            userId,
            requestContext,
            UserAction(DateHelper.now(), LogUserVoteEvent.action, UserVote(voteKey))
          )
        )
      }
      proposalCoordinatorService.vote(
        VoteProposalCommand(
          proposalId = proposalId,
          maybeUserId = maybeUserId,
          requestContext = requestContext,
          voteKey = voteKey
        )
      )
    }

    override def unvoteProposal(proposalId: ProposalId,
                                maybeUserId: Option[UserId],
                                requestContext: RequestContext,
                                voteKey: VoteKey): Future[Option[Vote]] = {
      maybeUserId.foreach { userId =>
        userHistoryService.logHistory(
          LogUserUnvoteEvent(
            userId,
            requestContext,
            UserAction(DateHelper.now(), LogUserUnvoteEvent.action, UserVote(voteKey))
          )
        )
      }
      proposalCoordinatorService.unvote(
        UnvoteProposalCommand(
          proposalId = proposalId,
          maybeUserId = maybeUserId,
          requestContext = requestContext,
          voteKey = voteKey
        )
      )
    }
  }

}
