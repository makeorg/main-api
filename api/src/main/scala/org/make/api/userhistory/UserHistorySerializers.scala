package org.make.api.userhistory

import org.make.core.SprayJsonFormatters
import org.make.core.user._
import stamina.json.JsonPersister
import stamina.{json, V1}

object UserHistorySerializers extends SprayJsonFormatters {

  private val logRegisterCitizenEventSerializer: JsonPersister[LogRegisterCitizenEvent, V1] =
    json.persister[LogRegisterCitizenEvent]("user-history-registered")

  private val logSearchProposalsEventSerializer: JsonPersister[LogSearchProposalsEvent, V1] =
    json.persister[LogSearchProposalsEvent]("user-history-searched")

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
    json.persister[LogUserQualificationEvent]("user-history-qualification-proposal")

  private val logUserUnqualificationEventSerializer: JsonPersister[LogUserUnqualificationEvent, V1] =
    json.persister[LogUserUnqualificationEvent]("user-history-unqualification-proposal")

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
      logUserUnqualificationEventSerializer
    )
}
