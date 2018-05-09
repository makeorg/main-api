package org.make.api.sequence

import akka.actor.PoisonPill
import org.make.api.sequence.PublishedSequenceEvent._
import org.make.api.technical.MakePersistentActor
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.core.operation.OperationId
import org.make.core.sequence._
import org.make.core.{DateHelper, SlugHelper}

class SequenceActor(dateHelper: DateHelper) extends MakePersistentActor(classOf[Sequence], classOf[SequenceEvent]) {
  def sequenceId: SequenceId = SequenceId(self.path.name)

  override def receiveCommand: Receive = {
    case GetSequence(_, _)                       => sender() ! state
    case command: ViewSequenceCommand            => onViewSequenceCommand(command)
    case command: CreateSequenceCommand          => onCreateCommand(command)
    case command: UpdateSequenceCommand          => onUpdateSequenceCommand(command)
    case command: RemoveProposalsSequenceCommand => onProposalsRemovedSequence(command)
    case command: AddProposalsSequenceCommand    => onProposalsAddedSequence(command)
    case command: PatchSequenceCommand           => onPatchSequenceCommand(command)
    case Snapshot                                => saveSnapshot()
    case _: KillSequenceShard                    => self ! PoisonPill
  }

  private def onViewSequenceCommand(command: ViewSequenceCommand): Unit = {
    persistAndPublishEvent(
      SequenceViewed(id = sequenceId, eventDate = dateHelper.now(), requestContext = command.requestContext)
    ) { _ =>
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
    ) { _ =>
      sender() ! state
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
    ) { _ =>
      sender() ! state
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
        operationId = command.operationId,
        searchable = command.searchable
      )
    ) { _ =>
      sender() ! sequenceId
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
        operationId = command.operationId,
        userId = userId,
        themeIds = command.themeIds
      )
    ) { _ =>
      sender() ! state
    }
  }

  private def onPatchSequenceCommand(command: PatchSequenceCommand): Unit = {
    persistAndPublishEvent(
      SequencePatched(id = command.sequenceId, requestContext = command.requestContext, sequence = command.sequence)
    ) { _ =>
      sender() ! state
    }
  }

  override def persistenceId: String = sequenceId.value

  override val applyEvent: PartialFunction[SequenceEvent, Option[Sequence]] = {
    case e: SequenceCreated =>
      Some(
        Sequence(
          sequenceId = e.id,
          slug = e.slug,
          createdAt = Some(e.eventDate),
          updatedAt = Some(e.eventDate),
          title = e.title,
          status = SequenceStatus.Unpublished,
          themeIds = e.themeIds,
          operationId = e.operationId,
          creationContext = e.requestContext,
          events = List(
            SequenceAction(
              date = e.eventDate,
              user = e.userId,
              actionType = "create",
              arguments = Map(
                "title" -> e.title,
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
            creationContext = state.creationContext.copy(operationId = e.operation.map(OperationId(_))),
            title = e.title.getOrElse(state.title),
            updatedAt = Some(e.eventDate),
            slug = SlugHelper(e.title.getOrElse(state.title)),
            status = e.status.getOrElse(state.status),
            themeIds = e.themeIds,
            operationId = e.operationId
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
    case e: SequencePatched =>
      state.map(_ => e.sequence)
    case _ => state
  }

}
