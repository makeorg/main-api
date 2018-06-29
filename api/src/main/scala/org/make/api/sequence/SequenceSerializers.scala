/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.sequence

import org.make.api.sequence.PublishedSequenceEvent._
import org.make.core.SprayJsonFormatters
import org.make.core.sequence.Sequence
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

  private val sequencePatchedSerializer: JsonPersister[SequencePatched, V1] =
    persister[SequencePatched]("sequence-tags-updated")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      sequenceCreatedSerializer,
      sequenceProposalsAddedSerializer,
      sequenceProposalsRemovedSerializer,
      sequenceViewedSerializer,
      sequenceUpdatedSerializer,
      sequenceSerializer,
      sequencePatchedSerializer
    )
}
