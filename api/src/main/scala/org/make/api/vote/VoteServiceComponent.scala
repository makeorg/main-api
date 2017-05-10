package org.make.api.vote

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.IdGeneratorComponent
import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId
import org.make.core.vote._
import org.make.core.vote.VoteId

import scala.concurrent.Future
import scala.concurrent.duration._

trait VoteServiceComponent {
  this: IdGeneratorComponent with VoteServiceComponent =>

  def voteService: VoteService

  class VoteService(actor: ActorRef) {

    implicit private val defaultTimeout = new Timeout(5.seconds)

    def getVote(voteId: VoteId, propositionId: PropositionId): Future[Option[Vote]] = {
      (actor ? ViewVoteCommand(voteId, propositionId)).mapTo[Option[Vote]]
    }

    def agree(
               propositionId: PropositionId,
               citizenId: CitizenId,
               createdAt: ZonedDateTime
             ): Future[Option[Vote]] = {
      (
        actor ?
          AgreeCommand(
            voteId = idGenerator.nextVoteId(),
            propositionId = propositionId,
            citizenId = citizenId,
            createdAt = createdAt
          )
        ).mapTo[Option[Vote]]
    }

    def disagree(
                  propositionId: PropositionId,
                  citizenId: CitizenId,
                  createdAt: ZonedDateTime
                ): Future[Option[Vote]] = {
      (
        actor ?
          DisagreeCommand(
            voteId = idGenerator.nextVoteId(),
            propositionId = propositionId,
            citizenId = citizenId,
            createdAt = createdAt
          )
        ).mapTo[Option[Vote]]
    }

    def unsure(
                propositionId: PropositionId,
                citizenId: CitizenId,
                createdAt: ZonedDateTime
              ): Future[Option[Vote]] = {
      (
        actor ?
          UnsureCommand(
            voteId = idGenerator.nextVoteId(),
            propositionId = propositionId,
            citizenId = citizenId,
            createdAt = createdAt
          )
        ).mapTo[Option[Vote]]
    }
  }
}
