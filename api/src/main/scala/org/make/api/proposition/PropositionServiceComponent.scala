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

trait PropositionServiceComponent {
  def propositionService: PropositionService
}

trait PropositionService {
  def getProposition(propositionId: PropositionId): Future[Option[Proposition]]
  def propose(userId: UserId, createdAt: ZonedDateTime, content: String): Future[Option[Proposition]]
  def update(propositionId: PropositionId, updatedAt: ZonedDateTime, content: String): Future[Option[Proposition]]
}

trait DefaultPropositionServiceComponent extends PropositionServiceComponent {
  this: IdGeneratorComponent with PropositionServiceComponent with PropositionCoordinatorComponent =>

  override lazy val propositionService = new PropositionService {

    implicit private val defaultTimeout = new Timeout(5.seconds)

    override def getProposition(propositionId: PropositionId): Future[Option[Proposition]] = {
      (propositionCoordinator ? ViewPropositionCommand(propositionId))
        .mapTo[Option[Proposition]]
    }

    override def propose(userId: UserId, createdAt: ZonedDateTime, content: String): Future[Option[Proposition]] = {
      (
        propositionCoordinator ?
          ProposeCommand(
            propositionId = idGenerator.nextPropositionId(),
            userId = userId,
            createdAt = createdAt,
            content = content
          )
      ).mapTo[Option[Proposition]]
    }

    override def update(propositionId: PropositionId,
                        updatedAt: ZonedDateTime,
                        content: String): Future[Option[Proposition]] = {
      (
        propositionCoordinator ?
          UpdatePropositionCommand(propositionId = propositionId, updatedAt = updatedAt, content = content)
      ).mapTo[Option[Proposition]]
    }

  }

}

trait PropositionCoordinatorComponent {
  def propositionCoordinator: ActorRef
}
