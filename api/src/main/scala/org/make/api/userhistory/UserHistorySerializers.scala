package org.make.api.userhistory

import java.time.ZonedDateTime

import org.elasticsearch.search.sort.SortOrder
import org.make.api.technical.SprayJsonFormatters
import org.make.core.user._
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}
import stamina.json.JsonPersister
import stamina.{json, V1}
import org.make.api.proposal.ProposalSerializers._
import org.make.core.proposal._

object UserHistorySerializers extends SprayJsonFormatters {

  implicit val sortOrderFormatted: JsonFormat[SortOrder] = new JsonFormat[SortOrder] {
    override def read(json: JsValue): SortOrder = json match {
      case JsString(s) => SortOrder.valueOf(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: SortOrder): JsValue = {
      JsString(obj.name())
    }
  }

  implicit val sortOptionFormatted: RootJsonFormat[SortOption] =
    DefaultJsonProtocol.jsonFormat2(SortOption.apply)

  implicit val limitOptionFormatted: RootJsonFormat[LimitOption] =
    DefaultJsonProtocol.jsonFormat1(LimitOption.apply)

  implicit val skipOptionFormatted: RootJsonFormat[SkipOption] =
    DefaultJsonProtocol.jsonFormat1(SkipOption.apply)

  implicit val searchOptionsFormatted: RootJsonFormat[SearchOptions] =
    DefaultJsonProtocol.jsonFormat3(SearchOptions.apply)

  implicit val themeSearchFilterFormatted: RootJsonFormat[ThemeSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(ThemeSearchFilter.apply)

  implicit val tagSearchFilterFormatted: RootJsonFormat[TagSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(TagSearchFilter.apply)

  implicit val contentSearchFilterFormatted: RootJsonFormat[ContentSearchFilter] =
    DefaultJsonProtocol.jsonFormat2(ContentSearchFilter.apply)

  implicit val proposalStatusFormatted: JsonFormat[ProposalStatus] = new JsonFormat[ProposalStatus] {
    override def read(json: JsValue): ProposalStatus = json match {
      case JsString(s) => ProposalStatus.statusMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ProposalStatus): JsValue = {
      JsString(obj.shortName)
    }
  }

  implicit val statusSearchFilterFormatted: RootJsonFormat[StatusSearchFilter] =
    DefaultJsonProtocol.jsonFormat1(StatusSearchFilter.apply)

  implicit val searchFilterFormatted: RootJsonFormat[SearchFilter] =
    DefaultJsonProtocol.jsonFormat4(SearchFilter.apply)

  implicit val searchQueryFormatted: RootJsonFormat[SearchQuery] =
    DefaultJsonProtocol.jsonFormat2(SearchQuery.apply)

  implicit val userProposalFormatted: RootJsonFormat[UserProposal] =
    DefaultJsonProtocol.jsonFormat1(UserProposal.apply)

  implicit val userRegisteredFormatted: RootJsonFormat[UserRegistered] =
    DefaultJsonProtocol.jsonFormat6(UserRegistered.apply)

  implicit val searchParametersFormatted: RootJsonFormat[SearchParameters] =
    DefaultJsonProtocol.jsonFormat1(SearchParameters.apply)

  implicit def userActionUserRegisteredFormatted[T](
    implicit formatter: RootJsonFormat[T]
  ): RootJsonFormat[UserAction[T]] =
    DefaultJsonProtocol.jsonFormat3[ZonedDateTime, String, T, UserAction[T]](
      (date: ZonedDateTime, action: String, parameter: T) => UserAction[T](date, action, parameter)
    )

  implicit val logSearchProposalsEventFormatted: RootJsonFormat[LogSearchProposalsEvent] =
    DefaultJsonProtocol.jsonFormat(LogSearchProposalsEvent.apply, "userId", "context", "action")

  implicit val logAcceptProposalEventFormatted: RootJsonFormat[LogAcceptProposalEvent] =
    DefaultJsonProtocol.jsonFormat(LogAcceptProposalEvent.apply, "userId", "context", "action")

  implicit val logUserProposalEventFormatted: RootJsonFormat[LogUserProposalEvent] =
    DefaultJsonProtocol.jsonFormat(LogUserProposalEvent.apply, "userId", "context", "action")

  implicit val logRegisterCitizenEventFormatted: RootJsonFormat[LogRegisterCitizenEvent] =
    DefaultJsonProtocol.jsonFormat(LogRegisterCitizenEvent.apply, "userId", "context", "action")

  private val logRegisterCitizenEventSerializer: JsonPersister[LogRegisterCitizenEvent, V1] =
    json.persister[LogRegisterCitizenEvent]("user-history-registered")

  private val logSearchProposalsEventSerializer: JsonPersister[LogSearchProposalsEvent, V1] =
    json.persister[LogSearchProposalsEvent]("user-history-searched")

  private val logAcceptProposalEventSerializer: JsonPersister[LogAcceptProposalEvent, V1] =
    json.persister[LogAcceptProposalEvent]("user-history-accepted-proposal")

  private val logUserProposalEventSerializer: JsonPersister[LogUserProposalEvent, V1] =
    json.persister[LogUserProposalEvent]("user-history-sent-proposal")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      logRegisterCitizenEventSerializer,
      logSearchProposalsEventSerializer,
      logAcceptProposalEventSerializer,
      logUserProposalEventSerializer
    )
}
