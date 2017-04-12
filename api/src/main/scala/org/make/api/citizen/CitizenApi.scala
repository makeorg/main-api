package org.make.api.citizen

import java.time.LocalDate

import io.circe.generic.auto._
import io.finch._
import io.finch.circe._
import org.make.api.Predef._
import org.make.core.citizen.{Citizen, CitizenId}

import org.make.api.SerializationPredef._

import scala.concurrent.ExecutionContext.Implicits.global

trait CitizenApi {
  this: CitizenServiceComponent =>

  def citizenId: Endpoint[CitizenId] = string.map(CitizenId)

  def getCitizen: Endpoint[Citizen] = get("citizen" :: citizenId) { citizenId: CitizenId =>
    citizenService.getCitizen(citizenId).asTwitter.map {
      case Some(citizen) => Ok(citizen)
      case None => NotFound(new Exception(s"Citizen with id ${citizenId.value} not found"))
    }

  }

  def register: Endpoint[Citizen] = post("citizen" :: jsonBody[RegisterCitizenRequest]) { body: RegisterCitizenRequest =>
    citizenService.register(
      email = body.email,
      dateOfBirth = body.dateOfBirth,
      firstName = body.firstName,
      lastName = body.lastName
    ).asTwitter.map {
      case Some(citizen) => Ok(citizen)
      case None => InternalServerError(new Exception("Unable to register citizen"))
    }
  }

  case class RegisterCitizenRequest(
                                     email: String,
                                     dateOfBirth: LocalDate,
                                     firstName: String,
                                     lastName: String
                                   )


}
