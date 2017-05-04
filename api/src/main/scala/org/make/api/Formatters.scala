package org.make.api

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import org.make.core.citizen.{Citizen, CitizenId}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, RootJsonFormat}
import DefaultJsonProtocol._
import com.sksamuel.elastic4s.http.Shards
import com.sksamuel.elastic4s.http.index.IndexResponse
import org.make.api.citizen.RegisterCitizenRequest
import org.make.api.proposition.{ProposePropositionRequest, UpdatePropositionRequest}
import org.make.core.proposition.{Proposition, PropositionId}

import scala.util.{Failure, Success, Try}

/**
  * Created by francois on 14/04/17.
  */
trait Formatters {

  implicit val localDateFormat: JsonFormat[LocalDate] = new JsonFormat[LocalDate] {
    override def write(obj: LocalDate): JsValue = JsString(obj.toString)
    override def read(json: JsValue): LocalDate = json match {
      case (JsString(s)) => Try(LocalDate.parse(s)) match {
        case Success(date) => date
        case Failure(e) => throw DeserializationException(s"Unable to read date from $s", e)
      }
      case _ => throw DeserializationException("Unable to read date")
    }
  }

  implicit val ZonedDateTimeFormat: JsonFormat[ZonedDateTime] = new JsonFormat[ZonedDateTime] {
    override def write(obj: ZonedDateTime): JsValue = JsString(obj.toString)
    override def read(json: JsValue): ZonedDateTime = json match {
      case (JsString(s)) => Try(ZonedDateTime.parse(s)) match {
        case Success(date) => date
        case Failure(e) => throw DeserializationException(s"Unable to read date from $s", e)
      }
      case _ => throw DeserializationException("Unable to read date")
    }
  }

  implicit val uuidFormat: JsonFormat[UUID] = new JsonFormat[UUID] {
    override def write(obj: UUID): JsValue = JsString(obj.toString)
    override def read(json: JsValue): UUID = json match {
      case JsString(s) => Try(UUID.fromString(s)) match {
        case Success(uuid) => uuid
        case Failure(e) => throw DeserializationException(s"Unable to read UUID from $s", e)
      }
      case _ => throw DeserializationException("Unable to read UUID")
    }
  }

  implicit val shardsFormatter: RootJsonFormat[Shards] =
    DefaultJsonProtocol.jsonFormat3(Shards)

  implicit val indexResponseFormatter: RootJsonFormat[IndexResponse] =
    DefaultJsonProtocol.jsonFormat8(IndexResponse)

  implicit val citizenIdFormat: JsonFormat[CitizenId] = new JsonFormat[CitizenId] {
    override def write(obj: CitizenId): JsValue = JsString(obj.value)
    override def read(json: JsValue): CitizenId = json match {
      case JsString(s) => CitizenId(s)
      case _ => throw DeserializationException("Unable to read citizen id")
    }
  }

  implicit val citizenFormatter: RootJsonFormat[Citizen] =
    DefaultJsonProtocol.jsonFormat5(Citizen)

  implicit val registerCitizenRequestFormatter: RootJsonFormat[RegisterCitizenRequest] =
    DefaultJsonProtocol.jsonFormat5(RegisterCitizenRequest)

  implicit val propositionFormatter: RootJsonFormat[Proposition] =
    DefaultJsonProtocol.jsonFormat5(Proposition)

  implicit val propositionIdFormat: JsonFormat[PropositionId] = new JsonFormat[PropositionId] {
    override def write(obj: PropositionId): JsValue = JsString(obj.value)
    override def read(json: JsValue): PropositionId = json match {
      case JsString(s) => PropositionId(s)
      case _ => throw DeserializationException("Unable to read proposition id")
    }
  }

  implicit val proposePropositionRequestFormatter: RootJsonFormat[ProposePropositionRequest] =
    DefaultJsonProtocol.jsonFormat1(ProposePropositionRequest)

  implicit val updatePropositionRequestFormatter: RootJsonFormat[UpdatePropositionRequest] =
    DefaultJsonProtocol.jsonFormat1(UpdatePropositionRequest)

}
