package org.make.api.proposal

import java.time.ZonedDateTime

import akka.util.Timeout
import org.make.api.technical.IdGeneratorComponent
import org.make.api.userhistory.UserHistoryServiceComponent
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext}

import scala.concurrent.Future
import scala.concurrent.duration._

trait ProposalServiceComponent {
  def proposalService: ProposalService
}

trait ProposalService {

  def getProposalById(proposalId: ProposalId, requestContext: RequestContext): Future[Option[IndexedProposal]]
  def getEventSourcingProposal(proposalId: ProposalId, context: RequestContext): Future[Option[Proposal]]
  def search(userId: Option[UserId], query: SearchQuery, context: RequestContext): Future[Seq[IndexedProposal]]
  def propose(user: User, context: RequestContext, createdAt: ZonedDateTime, content: String): Future[ProposalId]
  def update(proposalId: ProposalId,
             context: RequestContext,
             updatedAt: ZonedDateTime,
             content: String): Future[Option[Proposal]]

  def validateProposal(proposalId: ProposalId,
                       moderator: UserId,
                       context: RequestContext,
                       request: ValidateProposalRequest): Future[Proposal]
}

trait DefaultProposalServiceComponent extends ProposalServiceComponent {
  this: IdGeneratorComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryServiceComponent
    with ProposalSearchEngineComponent =>

  override lazy val proposalService = new ProposalService {

    implicit private val defaultTimeout: Timeout = new Timeout(5.seconds)

    override def getProposalById(proposalId: ProposalId, context: RequestContext): Future[Option[IndexedProposal]] = {
      proposalCoordinatorService.viewProposal(proposalId, context)
      elasticsearchAPI.findProposalById(proposalId)
    }

    override def getEventSourcingProposal(proposalId: ProposalId, context: RequestContext): Future[Option[Proposal]] = {
      proposalCoordinatorService.viewProposal(proposalId, context)
    }

    override def search(maybeUserId: Option[UserId],
                        query: SearchQuery,
                        context: RequestContext): Future[Seq[IndexedProposal]] = {
      maybeUserId.foreach { userId =>
        userHistoryService.logHistory(
          LogSearchProposalsEvent(
            userId,
            context,
            UserAction(DateHelper.now(), LogSearchProposalsEvent.action, SearchParameters(query))
          )
        )
      }
      elasticsearchAPI.searchProposals(query)
    }

    override def propose(user: User,
                         context: RequestContext,
                         createdAt: ZonedDateTime,
                         content: String): Future[ProposalId] = {

      proposalCoordinatorService.propose(
        ProposeCommand(
          proposalId = idGenerator.nextProposalId(),
          context = context,
          user = user,
          createdAt = createdAt,
          content = content
        )
      )
    }

    override def update(proposalId: ProposalId,
                        context: RequestContext,
                        updatedAt: ZonedDateTime,
                        content: String): Future[Option[Proposal]] = {

      proposalCoordinatorService.update(
        UpdateProposalCommand(proposalId = proposalId, context = context, updatedAt = updatedAt, content = content)
      )
    }

    override def validateProposal(proposalId: ProposalId,
                                  moderator: UserId,
                                  context: RequestContext,
                                  request: ValidateProposalRequest): Future[Proposal] = {

      proposalCoordinatorService.accept(
        AcceptProposalCommand(
          proposalId = proposalId,
          moderator = moderator,
          context = context,
          sendNotificationEmail = request.sendNotificationEmail,
          newContent = request.newContent,
          theme = request.theme,
          labels = request.labels,
          tags = request.tags,
          similarProposals = request.similarProposals
        )
      )
    }
  }

}
