package org.make.api.proposal

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.core.proposal._
import org.make.core.proposal.indexed.Vote
import org.make.core.{RequestContext, ValidationFailedError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait ProposalCoordinatorComponent {
  def proposalCoordinator: ActorRef
}

trait ProposalCoordinatorService {

  /**
    * Retrieve a Proposal without logging it
    *
    * @param proposalId the proposal to retrieve
    * @return the given proposal if exists
    */
  def getProposal(proposalId: ProposalId): Future[Option[Proposal]]

  /**
    * Retrieves a proposal by id and log the fact that it was seen
    *
    * @param proposalId the proposal viewed by the user
    * @param requestContext the context of the request
    * @return the proposal as viewed by the user
    */
  def viewProposal(proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]]
  def propose(command: ProposeCommand): Future[ProposalId]
  def update(command: UpdateProposalCommand): Future[Option[Proposal]]
  def accept(command: AcceptProposalCommand): Future[Option[Proposal]]
  def refuse(command: RefuseProposalCommand): Future[Option[Proposal]]
  def vote(command: VoteProposalCommand): Future[Option[Vote]]
  def unvote(command: UnvoteProposalCommand): Future[Option[Vote]]
}

trait ProposalCoordinatorServiceComponent {
  def proposalCoordinatorService: ProposalCoordinatorService
}

trait DefaultProposalCoordinatorServiceComponent extends ProposalCoordinatorServiceComponent {
  self: ProposalCoordinatorComponent =>

  override def proposalCoordinatorService: ProposalCoordinatorService = new ProposalCoordinatorService {

    implicit val timeout: Timeout = Timeout(3.seconds)

    override def getProposal(proposalId: ProposalId): Future[Option[Proposal]] = {
      (proposalCoordinator ? GetProposal(proposalId, RequestContext.empty)).mapTo[Option[Proposal]]
    }

    override def viewProposal(proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]] = {
      (proposalCoordinator ? ViewProposalCommand(proposalId, requestContext)).mapTo[Option[Proposal]]
    }

    override def propose(command: ProposeCommand): Future[ProposalId] = {
      (proposalCoordinator ? command).mapTo[ProposalId]
    }

    override def update(command: UpdateProposalCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).mapTo[Option[Proposal]]
    }

    override def accept(command: AcceptProposalCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).flatMap[Option[Proposal]] {
        case error: ValidationFailedError => Future.failed(error)
        case None                         => Future.successful(None)
        case Some(proposal) =>
          Future.successful(Some(proposal.asInstanceOf[Proposal]))
        case _ => Future.successful(None)

      }
    }

    override def refuse(command: RefuseProposalCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).mapTo[Option[Proposal]]
    }

    override def vote(command: VoteProposalCommand): Future[Option[Vote]] = {
      (proposalCoordinator ? command).mapTo[Option[Vote]]
    }

    override def unvote(command: UnvoteProposalCommand): Future[Option[Vote]] = {
      (proposalCoordinator ? command).mapTo[Option[Vote]]
    }
  }
}
