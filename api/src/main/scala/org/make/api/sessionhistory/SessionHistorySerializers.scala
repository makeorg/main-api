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
