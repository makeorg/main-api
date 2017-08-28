package org.make.api.userhistory

import org.make.api.technical.SprayJsonFormatters
import org.make.api.userhistory.UserHistoryActor.{ProposalAcceptedLogged, UserRegisteredLogged, UserSearchedLogged}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.json.JsonPersister
import stamina.{json, V1}

object UserHistorySerializers extends SprayJsonFormatters {

  implicit private val userSearchedLoggedFormatter: RootJsonFormat[UserSearchedLogged] =
    DefaultJsonProtocol.jsonFormat3(UserSearchedLogged.apply)

  implicit private val proposalAcceptedLoggedFormatter: RootJsonFormat[ProposalAcceptedLogged] =
    DefaultJsonProtocol.jsonFormat3(ProposalAcceptedLogged.apply)

  implicit private val userRegisteredLoggedFormatter: RootJsonFormat[UserRegisteredLogged] =
    DefaultJsonProtocol.jsonFormat3(UserRegisteredLogged.apply)

  private val userSearchedLoggedSerializer: JsonPersister[UserSearchedLogged, V1] =
    json.persister[UserSearchedLogged]("user-history-search")

  private val proposalAcceptedLoggedSerializer: JsonPersister[ProposalAcceptedLogged, V1] =
    json.persister[ProposalAcceptedLogged]("user-history-accepted-proposal")

  private val userRegisteredLoggedSerializer: JsonPersister[UserRegisteredLogged, V1] =
    json.persister[UserRegisteredLogged]("user-history-registered")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(userSearchedLoggedSerializer, proposalAcceptedLoggedSerializer, userRegisteredLoggedSerializer)
}
