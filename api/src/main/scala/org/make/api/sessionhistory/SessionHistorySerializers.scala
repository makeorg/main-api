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
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import stamina.json.{from, JsonPersister}
import stamina.{json, V1, V2, V3}

object SessionHistorySerializers extends SprayJsonFormatters {

  private val logSessionSearchEventSerializer: JsonPersister[LogSessionSearchProposalsEvent, V2] =
    json.persister[LogSessionSearchProposalsEvent, V2](
      "session-history-search-proposal",
      from[V1].to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val logSessionVoteEventSerializer: JsonPersister[LogSessionVoteEvent, V3] =
    json.persister[LogSessionVoteEvent, V3](
      "session-history-vote-proposal",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val logSessionUnvoteEventSerializer: JsonPersister[LogSessionUnvoteEvent, V3] =
    json.persister[LogSessionUnvoteEvent, V3](
      "session-history-unvote-proposal",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val logSessionQualificationEventSerializer: JsonPersister[LogSessionQualificationEvent, V3] =
    json.persister[LogSessionQualificationEvent, V3](
      "session-history-qualificaion-vote",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val logSessionUnqualificationEventSerializer: JsonPersister[LogSessionUnqualificationEvent, V3] =
    json.persister[LogSessionUnqualificationEvent, V3](
      "session-history-unqualificaion-vote",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val logSessionTransformedEventSerializer: JsonPersister[SessionTransformed, V2] =
    json.persister[SessionTransformed, V2](
      "session-transformed",
      from[V1].to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val logSessionStartSequenceEventSerializer: JsonPersister[LogSessionStartSequenceEvent, V2] =
    json.persister[LogSessionStartSequenceEvent, V2](
      "session-history-start-sequence",
      from[V1].to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val SessionHistorySerializer: JsonPersister[SessionHistory, V3] =
    json.persister[SessionHistory, V3](
      "session-history",
      from[V1]
        .to[V2] { json =>
          val eventsToMigrate = Set(
            "LogSessionVoteEvent",
            "LogSessionUnvoteEvent",
            "LogSessionQualificationEvent",
            "LogSessionUnqualificationEvent"
          )
          json.update(
            "events" /
              filter("type".is[String](value => eventsToMigrate.contains(value))) /
              "action" /
              "arguments" /
              "trust" !
              set[String]("trusted")
          )
        }
        .to[V3](_.update("events" / * / "context" / "customData" ! set[Map[String, String]](Map.empty)))
    )

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
