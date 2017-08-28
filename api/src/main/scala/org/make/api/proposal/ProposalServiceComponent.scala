package org.make.api.proposal

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.technical.IdGeneratorComponent
import org.make.core.RequestContext
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.user.{SearchProposalsHistoryCommand, User, UserId}

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
    with ProposalCoordinatorComponent
    with ProposalSearchEngineComponent =>

  override lazy val proposalService = new ProposalService {

    implicit private val defaultTimeout: Timeout = new Timeout(5.seconds)

    override def getProposalById(proposalId: ProposalId, context: RequestContext): Future[Option[IndexedProposal]] = {
      proposalCoordinator ! ViewProposalCommand(proposalId, context)
      elasticsearchAPI.findProposalById(proposalId)
    }

    override def getEventSourcingProposal(proposalId: ProposalId, context: RequestContext): Future[Option[Proposal]] = {
      (proposalCoordinator ? ViewProposalCommand(proposalId, context))
        .mapTo[Option[Proposal]]
    }

    override def search(userId: Option[UserId],
                        query: SearchQuery,
                        context: RequestContext): Future[Seq[IndexedProposal]] = {
      proposalCoordinator ! SearchProposalsHistoryCommand(userId, query, context)
      elasticsearchAPI.searchProposals(query)
    }

    override def propose(user: User,
                         context: RequestContext,
                         createdAt: ZonedDateTime,
                         content: String): Future[ProposalId] = {
      (
        proposalCoordinator ?
          ProposeCommand(
            proposalId = idGenerator.nextProposalId(),
            context = context,
            user = user,
            createdAt = createdAt,
            content = content
          )
      ).mapTo[ProposalId]
    }

    override def update(proposalId: ProposalId,
                        context: RequestContext,
                        updatedAt: ZonedDateTime,
                        content: String): Future[Option[Proposal]] = {
      (
        proposalCoordinator ?
          UpdateProposalCommand(proposalId = proposalId, context = context, updatedAt = updatedAt, content = content)
      ).mapTo[Option[Proposal]]
    }

    override def validateProposal(proposalId: ProposalId,
                                  moderator: UserId,
                                  context: RequestContext,
                                  request: ValidateProposalRequest): Future[Proposal] = {

      (proposalCoordinator ? AcceptProposalCommand(
        proposalId = proposalId,
        moderator = moderator,
        context = context,
        sendNotificationEmail = request.sendNotificationEmail,
        newContent = request.newContent,
        theme = request.theme,
        labels = request.labels,
        tags = request.tags,
        similarProposals = request.similarProposals
      )).mapTo[Option[Proposal]]

    }
  }

}

trait ProposalCoordinatorComponent {
  def proposalCoordinator: ActorRef
}
