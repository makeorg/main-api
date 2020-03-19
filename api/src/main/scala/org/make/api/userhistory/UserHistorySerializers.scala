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

import java.time.ZonedDateTime

import org.make.api.userhistory.UserHistoryActor.{UserHistory, UserVotesAndQualifications}
import org.make.core.SprayJsonFormatters
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import spray.json.{JsArray, JsObject, JsString, JsValue}
import stamina._
import stamina.json._

object UserHistorySerializers extends SprayJsonFormatters {

  val countryFixDate: ZonedDateTime = ZonedDateTime.parse("2018-09-01T00:00:00Z")
  private val logRegisterCitizenEventSerializer: JsonPersister[LogRegisterCitizenEvent, V5] =
    json.persister[LogRegisterCitizenEvent, V5](
      "user-history-registered",
      from[V1]
        .to[V2](
          _.update(Symbol("action") / Symbol("arguments") / Symbol("country") ! set[String]("FR"))
            .update(Symbol("action") / Symbol("arguments") / Symbol("language") ! set[String]("fr"))
        )
        .to[V3](identity)
        .to[V4] { json =>
          val actionDate: ZonedDateTime = ZonedDateTime.parse(json.extract[String](Symbol("action") / Symbol("date")))

          if (actionDate.isBefore(countryFixDate)) {

            json.extract[JsObject](Symbol("context")).getFields("language") match {

              case Seq(JsString("fr")) =>
                json
                  .update(Symbol("context") / Symbol("source") ! set[String]("core"))
                  .update(Symbol("context") / Symbol("country") ! set[String]("FR"))
              case Seq(JsString("it")) =>
                json
                  .update(Symbol("context") / Symbol("source") ! set[String]("core"))
                  .update(Symbol("context") / Symbol("country") ! set[String]("IT"))
              case Seq(JsString("en")) =>
                json
                  .update(Symbol("context") / Symbol("source") ! set[String]("core"))
                  .update(Symbol("context") / Symbol("country") ! set[String]("GB"))
              case _ =>
                json
                  .update(Symbol("context") / Symbol("source") ! set[String]("core"))
                  .update(Symbol("context") / Symbol("country") ! set[String]("FR"))
                  .update(Symbol("context") / Symbol("language") ! set[String]("fr"))
            }
          } else {
            json
          }
        }
        .to[V5] {
          _.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty))
        }
    )

  private val logSearchProposalsEventSerializer: JsonPersister[LogUserSearchProposalsEvent, V2] =
    json.persister[LogUserSearchProposalsEvent, V2](
      "user-history-searched",
      from[V1].to[V2](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logAcceptProposalEventSerializer: JsonPersister[LogAcceptProposalEvent, V2] =
    json.persister[LogAcceptProposalEvent, V2](
      "user-history-accepted-proposal",
      from[V1].to[V2](
        _.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty))
          .update(
            Symbol("action") / Symbol("arguments") / Symbol("requestContext") / Symbol("customData") ! set[Map[String,
                                                                                                               String]](
              Map.empty
            )
          )
      )
    )

  private val logRefuseProposalEventSerializer: JsonPersister[LogRefuseProposalEvent, V2] =
    json.persister[LogRefuseProposalEvent, V2](
      "user-history-refused-proposal",
      from[V1].to[V2](
        _.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty))
          .update(
            Symbol("action") / Symbol("arguments") / Symbol("requestContext") / Symbol("customData") ! set[Map[String,
                                                                                                               String]](
              Map.empty
            )
          )
      )
    )

  private val logPostponeProposalEventSerializer: JsonPersister[LogPostponeProposalEvent, V2] =
    json.persister[LogPostponeProposalEvent, V2](
      "user-history-postponed-proposal",
      from[V1].to[V2](
        _.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty))
          .update(
            Symbol("action") / Symbol("arguments") / Symbol("requestContext") / Symbol("customData") ! set[Map[String,
                                                                                                               String]](
              Map.empty
            )
          )
      )
    )

  private val logLockProposalEventSerializer: JsonPersister[LogLockProposalEvent, V2] =
    json.persister[LogLockProposalEvent, V2](
      "user-history-lock-proposal",
      from[V1].to[V2](
        _.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty))
          .update(
            Symbol("action") / Symbol("arguments") / Symbol("requestContext") / Symbol("customData") ! set[Map[String,
                                                                                                               String]](
              Map.empty
            )
          )
      )
    )

  private val logUserProposalEventSerializer: JsonPersister[LogUserProposalEvent, V2] =
    json.persister[LogUserProposalEvent, V2](
      "user-history-sent-proposal",
      from[V1].to[V2](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logUserVoteEventSerializer: JsonPersister[LogUserVoteEvent, V3] =
    json.persister[LogUserVoteEvent, V3](
      "user-history-vote-proposal",
      from[V1]
        .to[V2](_.update(Symbol("action") / Symbol("arguments") / Symbol("trust") ! set[String]("trusted")))
        .to[V3](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logUserUnvoteEventSerializer: JsonPersister[LogUserUnvoteEvent, V3] =
    json.persister[LogUserUnvoteEvent, V3](
      "user-history-unvote-proposal",
      from[V1]
        .to[V2](_.update(Symbol("action") / Symbol("arguments") / Symbol("trust") ! set[String]("trusted")))
        .to[V3](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logUserQualificationEventSerializer: JsonPersister[LogUserQualificationEvent, V3] =
    json.persister[LogUserQualificationEvent, V3](
      "user-history-qualification-vote",
      from[V1]
        .to[V2](_.update(Symbol("action") / Symbol("arguments") / Symbol("trust") ! set[String]("trusted")))
        .to[V3](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logUserUnqualificationEventSerializer: JsonPersister[LogUserUnqualificationEvent, V3] =
    json.persister[LogUserUnqualificationEvent, V3](
      "user-history-unqualification-vote",
      from[V1]
        .to[V2](_.update(Symbol("action") / Symbol("arguments") / Symbol("trust") ! set[String]("trusted")))
        .to[V3](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val userHistorySerializer: JsonPersister[UserHistory, V6] =
    json.persister[UserHistory, V6](
      "user-history",
      from[V1]
        .to[V2](
          _.update(
            Symbol("events") / filter("type".is[String](_ == "LogRegisterCitizenEvent")) / Symbol("action") / Symbol(
              "arguments"
            ) / Symbol("language") !
              set[String]("fr")
          ).update(
            Symbol("events") / filter("type".is[String](_ == "LogRegisterCitizenEvent")) / Symbol("action") / Symbol(
              "arguments"
            ) / Symbol("country") !
              set[String]("FR")
          )
        )
        .to[V3](
          _.update(
            Symbol("events") / filter("type".is[String](_ == "LogUserStartSequenceEvent")) / Symbol("action") / Symbol(
              "arguments"
            ) / Symbol("includedProposals") !
              set[Seq[String]](Seq.empty)
          )
        )
        .to[V4] { json =>
          json.update(Symbol("events") / filter("type".is[String](_ == "LogRegisterCitizenEvent")) ! modify[JsValue] {
            event =>
              val isBeforeDateFix: Boolean =
                ZonedDateTime.parse(event.extract[String](Symbol("action") / Symbol("date"))).isBefore(countryFixDate)

              if (isBeforeDateFix) {
                event.extract[String](Symbol("context") / Symbol("language").?).getOrElse("fr") match {
                  case "it" =>
                    event
                      .update(Symbol("context") / Symbol("country") ! set[String]("IT"))
                      .update(Symbol("context") / Symbol("source") ! set[String]("core"))
                  case "en" =>
                    event
                      .update(Symbol("context") / Symbol("country") ! set[String]("GB"))
                      .update(Symbol("context") / Symbol("source") ! set[String]("core"))
                  case _ =>
                    event
                      .update(Symbol("context") / Symbol("language") ! set[String]("fr"))
                      .update(Symbol("context") / Symbol("country") ! set[String]("FR"))
                      .update(Symbol("context") / Symbol("source") ! set[String]("core"))
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
            Symbol("events") /
              filter("type".is[String](event => migratedEvents.contains(event))) /
              Symbol("action") /
              Symbol("arguments") /
              Symbol("trust") !
              set[String]("trusted")
          )
        }
        .to[V6](
          _.update(
            Symbol("events") / * / Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)
          ).update(
            Symbol("events") / filter(
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
            ) / Symbol("action") / Symbol("arguments") / Symbol("requestContext") / Symbol("customData") ! set[
              Map[String, String]
            ](Map.empty)
          )
        )
    )

  private val logUserCreateSequenceEventSerializer: JsonPersister[LogUserCreateSequenceEvent, V2] =
    json.persister[LogUserCreateSequenceEvent, V2](
      "user-history-create-sequence",
      from[V1].to[V2](
        _.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty))
          .update(
            Symbol("action") / Symbol("arguments") / Symbol("requestContext") / Symbol("customData") ! set[Map[String,
                                                                                                               String]](
              Map.empty
            )
          )
      )
    )

  private val logUserAddProposalsSequenceEventSerializer: JsonPersister[LogUserAddProposalsSequenceEvent, V2] =
    json.persister[LogUserAddProposalsSequenceEvent, V2](
      "user-history-add-proposals-sequence",
      from[V1].to[V2](
        _.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty))
          .update(
            Symbol("action") / Symbol("arguments") / Symbol("requestContext") / Symbol("customData") ! set[Map[String,
                                                                                                               String]](
              Map.empty
            )
          )
      )
    )

  private val logUserRemoveSequenceEventSerializer: JsonPersister[LogUserRemoveProposalsSequenceEvent, V2] =
    json.persister[LogUserRemoveProposalsSequenceEvent, V2](
      "user-history-remove-proposals-sequence",
      from[V1].to[V2](
        _.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty))
          .update(
            Symbol("action") / Symbol("arguments") / Symbol("requestContext") / Symbol("customData") ! set[Map[String,
                                                                                                               String]](
              Map.empty
            )
          )
      )
    )

  private val logGetProposalDuplicatesEventSerializer: JsonPersister[LogGetProposalDuplicatesEvent, V2] =
    json.persister[LogGetProposalDuplicatesEvent, V2](
      "user-history-get-proposals-duplicate",
      from[V1].to[V2](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logUserUpdateSequenceEventSerializer: JsonPersister[LogUserUpdateSequenceEvent, V2] =
    json.persister[LogUserUpdateSequenceEvent, V2](
      "user-history-update-sequence",
      from[V1].to[V2](
        _.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty))
          .update(
            Symbol("action") / Symbol("arguments") / Symbol("requestContext") / Symbol("customData") ! set[Map[String,
                                                                                                               String]](
              Map.empty
            )
          )
      )
    )

  private val logUserSearchSequencesEventSerializer: JsonPersister[LogUserSearchSequencesEvent, V2] =
    json.persister[LogUserSearchSequencesEvent, V2](
      "user-history-search-sequence",
      from[V1].to[V2](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logUserStartSequenceEventSerializer: JsonPersister[LogUserStartSequenceEvent, V4] =
    json.persister[LogUserStartSequenceEvent, V4](
      "user-history-start-sequence",
      from[V1]
        .to[V2](
          _.update(Symbol("action") / Symbol("arguments") / Symbol("includedProposals") ! set[Seq[String]](Seq.empty))
        )
        .to[V3](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
        .to[V4](_.update(Symbol("action") / Symbol("questionId") ! set[Option[String]](None)))
    )

  private val logUserAnonymizedEventSerializer: JsonPersister[LogUserAnonymizedEvent, V2] =
    json.persister[LogUserAnonymizedEvent, V2](
      "user-anonymized",
      from[V1].to[V2](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logUserOptInNewsletterEventSerializer: JsonPersister[LogUserOptInNewsletterEvent, V2] =
    json.persister[LogUserOptInNewsletterEvent, V2](
      "user-opt-in-newsletter",
      from[V1].to[V2](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logUserOptOutNewsletterEventSerializer: JsonPersister[LogUserOptOutNewsletterEvent, V2] =
    json.persister[LogUserOptOutNewsletterEvent, V2](
      "user-opt-out-newsletter",
      from[V1].to[V2](_.update(Symbol("context") / Symbol("customData") ! set[Map[String, String]](Map.empty)))
    )

  private val logUserConnectedEventSerializer: JsonPersister[LogUserConnectedEvent, V1] =
    json.persister[LogUserConnectedEvent]("user-connected")

  private val logUserUploadedAvatarEventSerializer: JsonPersister[LogUserUploadedAvatarEvent, V1] =
    json.persister[LogUserUploadedAvatarEvent]("user-uploaded-avatar")

  private val logOrganisationEmailChangedEventSerializer: JsonPersister[LogOrganisationEmailChangedEvent, V1] =
    json.persister[LogOrganisationEmailChangedEvent]("organisation-email-changed")

  val defaultVoteDate: ZonedDateTime = ZonedDateTime.parse("2018-10-10T00:00:00Z")
  private val userVotesAndQualifications: JsonPersister[UserVotesAndQualifications, V3] =
    json.persister[UserVotesAndQualifications, V3](
      "user-votes-and-qualifications",
      from[V1]
        .to[V2](_.update(Symbol("votesAndQualifications") ! modify[Map[String, JsValue]] { voteAndQualifications =>
          voteAndQualifications.map {
            case (key, value) =>
              key -> value.update(Symbol("date") ! set[ZonedDateTime](defaultVoteDate))
          }
        }))
        .to[V3](
          _.update(Symbol("votesAndQualifications") ! modify[Map[String, JsObject]] {
            voteAndQualifications =>
              voteAndQualifications.map {
                case (key, proposalVotes) =>
                  val fields: Map[String, JsValue] = proposalVotes.fields
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
