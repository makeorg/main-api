package org.make.fixtures

import java.util

import com.typesafe.config.{ConfigException, ConfigFactory}
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.util.Try

trait SimulationConfig {

  /**
    * Application config object.
    */
  private[this] val config = ConfigFactory.load()

  def getRequiredString(path: String): String = {
    Try(config.getString(path)).getOrElse {
      handleError(path)
    }
  }

  def getRequiredInt(path: String): Int = {
    Try(config.getInt(path)).getOrElse {
      handleError(path)
    }
  }

  def getRequiredStringList(path: String): util.List[String] = {
    Try(config.getStringList(path)).getOrElse {
      handleError(path)
    }
  }

  private[this] def handleError(path: String) = {
    val errMsg = s"Missing required configuration entry: $path"
    throw new ConfigException.Missing(errMsg)
  }

  val baseURL: String = getRequiredString("service.host")
  val customerLink: String = getRequiredString("service.api-link")
  val userFeederPath: String = getRequiredString("feeder.users")
  val proposalFeederPath: String = getRequiredString("feeder.proposals")
  val vffUserFeederPath: String = getRequiredString("feeder.users-vff")
  val vffProposalFeederPath: String = getRequiredString("feeder.proposals-vff")
  val ideaProposalFeederPath: String = getRequiredString("feeder.idea-proposal")

  val defaultUserAgent =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36"
  val defaultAcceptLanguage = "fr-FR,fr;q=0.8,en-US;q=0.6,en;q=0.4"

  val adminAuthParams = UserAuthParams(
    username = getRequiredString("default-admin.email"),
    password = getRequiredString("default-admin.password")
  )
}

case class UserAuthParams(username: String, password: String)

case class ProposalCreateParams(content: String)

object MakeServicesBuilder {
  val authenticateBuilder: HttpRequestBuilder = http("POST_oauth_make_access_token")
    .post("/oauth/make_access_token")
    .header("Content-Type", "application/x-www-form-urlencoded")

  val createUserBuilder: HttpRequestBuilder = http("POST_user")
    .post("/user")
    .header("Content-Type", "application/json")

  val getUserBuilder: HttpRequestBuilder = http("GET_user")
    .get("/user/me")
    .header("Content-Type", "application/json")

  val createProposalBuilder: HttpRequestBuilder = http("POST_proposal")
    .post("/proposals")
    .header("Content-Type", "application/json")

  val acceptProposalBuilder: HttpRequestBuilder =
    http("POST_validate_proposal")
      .post("/moderation/proposals/${proposalId}/accept")
      .header("Content-Type", "application/json")

  val createIdeaBuilder: HttpRequestBuilder = http("POST_idea")
    .post("/moderation/ideas")
    .header("Content-Type", "application/json")

  val addProposalToIdeaBuilder: HttpRequestBuilder = http("PATCH_proposal_idea")
    .patch("/moderation/proposals/${proposalId}")
    .header("Content-Type", "application/json")

  val createOperationBuilder: HttpRequestBuilder = http("POST_operation")
    .post("/moderation/operations")
    .header("Content-Type", "application/json")

  val updateOperationBuilder: HttpRequestBuilder = http("PUT_operation")
    .put("/moderation/operations/${operationId}")
    .header("Content-Type", "application/json")

  val searchSequenceBuilder: HttpRequestBuilder = http("POST_search_sequence")
    .post("/moderation/sequences/search")
    .header("Content-Type", "application/json")

  val createSequenceBuilder: HttpRequestBuilder = http("POST_sequence")
    .post("/moderation/sequences")
    .header("Content-Type", "application/json")

}
