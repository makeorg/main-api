package org.make.api.citizen

import org.make.api.technical.SprayJsonFormatters
import org.make.core.citizen.Citizen
import org.make.core.citizen.CitizenEvent.{CitizenRegistered, CitizenViewed}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.json._
import stamina.{StaminaAkkaSerializer, V1}

class CitizenSerializers
    extends StaminaAkkaSerializer(
      CitizenSerializers.citizenRegisteredSerializer,
      CitizenSerializers.citizenViewedSerializer
    )

object CitizenSerializers extends SprayJsonFormatters {

  implicit val citizenRegisteredFormatter: RootJsonFormat[CitizenRegistered] =
    DefaultJsonProtocol.jsonFormat5(CitizenRegistered)

  implicit val citizenViewedFormatter: RootJsonFormat[CitizenViewed] =
    DefaultJsonProtocol.jsonFormat1(CitizenViewed)

  implicit val citizenFormatter: RootJsonFormat[Citizen] =
    DefaultJsonProtocol.jsonFormat5(Citizen)

  val citizenRegisteredSerializer: JsonPersister[CitizenRegistered, V1] =
    persister[CitizenRegistered]("citizen-registered")

  val citizenViewedSerializer: JsonPersister[CitizenViewed, V1] =
    persister[CitizenViewed]("citizen-viewed")

  val citizenSerializer: JsonPersister[Citizen, V1] =
    persister[Citizen]("citizen")

}
