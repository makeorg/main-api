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

package org.make.api.sessionhistory

import org.make.api.sessionhistory.SessionHistoryActor.SessionHistory
import org.make.core.SprayJsonFormatters
import stamina.json.JsonPersister
import stamina.{json, V1}

object SessionHistorySerializers extends SprayJsonFormatters {

  private val logSessionSearchEventSerializer: JsonPersister[LogSessionSearchProposalsEvent, V1] =
    json.persister[LogSessionSearchProposalsEvent]("session-history-search-proposal")

  private val logSessionVoteEventSerializer: JsonPersister[LogSessionVoteEvent, V1] =
    json.persister[LogSessionVoteEvent]("session-history-vote-proposal")

  private val logSessionUnvoteEventSerializer: JsonPersister[LogSessionUnvoteEvent, V1] =
    json.persister[LogSessionUnvoteEvent]("session-history-unvote-proposal")

  private val logSessionQualificationEventSerializer: JsonPersister[LogSessionQualificationEvent, V1] =
    json.persister[LogSessionQualificationEvent]("session-history-qualificaion-vote")

  private val logSessionUnqualificationEventSerializer: JsonPersister[LogSessionUnqualificationEvent, V1] =
    json.persister[LogSessionUnqualificationEvent]("session-history-unqualificaion-vote")

  private val logSessionTransformedEventSerializer: JsonPersister[SessionTransformed, V1] =
    json.persister[SessionTransformed]("session-transformed")

  private val logSessionStartSequenceEventSerializer: JsonPersister[LogSessionStartSequenceEvent, V1] =
    json.persister[LogSessionStartSequenceEvent]("session-history-start-sequence")

  private val SessionHistorySerializer: JsonPersister[SessionHistory, V1] =
    json.persister[SessionHistory]("session-history")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      logSessionSearchEventSerializer,
      logSessionVoteEventSerializer,
      logSessionUnvoteEventSerializer,
      logSessionQualificationEventSerializer,
      logSessionUnqualificationEventSerializer,
      logSessionTransformedEventSerializer,
      logSessionStartSequenceEventSerializer,
      SessionHistorySerializer
    )
}
