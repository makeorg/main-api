package org.make.api

import java.time.LocalDate

import org.make.core.citizen.{Citizen, CitizenId}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, RootJsonFormat}
import DefaultJsonProtocol._
import org.make.api.citizen.RegisterCitizenRequest

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
    DefaultJsonProtocol.jsonFormat4(RegisterCitizenRequest)

}
