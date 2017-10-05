package org.make.api.sessionhistory

import org.make.core.SprayJsonFormatters
import org.make.core.session._
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

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      logSessionSearchEventSerializer,
      logSessionVoteEventSerializer,
      logSessionUnvoteEventSerializer,
      logSessionQualificationEventSerializer,
      logSessionUnqualificationEventSerializer
    )
}
