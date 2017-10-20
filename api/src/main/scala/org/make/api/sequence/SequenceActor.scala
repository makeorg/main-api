package org.make.api.sequence

import akka.actor.{ActorLogging, PoisonPill}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.sequence.SequenceActor.Snapshot
import org.make.api.sequence.SequenceEvent._
import org.make.core.sequence._
import org.make.core.{DateHelper, SlugHelper}

class SequenceActor(dateHelper: DateHelper) extends PersistentActor with ActorLogging {
  def sequenceId: SequenceId = SequenceId(self.path.name)

  private[this] var state: Option[Sequence] = None

  override def receiveRecover: Receive = {
    case e: SequenceEvent                     => state = applyEvent(e)
    case SnapshotOffer(_, snapshot: Sequence) => state = Some(snapshot)
    case _: RecoveryCompleted                 =>
  }

  override def receiveCommand: Receive = {
    case GetSequence(_, _)                       => sender() ! state
    case command: ViewSequenceCommand            => onViewSequenceCommand(command)
    case command: CreateSequenceCommand          => onCreateCommand(command)
    case command: UpdateSequenceCommand          => onUpdateSequenceCommand(command)
    case command: RemoveProposalsSequenceCommand => onProposalsRemovedSequence(command)
    case command: AddProposalsSequenceCommand    => onProposalsAddedSequence(command)
    case Snapshot                                => state.foreach(saveSnapshot)
    case _: KillSequenceShard                    => self ! PoisonPill
  }

  private def onViewSequenceCommand(command: ViewSequenceCommand): Unit = {
    persistAndPublishEvent(
      SequenceViewed(id = sequenceId, eventDate = dateHelper.now(), requestContext = command.requestContext)
    ) {
      sender() ! state
    }
  }

  private def onProposalsAddedSequence(command: AddProposalsSequenceCommand): Unit = {
    val userId = command.moderatorId
    persistAndPublishEvent(
      SequenceProposalsAdded(
        id = command.sequenceId,
        proposalIds = command.proposalIds,
        requestContext = command.requestContext,
        eventDate = dateHelper.now(),
        userId = userId
      )
    ) {
      sender() ! state
      self ! Snapshot
    }
  }
  private def onProposalsRemovedSequence(command: RemoveProposalsSequenceCommand): Unit = {
    val userId = command.moderatorId
    persistAndPublishEvent(
      SequenceProposalsRemoved(
        id = command.sequenceId,
        proposalIds = command.proposalIds,
        requestContext = command.requestContext,
        eventDate = dateHelper.now(),
        userId = userId
      )
    ) {
      sender() ! state
      self ! Snapshot
    }
  }

  private def onCreateCommand(command: CreateSequenceCommand): Unit = {
    val userId = command.moderatorId
    persistAndPublishEvent(
      SequenceCreated(
        id = sequenceId,
        slug = SlugHelper(command.title),
        requestContext = command.requestContext,
        userId = userId,
        eventDate = dateHelper.now(),
        title = command.title,
        themeIds = command.themeIds,
        tagIds = command.tagIds,
        searchable = command.searchable
      )
    ) {
      sender() ! sequenceId
      self ! Snapshot
    }

  }

  private def onUpdateSequenceCommand(command: UpdateSequenceCommand): Unit = {
    val userId = command.moderatorId
    persistAndPublishEvent(
      SequenceUpdated(
        id = sequenceId,
        eventDate = dateHelper.now(),
        requestContext = command.requestContext,
        title = command.title,
        status = command.status,
        userId = userId,
        themeIds = command.themeIds,
        tagIds = command.tagIds
      )
    ) {
      sender() ! state
      self ! Snapshot
    }
  }

  override def persistenceId: String = sequenceId.value

  private val applyEvent: PartialFunction[SequenceEvent, Option[Sequence]] = {
    case e: SequenceCreated =>
      Some(
        Sequence(
          sequenceId = e.id,
          slug = e.slug,
          createdAt = Some(e.eventDate),
          updatedAt = Some(e.eventDate),
          title = e.title,
          status = SequenceStatus.Published,
          themeIds = e.themeIds,
          creationContext = e.requestContext,
          tagIds = e.tagIds,
          events = List(
            SequenceAction(
              date = e.eventDate,
              user = e.userId,
              actionType = "create",
              arguments = Map(
                "title" -> e.title,
                "tagIds" -> e.tagIds.mkString(","),
                "themeIds" -> e.themeIds.map(_.value).mkString(",")
              )
            )
          ),
          searchable = e.searchable
        )
      )
    case e: SequenceUpdated =>
      state.map(
        state =>
          state.copy(
            title = e.title.getOrElse(state.title),
            updatedAt = Some(e.eventDate),
            slug = SlugHelper(e.title.getOrElse(state.title)),
            status = e.status.getOrElse(state.status),
            themeIds = e.themeIds,
            tagIds = e.tagIds
        )
      )
    case e: SequenceProposalsAdded =>
      state.map(
        state => state.copy(updatedAt = Some(e.eventDate), proposalIds = (state.proposalIds ++ e.proposalIds).distinct)
      )
    case e: SequenceProposalsRemoved =>
      state.map(
        state =>
          state.copy(updatedAt = Some(e.eventDate), proposalIds = state.proposalIds.filterNot(e.proposalIds.toSet))
      )
    case _ => state
  }

  private def persistAndPublishEvent(event: SequenceEvent)(andThen: => Unit): Unit = {
    persist(event) { e: SequenceEvent =>
      state = applyEvent(e)
      context.system.eventStream.publish(e)
      andThen
    }
  }

}

object SequenceActor {

  case object Snapshot
}
