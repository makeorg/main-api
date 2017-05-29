package org.make.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.syntax._
import org.make.api.citizen.{CitizenApi, CitizenServiceComponent, PersistentCitizenServiceComponent}
import org.make.api.extensions.DatabaseConfiguration
import org.make.api.proposition._
import org.make.api.technical.auth.{MakeDataHandlerComponent, TokenServiceComponent}
import org.make.api.technical.{AvroSerializers, BuildInfoRoutes, IdGeneratorComponent, MakeDocumentation}
import org.make.api.vote.{VoteApi, VoteCoordinator, VoteServiceComponent, VoteSupervisor}
import org.make.core.ValidationFailedError

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.runtime.{universe => ru}
import scalaoauth2.provider._

trait MakeApi
    extends CitizenServiceComponent
    with IdGeneratorComponent
    with PersistentCitizenServiceComponent
    with CitizenApi
    with PropositionServiceComponent
    with PropositionApi
    with VoteServiceComponent
    with VoteApi
    with BuildInfoRoutes
    with AvroSerializers
    with MakeDataHandlerComponent
    with TokenServiceComponent
    with StrictLogging {

  def actorSystem: ActorSystem

  override lazy val idGenerator: IdGenerator = new UUIDIdGenerator
  override lazy val citizenService: CitizenService = new CitizenService()
  override lazy val propositionService: PropositionService =
    new PropositionService(
      Await.result(
        actorSystem
          .actorSelection(actorSystem / MakeGuardian.name / PropositionSupervisor.name / PropositionCoordinator.name)
          .resolveOne()(Timeout(2.seconds)),
        atMost = 2.seconds
      )
    )
  override lazy val voteService: VoteService = new VoteService(
    Await.result(
      actorSystem
        .actorSelection(actorSystem / MakeGuardian.name / VoteSupervisor.name / VoteCoordinator.name)
        .resolveOne()(Timeout(2.seconds)),
      atMost = 2.seconds
    )
  )
  override lazy val persistentCitizenService: PersistentCitizenService =
    new PersistentCitizenService()
  override lazy val oauth2DataHandler: MakeDataHandler =
    new MakeDataHandler()(ECGlobal)
  override lazy val tokenService: TokenService = new TokenService()
  override lazy val readExecutionContext: EC = actorSystem.extension(DatabaseConfiguration).readThreadPool
  override lazy val writeExecutionContext: EC = actorSystem.extension(DatabaseConfiguration).writeThreadPool
  override lazy val tokenEndpoint: TokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.IMPLICIT -> new Implicit,
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials,
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode,
      OAuthGrantType.PASSWORD -> new Password,
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken
    )
  }

  val exceptionHandler = ExceptionHandler {
    case ValidationFailedError(messages) =>
      complete(
        HttpResponse(
          status = StatusCodes.BadRequest,
          entity = HttpEntity(ContentTypes.`application/json`, messages.asJson.toString)
        )
      )
    case e =>
      logger.error("", e)
      complete(
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(ContentTypes.`application/json`, MakeApi.defaultError)
        )
      )

  }

  private lazy val swagger: Route =
    path("swagger") {
      getFromResource("META-INF/resources/webjars/swagger-ui/2.2.8/index.html")
    } ~ getFromResourceDirectory("META-INF/resources/webjars/swagger-ui/2.2.8")

  private lazy val login: Route = path("login.html") {
    getFromResource("auth/login.html")
  }

  private lazy val apiTypes: Seq[ru.Type] =
    Seq(ru.typeOf[CitizenApi], ru.typeOf[PropositionApi])

  lazy val makeRoutes: Route = handleExceptions(exceptionHandler)(
    new MakeDocumentation(actorSystem, apiTypes).routes ~
      swagger ~
      login ~
      citizenRoutes ~
      propositionRoutes ~
//    voteRoutes ~
      accessTokenRoute ~
      buildRoutes
  )
}

object MakeApi {
  val defaultError: String =
    """
      |{
      |  "error": "an error occurred, please try again later."
      |}
    """.stripMargin
}
