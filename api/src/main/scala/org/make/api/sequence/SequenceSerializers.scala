package org.make.api.sequence

import org.make.core.SprayJsonFormatters
import org.make.core.sequence.Sequence
import org.make.core.sequence.SequenceEvent._
import stamina.V1
import stamina.json._

object SequenceSerializers extends SprayJsonFormatters {
  private val sequenceCreatedSerializer: JsonPersister[SequenceCreated, V1] =
    persister[SequenceCreated]("sequence-created")

  private val sequenceProposalsAddedSerializer: JsonPersister[SequenceProposalsAdded, V1] =
    persister[SequenceProposalsAdded]("sequence-proposals-added")

  private val sequenceProposalsRemovedSerializer: JsonPersister[SequenceProposalsRemoved, V1] =
    persister[SequenceProposalsRemoved]("sequence-proposals-removed")

  private val sequenceViewedSerializer: JsonPersister[SequenceViewed, V1] =
    persister[SequenceViewed]("sequence-viewed")

  private val sequenceUpdatedSerializer: JsonPersister[SequenceUpdated, V1] =
    persister[SequenceUpdated]("sequence-updated")

  private val sequenceSerializer: JsonPersister[Sequence, V1] =
    persister[Sequence]("sequence")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      sequenceCreatedSerializer,
      sequenceProposalsAddedSerializer,
      sequenceProposalsRemovedSerializer,
      sequenceViewedSerializer,
      sequenceUpdatedSerializer,
      sequenceSerializer
    )
}
