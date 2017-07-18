package org.make.api.vote

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.technical.IdGeneratorComponent
import org.make.core.user.UserId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteStatus.VoteStatus
import org.make.core.vote.{VoteId, _}

import scala.concurrent.Future
import scala.concurrent.duration._

trait VoteServiceComponent {
  val voteService: VoteService
}

trait VoteService {
  def getVote(voteId: VoteId, propositionId: PropositionId): Future[Option[Vote]]
  def vote(propositionId: PropositionId,
           userId: UserId,
           createdAt: ZonedDateTime,
           status: VoteStatus): Future[Option[Vote]]
}

trait DefaultVoteServiceComponent extends VoteServiceComponent {
  this: IdGeneratorComponent with VoteServiceComponent with VoteCoordinatorComponent =>

  override lazy val voteService = new VoteService {

    implicit private val defaultTimeout = new Timeout(5.seconds)

    override def getVote(voteId: VoteId, propositionId: PropositionId): Future[Option[Vote]] = {
      (voteCoordinator ? ViewVoteCommand(voteId, propositionId)).mapTo[Option[Vote]]
    }

    override def vote(propositionId: PropositionId,
                      userId: UserId,
                      createdAt: ZonedDateTime,
                      status: VoteStatus): Future[Option[Vote]] = {
      (
        voteCoordinator ?
          PutVoteCommand(
            voteId = idGenerator.nextVoteId(),
            propositionId = propositionId,
            userId = userId,
            createdAt = createdAt,
            status = status
          )
      ).mapTo[Option[Vote]]
    }
  }
}

trait VoteCoordinatorComponent {
  def voteCoordinator: ActorRef
}
