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

package org.make.api.userhistory

import org.make.api.technical.MakeEventSerializer
import org.make.api.technical.security.SecurityConfiguration
import org.make.api.user.Anonymization

import java.time.ZonedDateTime
import org.make.api.userhistory.UserHistoryActor.{UserHistory, UserVotesAndQualifications}
import org.make.core.SprayJsonFormatters
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import spray.json.lenses.SeqLens
import spray.json.{JsArray, JsObject, JsString, JsValue}
import stamina._
import stamina.json._

final class UserHistorySerializers(securityConfiguration: SecurityConfiguration) extends SprayJsonFormatters {

  val countryFixDate: ZonedDateTime = ZonedDateTime.parse("2018-09-01T00:00:00Z")
  private val logRegisterCitizenEventSerializer: JsonPersister[LogRegisterCitizenEvent, V6] =
    json.persister[LogRegisterCitizenEvent, V6](
      "user-history-registered",
      from[V1]
        .to[V2](
          _.update("action" / "arguments" / "country" ! set[String]("FR"))
            .update("action" / "arguments" / "language" ! set[String]("fr"))
        )
        .to[V3](identity)
        .to[V4] { json =>
          val actionDate: ZonedDateTime = ZonedDateTime.parse(json.extract[String]("action" / "date"))

          if (actionDate.isBefore(countryFixDate)) {

            json.extract[JsObject]("context").getFields("language") match {

              case Seq(JsString("fr")) =>
                json
                  .update("context" / "source" ! set[String]("core"))
                  .update("context" / "country" ! set[String]("FR"))
              case Seq(JsString("it")) =>
                json
                  .update("context" / "source" ! set[String]("core"))
                  .update("context" / "country" ! set[String]("IT"))
              case Seq(JsString("en")) =>
                json
                  .update("context" / "source" ! set[String]("core"))
                  .update("context" / "country" ! set[String]("GB"))
              case _ =>
                json
                  .update("context" / "source" ! set[String]("core"))
                  .update("context" / "country" ! set[String]("FR"))
                  .update("context" / "language" ! set[String]("fr"))
            }
          } else {
            json
          }
        }
        .to[V5] {
          _.update("context" / "customData" ! set[Map[String, String]](Map.empty))
        }
        .to[V6](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logSearchProposalsEventSerializer: JsonPersister[LogUserSearchProposalsEvent, V3] =
    json.persister[LogUserSearchProposalsEvent, V3](
      "user-history-searched",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logAcceptProposalEventSerializer: JsonPersister[LogAcceptProposalEvent, V3] =
    json.persister[LogAcceptProposalEvent, V3](
      "user-history-accepted-proposal",
      from[V1]
        .to[V2](
          _.update("context" / "customData" ! set[Map[String, String]](Map.empty))
            .update("action" / "arguments" / "requestContext" / "customData" ! set[Map[String, String]](Map.empty))
        )
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          ).update(
            "action" / "arguments" / "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val logRefuseProposalEventSerializer: JsonPersister[LogRefuseProposalEvent, V3] =
    json.persister[LogRefuseProposalEvent, V3](
      "user-history-refused-proposal",
      from[V1]
        .to[V2](
          _.update("context" / "customData" ! set[Map[String, String]](Map.empty))
            .update("action" / "arguments" / "requestContext" / "customData" ! set[Map[String, String]](Map.empty))
        )
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          ).update(
            "action" / "arguments" / "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val logPostponeProposalEventSerializer: JsonPersister[LogPostponeProposalEvent, V3] =
    json.persister[LogPostponeProposalEvent, V3](
      "user-history-postponed-proposal",
      from[V1]
        .to[V2](
          _.update("context" / "customData" ! set[Map[String, String]](Map.empty))
            .update("action" / "arguments" / "requestContext" / "customData" ! set[Map[String, String]](Map.empty))
        )
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          ).update(
            "action" / "arguments" / "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val logLockProposalEventSerializer: JsonPersister[LogLockProposalEvent, V3] =
    json.persister[LogLockProposalEvent, V3](
      "user-history-lock-proposal",
      from[V1]
        .to[V2](
          _.update("context" / "customData" ! set[Map[String, String]](Map.empty))
            .update("action" / "arguments" / "requestContext" / "customData" ! set[Map[String, String]](Map.empty))
        )
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          ).update(
            "action" / "arguments" / "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val logUserProposalEventSerializer: JsonPersister[LogUserProposalEvent, V3] =
    json.persister[LogUserProposalEvent, V3](
      "user-history-sent-proposal",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logUserVoteEventSerializer: JsonPersister[LogUserVoteEvent, V4] =
    json.persister[LogUserVoteEvent, V4](
      "user-history-vote-proposal",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logUserUnvoteEventSerializer: JsonPersister[LogUserUnvoteEvent, V4] =
    json.persister[LogUserUnvoteEvent, V4](
      "user-history-unvote-proposal",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logUserQualificationEventSerializer: JsonPersister[LogUserQualificationEvent, V4] =
    json.persister[LogUserQualificationEvent, V4](
      "user-history-qualification-vote",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logUserUnqualificationEventSerializer: JsonPersister[LogUserUnqualificationEvent, V4] =
    json.persister[LogUserUnqualificationEvent, V4](
      "user-history-unqualification-vote",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "trust" ! set[String]("trusted")))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val filterWithRequestContextInAction: SeqLens = filter(
    "type".is[String](
      eventType =>
        Seq(
          "LogUserCreateSequenceEvent",
          "LogUserAddProposalsSequenceEvent",
          "LogUserRemoveProposalsSequenceEvent",
          "LogUserUpdateSequenceEvent",
          "LogLockProposalEvent",
          "LogPostponeProposalEvent",
          "LogRefuseProposalEvent",
          "LogAcceptProposalEvent"
        ).contains(eventType)
    )
  )
  private val userHistorySerializer: JsonPersister[UserHistory, V7] =
    json.persister[UserHistory, V7](
      "user-history",
      from[V1]
        .to[V2](
          _.update(
            "events" / filter("type".is[String](_ == "LogRegisterCitizenEvent")) / "action" / "arguments" / "language" !
              set[String]("fr")
          ).update(
            "events" / filter("type".is[String](_ == "LogRegisterCitizenEvent")) / "action" / "arguments" / "country" !
              set[String]("FR")
          )
        )
        .to[V3](
          _.update(
            "events" / filter("type".is[String](_ == "LogUserStartSequenceEvent")) / "action" / "arguments" / "includedProposals" !
              set[Seq[String]](Seq.empty)
          )
        )
        .to[V4] { json =>
          json.update("events" / filter("type".is[String](_ == "LogRegisterCitizenEvent")) ! modify[JsValue] {
            event =>
              val isBeforeDateFix: Boolean =
                ZonedDateTime.parse(event.extract[String]("action" / "date")).isBefore(countryFixDate)

              if (isBeforeDateFix) {
                event.extract[String]("context" / "language".?).getOrElse("fr") match {
                  case "it" =>
                    event
                      .update("context" / "country" ! set[String]("IT"))
                      .update("context" / "source" ! set[String]("core"))
                  case "en" =>
                    event
                      .update("context" / "country" ! set[String]("GB"))
                      .update("context" / "source" ! set[String]("core"))
                  case _ =>
                    event
                      .update("context" / "language" ! set[String]("fr"))
                      .update("context" / "country" ! set[String]("FR"))
                      .update("context" / "source" ! set[String]("core"))
                }
              } else {
                event
              }
          })
        }
        .to[V5] { json =>
          val migratedEvents =
            Set("LogUserVoteEvent", "LogUserUnvoteEvent", "LogUserQualificationEvent", "LogUserUnqualificationEvent")
          json.update(
            "events" /
              filter("type".is[String](event => migratedEvents.contains(event))) /
              "action" /
              "arguments" /
              "trust" !
              set[String]("trusted")
          )
        }
        .to[V6](
          _.update("events" / * / "context" / "customData" ! set[Map[String, String]](Map.empty))
            .update(
              "events" / filterWithRequestContextInAction / "action" / "arguments" / "requestContext" / "customData" !
                set[Map[String, String]](Map.empty)
            )
        )
        .to[V7](
          _.update(
            "events" / * / "context" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          ).update(
            "events" / filterWithRequestContextInAction / "action" / "arguments" / "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val logUserCreateSequenceEventSerializer: JsonPersister[LogUserCreateSequenceEvent, V3] =
    json.persister[LogUserCreateSequenceEvent, V3](
      "user-history-create-sequence",
      from[V1]
        .to[V2](
          _.update("context" / "customData" ! set[Map[String, String]](Map.empty))
            .update("action" / "arguments" / "requestContext" / "customData" ! set[Map[String, String]](Map.empty))
        )
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          ).update(
            "action" / "arguments" / "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val logUserAddProposalsSequenceEventSerializer: JsonPersister[LogUserAddProposalsSequenceEvent, V3] =
    json.persister[LogUserAddProposalsSequenceEvent, V3](
      "user-history-add-proposals-sequence",
      from[V1]
        .to[V2](
          _.update("context" / "customData" ! set[Map[String, String]](Map.empty))
            .update("action" / "arguments" / "requestContext" / "customData" ! set[Map[String, String]](Map.empty))
        )
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          ).update(
            "action" / "arguments" / "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val logUserRemoveSequenceEventSerializer: JsonPersister[LogUserRemoveProposalsSequenceEvent, V3] =
    json.persister[LogUserRemoveProposalsSequenceEvent, V3](
      "user-history-remove-proposals-sequence",
      from[V1]
        .to[V2](
          _.update("context" / "customData" ! set[Map[String, String]](Map.empty))
            .update("action" / "arguments" / "requestContext" / "customData" ! set[Map[String, String]](Map.empty))
        )
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          ).update(
            "action" / "arguments" / "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val logGetProposalDuplicatesEventSerializer: JsonPersister[LogGetProposalDuplicatesEvent, V3] =
    json.persister[LogGetProposalDuplicatesEvent, V3](
      "user-history-get-proposals-duplicate",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logUserUpdateSequenceEventSerializer: JsonPersister[LogUserUpdateSequenceEvent, V3] =
    json.persister[LogUserUpdateSequenceEvent, V3](
      "user-history-update-sequence",
      from[V1]
        .to[V2](
          _.update("context" / "customData" ! set[Map[String, String]](Map.empty))
            .update("action" / "arguments" / "requestContext" / "customData" ! set[Map[String, String]](Map.empty))
        )
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          ).update(
            "action" / "arguments" / "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val logUserSearchSequencesEventSerializer: JsonPersister[LogUserSearchSequencesEvent, V3] =
    json.persister[LogUserSearchSequencesEvent, V3](
      "user-history-search-sequence",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logUserStartSequenceEventSerializer: JsonPersister[LogUserStartSequenceEvent, V5] =
    json.persister[LogUserStartSequenceEvent, V5](
      "user-history-start-sequence",
      from[V1]
        .to[V2](_.update("action" / "arguments" / "includedProposals" ! set[Seq[String]](Seq.empty)))
        .to[V3](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](_.update("action" / "questionId" ! set[Option[String]](None)))
        .to[V5](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logUserAnonymizedEventSerializer: JsonPersister[LogUserAnonymizedEvent, V4] =
    json.persister[LogUserAnonymizedEvent, V4](
      "user-anonymized",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
        .to[V4]("action" / "arguments" / "mode" ! set(Anonymization.Automatic.value))
    )

  private val logUserOptInNewsletterEventSerializer: JsonPersister[LogUserOptInNewsletterEvent, V3] =
    json.persister[LogUserOptInNewsletterEvent, V3](
      "user-opt-in-newsletter",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logUserOptOutNewsletterEventSerializer: JsonPersister[LogUserOptOutNewsletterEvent, V3] =
    json.persister[LogUserOptOutNewsletterEvent, V3](
      "user-opt-out-newsletter",
      from[V1]
        .to[V2](_.update("context" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
          )
        )
    )

  private val logUserConnectedEventSerializer: JsonPersister[LogUserConnectedEvent, V2] =
    json.persister[LogUserConnectedEvent, V2](
      "user-connected",
      from[V1].to[V2](
        _.update(
          "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
        )
      )
    )

  private val logUserUploadedAvatarEventSerializer: JsonPersister[LogUserUploadedAvatarEvent, V2] =
    json.persister[LogUserUploadedAvatarEvent, V2](
      "user-uploaded-avatar",
      from[V1].to[V2](
        _.update(
          "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
        )
      )
    )

  private val logOrganisationEmailChangedEventSerializer: JsonPersister[LogOrganisationEmailChangedEvent, V2] =
    json.persister[LogOrganisationEmailChangedEvent, V2](
      "organisation-email-changed",
      from[V1].to[V2](
        _.update(
          "context" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))
        )
      )
    )

  val defaultVoteDate: ZonedDateTime = ZonedDateTime.parse("2018-10-10T00:00:00Z")
  private val userVotesAndQualifications: JsonPersister[UserVotesAndQualifications, V3] =
    json.persister[UserVotesAndQualifications, V3](
      "user-votes-and-qualifications",
      from[V1]
        .to[V2](_.update("votesAndQualifications" ! modify[Map[String, JsValue]] { voteAndQualifications =>
          voteAndQualifications.map {
            case (key, value) =>
              key -> value.update("date" ! set[ZonedDateTime](defaultVoteDate))
          }
        }))
        .to[V3](
          _.update("votesAndQualifications" ! modify[Map[String, JsObject]] {
            voteAndQualifications =>
              voteAndQualifications.map {
                case (key, proposalVotes) =>
                  val fields: Map[String, JsValue] = proposalVotes.fields
                  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
                  val qualifications =
                    fields("qualificationKeys").asInstanceOf[JsArray].elements.map(_.asInstanceOf[JsString])
                  val newQualifications: Map[String, JsValue] = qualifications.map(_.value -> JsString("trusted")).toMap
                  val modifiedFields: Map[String, JsValue] = fields + ("qualificationKeys" -> JsObject(
                    newQualifications
                  )) + ("trust" -> JsString("trusted"))
                  key -> JsObject(modifiedFields)
              }
          })
        )
    )

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      logRegisterCitizenEventSerializer,
      logSearchProposalsEventSerializer,
      logAcceptProposalEventSerializer,
      logRefuseProposalEventSerializer,
      logPostponeProposalEventSerializer,
      logLockProposalEventSerializer,
      logUserProposalEventSerializer,
      logUserVoteEventSerializer,
      logUserUnvoteEventSerializer,
      logUserQualificationEventSerializer,
      logUserUnqualificationEventSerializer,
      userHistorySerializer,
      logUserCreateSequenceEventSerializer,
      logUserUpdateSequenceEventSerializer,
      logUserAddProposalsSequenceEventSerializer,
      logUserRemoveSequenceEventSerializer,
      logGetProposalDuplicatesEventSerializer,
      logUserSearchSequencesEventSerializer,
      logUserStartSequenceEventSerializer,
      userVotesAndQualifications,
      logUserAnonymizedEventSerializer,
      logUserOptInNewsletterEventSerializer,
      logUserOptOutNewsletterEventSerializer,
      logUserConnectedEventSerializer,
      logUserUploadedAvatarEventSerializer,
      logOrganisationEmailChangedEventSerializer
    )
}

object UserHistorySerializers {
  def apply(securityConfiguration: SecurityConfiguration): UserHistorySerializers =
    new UserHistorySerializers(securityConfiguration)
}
