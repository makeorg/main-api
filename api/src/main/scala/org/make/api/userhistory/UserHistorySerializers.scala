package org.make.api.userhistory

import org.make.api.userhistory.UserHistoryActor.UserHistory
import org.make.core.SprayJsonFormatters
import stamina.json.JsonPersister
import stamina.{json, V1}

object UserHistorySerializers extends SprayJsonFormatters {

  private val logRegisterCitizenEventSerializer: JsonPersister[LogRegisterCitizenEvent, V1] =
    json.persister[LogRegisterCitizenEvent]("user-history-registered")

  private val logSearchProposalsEventSerializer: JsonPersister[LogUserSearchProposalsEvent, V1] =
    json.persister[LogUserSearchProposalsEvent]("user-history-searched")

  private val logAcceptProposalEventSerializer: JsonPersister[LogAcceptProposalEvent, V1] =
    json.persister[LogAcceptProposalEvent]("user-history-accepted-proposal")

  private val logRefuseProposalEventSerializer: JsonPersister[LogRefuseProposalEvent, V1] =
    json.persister[LogRefuseProposalEvent]("user-history-refused-proposal")

  private val logUserProposalEventSerializer: JsonPersister[LogUserProposalEvent, V1] =
    json.persister[LogUserProposalEvent]("user-history-sent-proposal")

  private val logUserVoteEventSerializer: JsonPersister[LogUserVoteEvent, V1] =
    json.persister[LogUserVoteEvent]("user-history-vote-proposal")

  private val logUserUnvoteEventSerializer: JsonPersister[LogUserUnvoteEvent, V1] =
    json.persister[LogUserUnvoteEvent]("user-history-unvote-proposal")

  private val logUserQualificationEventSerializer: JsonPersister[LogUserQualificationEvent, V1] =
    json.persister[LogUserQualificationEvent]("user-history-qualification-vote")

  private val logUserUnqualificationEventSerializer: JsonPersister[LogUserUnqualificationEvent, V1] =
    json.persister[LogUserUnqualificationEvent]("user-history-unqualification-vote")

  private val userHistorySerializer: JsonPersister[UserHistory, V1] =
    json.persister[UserHistory]("user-history")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      logRegisterCitizenEventSerializer,
      logSearchProposalsEventSerializer,
      logAcceptProposalEventSerializer,
      logRefuseProposalEventSerializer,
      logUserProposalEventSerializer,
      logUserVoteEventSerializer,
      logUserUnvoteEventSerializer,
      logUserQualificationEventSerializer,
      logUserUnqualificationEventSerializer,
      userHistorySerializer
    )
}
