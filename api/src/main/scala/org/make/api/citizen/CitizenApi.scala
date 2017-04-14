package org.make.api.citizen

import java.time.LocalDate

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.{NotFound, OK}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import org.make.api.Formatters
import org.make.core.citizen.CitizenId

import scala.util.Try

trait CitizenApi extends Formatters {
  this: CitizenServiceComponent =>


  def getCitizen: Route = get {
    path("citizen" / citizenId) { citizenId =>
      onSuccess(citizenService.getCitizen(citizenId)) {
        case Some(citizen) => complete(citizen)
        case None => complete(NotFound)
      }
    }
  }


  def register: Route = post {
    path("citizen") {
      decodeRequest {
        entity(as[RegisterCitizenRequest]) { request: RegisterCitizenRequest =>
          onSuccess(citizenService.register(
            email = request.email,
            dateOfBirth = request.dateOfBirth,
            firstName = request.firstName,
            lastName = request.lastName
          )) {
            case Some(citizen) => complete(citizen)
            case None => complete(NotFound)
          }
        }
      }
    }
  }

  def routes: Route = register ~ getCitizen

  val citizenId: PathMatcher1[CitizenId] = Segment.flatMap(id => Try(CitizenId(id)).toOption)

}

case class RegisterCitizenRequest(
                                   email: String,
                                   dateOfBirth: LocalDate,
                                   firstName: String,
                                   lastName: String
                                 )

