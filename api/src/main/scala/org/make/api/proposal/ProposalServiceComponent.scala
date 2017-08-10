package org.make.api.proposal

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.technical.IdGeneratorComponent
import org.make.core.user.UserId
import org.make.core.proposal._

import scala.concurrent.Future
import scala.concurrent.duration._

trait ProposalServiceComponent {
  def proposalService: ProposalService
}

trait ProposalService {
  def getProposal(proposalId: ProposalId): Future[Option[Proposal]]
  def propose(userId: UserId, createdAt: ZonedDateTime, content: String): Future[Option[Proposal]]
  def update(proposalId: ProposalId, updatedAt: ZonedDateTime, content: String): Future[Option[Proposal]]
}

trait DefaultProposalServiceComponent extends ProposalServiceComponent {
  this: IdGeneratorComponent with ProposalServiceComponent with ProposalCoordinatorComponent =>

  override lazy val proposalService = new ProposalService {

    implicit private val defaultTimeout = new Timeout(5.seconds)

    override def getProposal(proposalId: ProposalId): Future[Option[Proposal]] = {
      (proposalCoordinator ? ViewProposalCommand(proposalId))
        .mapTo[Option[Proposal]]
    }

    override def propose(userId: UserId, createdAt: ZonedDateTime, content: String): Future[Option[Proposal]] = {
      (
        proposalCoordinator ?
          ProposeCommand(
            proposalId = idGenerator.nextProposalId(),
            userId = userId,
            createdAt = createdAt,
            content = content
          )
      ).mapTo[Option[Proposal]]
    }

    override def update(proposalId: ProposalId, updatedAt: ZonedDateTime, content: String): Future[Option[Proposal]] = {
      (
        proposalCoordinator ?
          UpdateProposalCommand(proposalId = proposalId, updatedAt = updatedAt, content = content)
      ).mapTo[Option[Proposal]]
    }

  }

}

trait ProposalCoordinatorComponent {
  def proposalCoordinator: ActorRef
}
