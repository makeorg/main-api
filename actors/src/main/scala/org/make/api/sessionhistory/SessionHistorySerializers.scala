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
import org.make.api.technical.MakeEventSerializer
import org.make.api.technical.security.SecurityConfiguration
import org.make.core.SprayJsonFormatters
import spray.json.DefaultJsonProtocol._
import spray.json.JsObject
import spray.json.lenses.JsonLenses._
import stamina.json.{from, JsonPersister}
import stamina._

final class SessionHistorySerializers(securityConfiguration: SecurityConfiguration) extends SprayJsonFormatters {

  private val logSessionSearchEventSerializer: JsonPersister[LogSessionSearchProposalsEvent, V3] =
    json.persister[LogSessionSearchProposalsEvent, V3](
      "session-history-search-proposal",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logSessionVoteEventSerializer: JsonPersister[LogSessionVoteEvent, V4] =
    json.persister[LogSessionVoteEvent, V4](
      "session-history-vote-proposal",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logSessionUnvoteEventSerializer: JsonPersister[LogSessionUnvoteEvent, V4] =
    json.persister[LogSessionUnvoteEvent, V4](
      "session-history-unvote-proposal",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logSessionQualificationEventSerializer: JsonPersister[LogSessionQualificationEvent, V4] =
    json.persister[LogSessionQualificationEvent, V4](
      "session-history-qualificaion-vote",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logSessionUnqualificationEventSerializer: JsonPersister[LogSessionUnqualificationEvent, V4] =
    json.persister[LogSessionUnqualificationEvent, V4](
      "session-history-unqualificaion-vote",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logSessionTransformedEventSerializer: JsonPersister[SessionTransformed, V3] =
    json.persister[SessionTransformed, V3](
      "session-transformed",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logSessionExpiredSerializer: JsonPersister[SessionExpired, V2] =
    json.persister[SessionExpired, V2](
      "session-expired",
      from[V1].to[V2](
        _.update(
          "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
        )
      )
    )

  private val logSessionStartSequenceEventSerializer: JsonPersister[LogSessionStartSequenceEvent, V3] =
    json.persister[LogSessionStartSequenceEvent, V3](
      "session-history-start-sequence",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val SessionHistorySerializer: JsonPersister[SessionHistory, V4] =
    json.persister[SessionHistory, V4](
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
        .to[V4](
          _.update(
            "events" / * / "context" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val saveLastEventDateSerializer: JsonPersister[SaveLastEventDate, V2] =
    json.persister[SaveLastEventDate, V2](
      "save-last-event-date",
      from[V1].to[V2](
        _.update(
          "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
        )
      )
    )

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      logSessionSearchEventSerializer,
      logSessionVoteEventSerializer,
      logSessionUnvoteEventSerializer,
      logSessionQualificationEventSerializer,
      logSessionUnqualificationEventSerializer,
      logSessionTransformedEventSerializer,
      logSessionExpiredSerializer,
      logSessionStartSequenceEventSerializer,
      saveLastEventDateSerializer,
      SessionHistorySerializer
    )
}

object SessionHistorySerializers {
  def apply(securityConfiguration: SecurityConfiguration): SessionHistorySerializers =
    new SessionHistorySerializers(securityConfiguration)
}
