package org.make.api.citizen

import org.make.api.technical.SprayJsonFormatters
import org.make.core.citizen.Citizen
import org.make.core.citizen.CitizenEvent.{CitizenRegistered, CitizenViewed}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.V1
import stamina.json._

object CitizenSerializers extends SprayJsonFormatters {

  implicit private val citizenRegisteredFormatter: RootJsonFormat[CitizenRegistered] =
    DefaultJsonProtocol.jsonFormat5(CitizenRegistered)

  implicit private val citizenViewedFormatter: RootJsonFormat[CitizenViewed] =
    DefaultJsonProtocol.jsonFormat1(CitizenViewed)

  implicit private val citizenFormatter: RootJsonFormat[Citizen] =
    DefaultJsonProtocol.jsonFormat5(Citizen)

  private val citizenRegisteredSerializer: JsonPersister[CitizenRegistered, V1] =
    persister[CitizenRegistered]("citizen-registered")

  private val citizenViewedSerializer: JsonPersister[CitizenViewed, V1] =
    persister[CitizenViewed]("citizen-viewed")

  private val citizenSerializer: JsonPersister[Citizen, V1] =
    persister[Citizen]("citizen")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(citizenRegisteredSerializer, citizenViewedSerializer, citizenSerializer)
}
