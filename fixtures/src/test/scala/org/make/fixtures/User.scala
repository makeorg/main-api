package org.make.fixtures

import io.gatling.core.Predef._
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder
import scala.concurrent.duration._

object User extends SimulationConfig {

  val maxClients = 328
  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseURL)
    .acceptHeader("*/*")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader(defaultAcceptLanguage)
    .userAgentHeader(defaultUserAgent)
    .disableCaching

  private val userFeeder = ssv(userFeederPath, '"', '\\').convert {
    case ("dateOfBirth", dateOfBirth) =>
      dateOfBirth match {
        case dateOfBirth if dateOfBirth.isEmpty || dateOfBirth == null => "null"
        case age                                                       => s""""$age""""
      }
  }

  private val defaultPause = 20.seconds

  val scnRegister: ScenarioBuilder = scenario("Register user")
    .feed(userFeeder)
    .exec(
      UserChainBuilder.createUser
        .pause(defaultPause)
    )
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
