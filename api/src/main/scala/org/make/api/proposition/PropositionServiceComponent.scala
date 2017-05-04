package org.make.api.proposition

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.util.Timeout
import org.make.api.IdGeneratorComponent
import org.make.core.citizen.CitizenId
import org.make.core.proposition._
import akka.pattern.ask

import scala.concurrent.Future

trait PropositionServiceComponent {
  this: IdGeneratorComponent with PropositionServiceComponent =>

  def propositionService: PropositionService

  class PropositionService(actor: ActorRef) {

    implicit private val defaultTimeout = new Timeout(2, TimeUnit.SECONDS)

    def getProposition(propositionId: PropositionId): Future[Option[Proposition]] = {
      (actor ? ViewPropositionCommand(propositionId)).mapTo[Option[Proposition]]
    }

    def propose(
                 citizenId: CitizenId,
                 createdAt: ZonedDateTime,
                 content: String
               ): Future[Option[Proposition]] = {
      (
        actor ?
          ProposeCommand(
            propositionId = idGenerator.nextPropositionId(),
            citizenId = citizenId,
            createdAt = createdAt,
            content = content
          )
        ).mapTo[Option[Proposition]]
    }

    def update(
                propositionId: PropositionId,
                updatedAt: ZonedDateTime,
                content: String
               ): Future[Option[Proposition]] = {
      (
        actor ?
          UpdatePropositionCommand(
            propositionId = idGenerator.nextPropositionId(),
            updatedAt = updatedAt,
            content = content
          )
        ).mapTo[Option[Proposition]]
    }

  }

}
