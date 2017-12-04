package org.make.api.sequence

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import org.make.core.RequestContext
import org.make.core.reference.TagId
import org.make.core.sequence._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait SequenceCoordinatorComponent {
  def sequenceCoordinator: ActorRef
}

trait SequenceCoordinatorService {

  /**
    * Retrieve a Sequence without logging it
    *
    * @param sequenceId the sequence to retrieve
    *
    * @return the given sequence if exists
    */
  def getSequence(sequenceId: SequenceId): Future[Option[Sequence]]

  /**
    * Retrieves a sequence by id and log the fact that it was seen
    *
    * @param sequenceId the sequence viewed by the user
    * @param requestContext the context of the request
    * @return the sequence as viewed by the user
    */
  def viewSequence(sequenceId: SequenceId, requestContext: RequestContext): Future[Option[Sequence]]
  def create(command: CreateSequenceCommand): Future[SequenceId]
  def update(command: UpdateSequenceCommand): Future[Option[Sequence]]
  def removeProposals(command: RemoveProposalsSequenceCommand): Future[Option[Sequence]]
  def addProposals(command: AddProposalsSequenceCommand): Future[Option[Sequence]]
  def updateSequenceTag(sequenceId: SequenceId, oldTag: TagId, newTag: TagId): Unit
  // toDo:
  // def publish(command: PublishSequenceCommand): Future[Option[Sequence]]
  // def unpublish(command: UnpublishSequenceCommand): Future[Option[Sequence]]
}

trait SequenceCoordinatorServiceComponent {
  def sequenceCoordinatorService: SequenceCoordinatorService
}

trait DefaultSequenceCoordinatorServiceComponent extends SequenceCoordinatorServiceComponent with StrictLogging {
  self: SequenceCoordinatorComponent =>

  override def sequenceCoordinatorService: SequenceCoordinatorService = new SequenceCoordinatorService {

    implicit val timeout: Timeout = Timeout(3.seconds)

    override def getSequence(sequenceId: SequenceId): Future[Option[Sequence]] = {
      (sequenceCoordinator ? GetSequence(sequenceId, RequestContext.empty)).mapTo[Option[Sequence]]
    }

    override def viewSequence(sequenceId: SequenceId, requestContext: RequestContext): Future[Option[Sequence]] = {
      (sequenceCoordinator ? ViewSequenceCommand(sequenceId, requestContext)).mapTo[Option[Sequence]]
    }

    override def create(command: CreateSequenceCommand): Future[SequenceId] = {
      (sequenceCoordinator ? command).mapTo[SequenceId]
    }

    override def update(command: UpdateSequenceCommand): Future[Option[Sequence]] = {
      (sequenceCoordinator ? command).mapTo[Option[Sequence]]
    }

    override def removeProposals(command: RemoveProposalsSequenceCommand): Future[Option[Sequence]] = {
      (sequenceCoordinator ? command).mapTo[Option[Sequence]]
    }

    override def addProposals(command: AddProposalsSequenceCommand): Future[Option[Sequence]] = {
      (sequenceCoordinator ? command).mapTo[Option[Sequence]]
    }
    override def updateSequenceTag(sequenceId: SequenceId, oldTag: TagId, newTag: TagId): Unit = {
      getSequence(sequenceId).onComplete {
        case Success(Some(sequence)) =>
          val newTagIds = sequence.tagIds.map {
            case `oldTag` => newTag
            case other    => other
          }
          val modifiedSequence = sequence.copy(tagIds = newTagIds)
          sequenceCoordinator ! PatchSequenceCommand(sequenceId = sequenceId, sequence = modifiedSequence)
        case Failure(e) => logger.error("", e)
        case _          =>
      }

    }

  }
}
