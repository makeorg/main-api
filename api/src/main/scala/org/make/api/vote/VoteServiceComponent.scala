package org.make.api.vote

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.technical.IdGeneratorComponent
import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteStatus.VoteStatus
import org.make.core.vote.{VoteId, _}

import scala.concurrent.Future
import scala.concurrent.duration._

trait VoteServiceComponent { this: IdGeneratorComponent with VoteServiceComponent =>

  def voteService: VoteService

  class VoteService(actor: ActorRef) {

    implicit private val defaultTimeout = new Timeout(5.seconds)

    def getVote(voteId: VoteId, propositionId: PropositionId): Future[Option[Vote]] = {
      (actor ? ViewVoteCommand(voteId, propositionId)).mapTo[Option[Vote]]
    }

    def vote(propositionId: PropositionId,
             citizenId: CitizenId,
             createdAt: ZonedDateTime,
             status: VoteStatus): Future[Option[Vote]] = {
      (
        actor ?
          PutVoteCommand(
            voteId = idGenerator.nextVoteId(),
            propositionId = propositionId,
            citizenId = citizenId,
            createdAt = createdAt,
            status = status
          )
      ).mapTo[Option[Vote]]
    }
  }
}
