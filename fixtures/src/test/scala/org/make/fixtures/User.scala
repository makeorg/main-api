package org.make.fixtures

import io.gatling.core.Predef._
import io.gatling.core.feeder.RecordSeqFeederBuilder
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.concurrent.duration._

object User extends SimulationConfig {

  val userFeeder: RecordSeqFeederBuilder[Any] = ssv(userFeederPath, '"', '\\').convert {
    case ("dateOfBirth", dateOfBirth) =>
      dateOfBirth match {
        case dateOfBirth if dateOfBirth.isEmpty => "null"
        case dateOfBirth                        => s""""$dateOfBirth""""
      }
  }

  val vffUserFeeder: RecordSeqFeederBuilder[Any] = ssv(vffUserFeederPath, '"', '\\').convert {
    case ("dateOfBirth", dateOfBirth) =>
      dateOfBirth match {
        case dateOfBirth if dateOfBirth.isEmpty => "null"
        case dateOfBirth                        => s""""$dateOfBirth""""
      }
  }

  val defaultPause = 2.seconds
}

object UserChainBuilder extends SimulationConfig {
  private val statusOk = 200
  private val statusCreated = 201

  def authenticate(params: UserAuthParams, withCheckUser: Boolean = false): ChainBuilder = {

    val authBuilder: HttpRequestBuilder = MakeServicesBuilder.authenticateBuilder
      .formParam("username", params.username)
      .formParam("password", params.password)
      .formParam("grant_type", "password")
      .asFormUrlEncoded
      .check(status.is(statusOk), headerRegex("Set-Cookie", "make-secure"))

    val chainBuilder: ChainBuilder = exec(authBuilder)
    if (withCheckUser) {
      chainBuilder.exec(MakeServicesBuilder.getUserBuilder.check(jsonPath("$.username").is(s"${params.username}")))
    }

    chainBuilder
  }

  lazy val authenticateAsAdmin: ChainBuilder = {
    authenticate(adminAuthParams)
  }

  val createUser: ChainBuilder = {
    exec(
      MakeServicesBuilder.createUserBuilder
        .body(ElFileBody("jsonModel/createUser.json"))
        .asJSON
        .check(status.is(statusCreated))
    )
  }

  val getUser: ChainBuilder = {
    exec(MakeServicesBuilder.getUserBuilder.check(status.is(statusOk)))
  }
}
