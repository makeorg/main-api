package org.make.api.proposition

import java.time.ZonedDateTime

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.technical.IdGeneratorComponent
import org.make.core.user.UserId
import org.make.core.proposition._

import scala.concurrent.Future
import scala.concurrent.duration._

trait PropositionServiceComponent { this: IdGeneratorComponent with PropositionServiceComponent =>

  def propositionService: PropositionService

  class PropositionService(actor: ActorRef) {

    implicit private val defaultTimeout = new Timeout(5.seconds)

    def getProposition(propositionId: PropositionId): Future[Option[Proposition]] = {
      (actor ? ViewPropositionCommand(propositionId))
        .mapTo[Option[Proposition]]
    }

    def propose(userId: UserId, createdAt: ZonedDateTime, content: String): Future[Option[Proposition]] = {
      (
        actor ?
          ProposeCommand(
            propositionId = idGenerator.nextPropositionId(),
            userId = userId,
            createdAt = createdAt,
            content = content
          )
      ).mapTo[Option[Proposition]]
    }

    def update(propositionId: PropositionId, updatedAt: ZonedDateTime, content: String): Future[Option[Proposition]] = {
      (
        actor ?
          UpdatePropositionCommand(propositionId = propositionId, updatedAt = updatedAt, content = content)
      ).mapTo[Option[Proposition]]
    }

  }

}
