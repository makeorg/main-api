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
import org.make.core.{RequestContext, SprayJsonFormatters}
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import spray.json.{JsArray, JsObject, JsString, JsValue}
import stamina._
import stamina.json._

object UserHistorySerializers extends SprayJsonFormatters {

  val countryFixDate: ZonedDateTime = ZonedDateTime.parse("2018-09-01T00:00:00Z")
  private val logRegisterCitizenEventSerializer: JsonPersister[LogRegisterCitizenEvent, V4] =
    json.persister[LogRegisterCitizenEvent, V4](
      "user-history-registered",
      from[V1]
        .to[V2](
          _.update('action / 'arguments / 'country ! set[String]("FR"))
            .update('action / 'arguments / 'language ! set[String]("fr"))
        )
        .to[V3](identity)
        .to[V4] { json =>
          val actionDate: ZonedDateTime = ZonedDateTime.parse(json.extract[String]('action / 'date))

          if (actionDate.isBefore(countryFixDate)) {

            json.extract[RequestContext]('context).language.map(_.value) match {

              case Some("fr") =>
                json
                  .update('context / 'source ! set[String]("core"))
                  .update('context / 'country ! set[String]("FR"))
              case Some("it") =>
                json
                  .update('context / 'source ! set[String]("core"))
                  .update('context / 'country ! set[String]("IT"))
              case Some("en") =>
                json
                  .update('context / 'source ! set[String]("core"))
                  .update('context / 'country ! set[String]("GB"))
              case _ =>
                json
                  .update('context / 'source ! set[String]("core"))
                  .update('context / 'country ! set[String]("FR"))
                  .update('context / 'language ! set[String]("fr"))

            }
          } else {
            json
          }
        }
    )

  private val logSearchProposalsEventSerializer: JsonPersister[LogUserSearchProposalsEvent, V1] =
    json.persister[LogUserSearchProposalsEvent]("user-history-searched")

  private val logAcceptProposalEventSerializer: JsonPersister[LogAcceptProposalEvent, V1] =
    json.persister[LogAcceptProposalEvent]("user-history-accepted-proposal")

  private val logRefuseProposalEventSerializer: JsonPersister[LogRefuseProposalEvent, V1] =
    json.persister[LogRefuseProposalEvent]("user-history-refused-proposal")

  private val logPostponeProposalEventSerializer: JsonPersister[LogPostponeProposalEvent, V1] =
    json.persister[LogPostponeProposalEvent]("user-history-postponed-proposal")

  private val logLockProposalEventSerializer: JsonPersister[LogLockProposalEvent, V1] =
    json.persister[LogLockProposalEvent]("user-history-lock-proposal")

  private val logUserProposalEventSerializer: JsonPersister[LogUserProposalEvent, V1] =
    json.persister[LogUserProposalEvent]("user-history-sent-proposal")

  private val logUserVoteEventSerializer: JsonPersister[LogUserVoteEvent, V2] =
    json.persister[LogUserVoteEvent, V2](
      "user-history-vote-proposal",
      from[V1].to[V2](_.update('action / 'arguments / 'trust ! set[String]("trusted")))
    )

  private val logUserUnvoteEventSerializer: JsonPersister[LogUserUnvoteEvent, V2] =
    json.persister[LogUserUnvoteEvent, V2](
      "user-history-unvote-proposal",
      from[V1].to[V2](_.update('action / 'arguments / 'trust ! set[String]("trusted")))
    )

  private val logUserQualificationEventSerializer: JsonPersister[LogUserQualificationEvent, V2] =
    json.persister[LogUserQualificationEvent, V2](
      "user-history-qualification-vote",
      from[V1].to[V2](_.update('action / 'arguments / 'trust ! set[String]("trusted")))
    )

  private val logUserUnqualificationEventSerializer: JsonPersister[LogUserUnqualificationEvent, V2] =
    json.persister[LogUserUnqualificationEvent, V2](
      "user-history-unqualification-vote",
      from[V1].to[V2](_.update('action / 'arguments / 'trust ! set[String]("trusted")))
    )

  private val userHistorySerializer: JsonPersister[UserHistory, V5] =
    json.persister[UserHistory, V5](
      "user-history",
      from[V1]
        .to[V2](
          _.update(
            'events / filter("type".is[String](_ == "LogRegisterCitizenEvent")) / 'action / 'arguments / 'language !
              set[String]("fr")
          ).update(
            'events / filter("type".is[String](_ == "LogRegisterCitizenEvent")) / 'action / 'arguments / 'country !
              set[String]("FR")
          )
        )
        .to[V3](
          _.update(
            'events / filter("type".is[String](_ == "LogUserStartSequenceEvent")) / 'action / 'arguments / 'includedProposals !
              set[Seq[String]](Seq.empty)
          )
        )
        .to[V4] { json =>
          json.update('events / filter("type".is[String](_ == "LogRegisterCitizenEvent")) ! modify[JsValue] {
            event =>
              val isBeforeDateFix: Boolean =
                ZonedDateTime.parse(event.extract[String]('action / 'date)).isBefore(countryFixDate)

              if (isBeforeDateFix) {
                event.extract[String]('context / 'language.?).getOrElse("fr") match {
                  case "it" =>
                    event
                      .update('context / 'country ! set[String]("IT"))
                      .update('context / 'source ! set[String]("core"))
                  case "en" =>
                    event
                      .update('context / 'country ! set[String]("GB"))
                      .update('context / 'source ! set[String]("core"))
                  case _ =>
                    event
                      .update('context / 'language ! set[String]("fr"))
                      .update('context / 'country ! set[String]("FR"))
                      .update('context / 'source ! set[String]("core"))
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
            'events /
              filter("type".is[String](event => migratedEvents.contains(event))) /
              'action /
              'arguments /
              'trust !
              set[String]("trusted")
          )
        }
    )

  private val logUserCreateSequenceEventSerializer: JsonPersister[LogUserCreateSequenceEvent, V1] =
    json.persister[LogUserCreateSequenceEvent]("user-history-create-sequence")

  private val logUserAddProposalsSequenceEventSerializer: JsonPersister[LogUserAddProposalsSequenceEvent, V1] =
    json.persister[LogUserAddProposalsSequenceEvent]("user-history-add-proposals-sequence")

  private val logUserRemoveSequenceEventSerializer: JsonPersister[LogUserRemoveProposalsSequenceEvent, V1] =
    json.persister[LogUserRemoveProposalsSequenceEvent]("user-history-remove-proposals-sequence")

  private val logGetProposalDuplicatesEventSerializer: JsonPersister[LogGetProposalDuplicatesEvent, V1] =
    json.persister[LogGetProposalDuplicatesEvent]("user-history-get-proposals-duplicate")

  private val logUserUpdateSequenceEventSerializer: JsonPersister[LogUserUpdateSequenceEvent, V1] =
    json.persister[LogUserUpdateSequenceEvent]("user-history-update-sequence")

  private val logUserSearchSequencesEventSerializer: JsonPersister[LogUserSearchSequencesEvent, V1] =
    json.persister[LogUserSearchSequencesEvent]("user-history-search-sequence")

  private val logUserStartSequenceEventSerializer: JsonPersister[LogUserStartSequenceEvent, V2] =
    json.persister[LogUserStartSequenceEvent, V2](
      "user-history-start-sequence",
      from[V1].to[V2](_.update('action / 'arguments / 'includedProposals ! set[Seq[String]](Seq.empty)))
    )

  private val logUserAnonymizedEventSerializer: JsonPersister[LogUserAnonymizedEvent, V1] =
    json.persister[LogUserAnonymizedEvent]("user-anonymized")

  private val logUserOptInNewsletterEventSerializer: JsonPersister[LogUserOptInNewsletterEvent, V1] =
    json.persister[LogUserOptInNewsletterEvent]("user-opt-in-newsletter")

  private val logUserOptOutNewsletterEventSerializer: JsonPersister[LogUserOptOutNewsletterEvent, V1] =
    json.persister[LogUserOptOutNewsletterEvent]("user-opt-out-newsletter")

  val defaultVoteDate: ZonedDateTime = ZonedDateTime.parse("2018-10-10T00:00:00Z")
  private val userVotesAndQualifications: JsonPersister[UserVotesAndQualifications, V3] =
    json.persister[UserVotesAndQualifications, V3](
      "user-votes-and-qualifications",
      from[V1]
        .to[V2](_.update('votesAndQualifications ! modify[Map[String, JsValue]] { voteAndQualifications =>
          voteAndQualifications.mapValues {
            _.update('date ! set[ZonedDateTime](defaultVoteDate))
          }
        }))
        .to[V3](_.update('votesAndQualifications ! modify[Map[String, JsObject]] {
          voteAndQualifications =>
            voteAndQualifications
              .mapValues[JsObject] {
                proposalVotes =>
                  val fields: Map[String, JsValue] = proposalVotes.fields
                  val qualifications =
                    fields("qualificationKeys").asInstanceOf[JsArray].elements.map(_.asInstanceOf[JsString])
                  val newQualifications: Map[String, JsValue] = qualifications.map(_.value -> JsString("trusted")).toMap
                  val modifiedFields: Map[String, JsValue] = fields + ("qualificationKeys" -> JsObject(
                    newQualifications
                  )) + ("trust" -> JsString("trusted"))
                  JsObject(modifiedFields)
              }
        }))
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
      logUserOptOutNewsletterEventSerializer
    )
}
