package org.make.api.proposal

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.core.proposal._
import org.make.core.user.UserId
import org.make.core.{RequestContext, ValidationFailedError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait ProposalCoordinatorComponent {
  def proposalCoordinator: ActorRef
}

trait ProposalCoordinatorService {

  def clearSimilarProposals(id: ProposalId): Unit

  def removeProposalFromCluster(proposalId: ProposalId, proposalToRemove: ProposalId): Unit

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
  def postpone(command: PostponeProposalCommand): Future[Option[Proposal]]
  def vote(command: VoteProposalCommand): Future[Option[Vote]]
  def unvote(command: UnvoteProposalCommand): Future[Option[Vote]]
  def qualification(command: QualifyVoteCommand): Future[Option[Qualification]]
  def unqualification(command: UnqualifyVoteCommand): Future[Option[Qualification]]
  def lock(command: LockProposalCommand): Future[Option[UserId]]
}

trait ProposalCoordinatorServiceComponent {
  def proposalCoordinatorService: ProposalCoordinatorService
}

trait DefaultProposalCoordinatorServiceComponent extends ProposalCoordinatorServiceComponent {
  self: ProposalCoordinatorComponent =>

  override lazy val proposalCoordinatorService: ProposalCoordinatorService = new ProposalCoordinatorService {

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
      (proposalCoordinator ? command).flatMap[Option[Proposal]] {
        case error: ValidationFailedError => Future.failed(error)
        case None                         => Future.successful(None)
        case Some(proposal) =>
          Future.successful(Some(proposal.asInstanceOf[Proposal]))
        case _ => Future.successful(None)
      }
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
      (proposalCoordinator ? command).flatMap[Option[Proposal]] {
        case error: ValidationFailedError => Future.failed(error)
        case None                         => Future.successful(None)
        case Some(proposal) =>
          Future.successful(Some(proposal.asInstanceOf[Proposal]))
        case _ => Future.successful(None)
      }
    }

    override def postpone(command: PostponeProposalCommand): Future[Option[Proposal]] = {
      (proposalCoordinator ? command).flatMap[Option[Proposal]] {
        case error: ValidationFailedError => Future.failed(error)
        case None                         => Future.successful(None)
        case Some(proposal) =>
          Future.successful(Some(proposal.asInstanceOf[Proposal]))
        case _ => Future.successful(None)
      }
    }

    override def vote(command: VoteProposalCommand): Future[Option[Vote]] = {
      (proposalCoordinator ? command).mapTo[Either[Exception, Option[Vote]]].flatMap {
        case Right(success) => Future.successful(success)
        case Left(e)        => Future.failed(e)
      }
    }

    override def unvote(command: UnvoteProposalCommand): Future[Option[Vote]] = {
      (proposalCoordinator ? command).mapTo[Either[Exception, Option[Vote]]].flatMap {
        case Right(success) => Future.successful(success)
        case Left(e)        => Future.failed(e)
      }
    }

    override def qualification(command: QualifyVoteCommand): Future[Option[Qualification]] = {
      (proposalCoordinator ? command).mapTo[Either[Exception, Option[Qualification]]].flatMap {
        case Right(success) => Future.successful(success)
        case Left(e)        => Future.failed(e)
      }
    }

    override def unqualification(command: UnqualifyVoteCommand): Future[Option[Qualification]] = {
      (proposalCoordinator ? command).mapTo[Either[Exception, Option[Qualification]]].flatMap {
        case Right(success) => Future.successful(success)
        case Left(e)        => Future.failed(e)
      }
    }

    override def lock(command: LockProposalCommand): Future[Option[UserId]] = {
      (proposalCoordinator ? command).mapTo[Either[Exception, Option[UserId]]].flatMap {
        case Right(success) => Future.successful(success)
        case Left(e)        => Future.failed(e)
      }
    }

    override def removeProposalFromCluster(proposalId: ProposalId, proposalToRemove: ProposalId): Unit = {
      proposalCoordinator ! RemoveSimilarProposalCommand(
        proposalId = proposalId,
        similarToRemove = proposalToRemove,
        requestContext = RequestContext.empty
      )
    }

    override def clearSimilarProposals(proposalId: ProposalId): Unit = {
      proposalCoordinator ! ClearSimilarProposalsCommand(proposalId = proposalId, requestContext = RequestContext.empty)
    }
  }
}
