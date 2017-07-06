package org.make.api

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.server.{ExceptionHandler, Route, _}
import akka.util.Timeout
import buildinfo.BuildInfo
import com.typesafe.scalalogging.StrictLogging
import de.knutwalker.akka.http.support.CirceHttpSupport
import io.circe.generic.auto._
import io.circe.syntax._
import kamon.trace.Tracer
import org.make.api.extensions.{DatabaseConfiguration, MailJetConfiguration, MailJetConfigurationComponent}
import org.make.api.proposition._
import org.make.api.technical.auth._
import org.make.api.technical.mailjet.MailJetApi
import org.make.api.technical.{AvroSerializers, BuildInfoRoutes, IdGeneratorComponent, MakeDocumentation, _}
import org.make.api.user.UserExceptions.EmailAlreadyRegistredException
import org.make.api.user.{PersistentUserServiceComponent, UserApi, UserServiceComponent}
import org.make.api.vote.{VoteApi, VoteCoordinator, VoteServiceComponent, VoteSupervisor}
import org.make.core.{ValidationError, ValidationFailedError}

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.runtime.{universe => ru}
import scalaoauth2.provider._

trait MakeApi
    extends UserServiceComponent
    with IdGeneratorComponent
    with PersistentUserServiceComponent
    with UserApi
    with PropositionServiceComponent
    with PropositionApi
    with VoteServiceComponent
    with VoteApi
    with BuildInfoRoutes
    with AvroSerializers
    with MakeDataHandlerComponent
    with MailJetApi
    with MailJetConfigurationComponent
    with EventBusServiceComponent
    with DefaultTokenGeneratorComponent
    with DefaultUserTokenGeneratorComponent
    with DefaultOauthTokenGeneratorComponent
    with PersistentTokenServiceComponent
    with StrictLogging {

  def actorSystem: ActorSystem

  override def eventBusService: EventBusService = new EventBusService(actorSystem)

  override lazy val mailJetConfiguration: MailJetConfiguration = MailJetConfiguration(actorSystem)
  override lazy val idGenerator: IdGenerator = new UUIDIdGenerator
  override lazy val userService: UserService = new UserService()
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
  override lazy val persistentUserService: PersistentUserService =
    new PersistentUserService()
  override lazy val oauth2DataHandler: MakeDataHandler =
    new MakeDataHandler()(ECGlobal)
  override lazy val persistentTokenService: PersistentTokenService =
    new PersistentTokenService()
  override lazy val persistentClientService: PersistentClientService =
    new PersistentClientService()
  //  override lazy val tokenService: TokenService = new TokenService()
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
      logger.error(s"Error on request ${MakeApi.routeId} with id ${MakeApi.requestId}", e)
      complete(
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(ContentTypes.`application/json`, MakeApi.defaultError(MakeApi.requestId))
        )
      )

  }

  private lazy val swagger: Route =
    path("swagger") {
      parameters('url.?) {
        case None => redirect(Uri("/swagger?url=/api-docs/swagger.json"), StatusCodes.PermanentRedirect)
        case _    => getFromResource(s"META-INF/resources/webjars/swagger-ui/${BuildInfo.swaggerUiVersion}/index.html")
      }
    } ~ getFromResourceDirectory(s"META-INF/resources/webjars/swagger-ui/${BuildInfo.swaggerUiVersion}")

  private lazy val login: Route = path("login.html") {
    getFromResource("auth/login.html")
  }

  private lazy val apiTypes: Seq[ru.Type] =
    Seq(ru.typeOf[UserApi], ru.typeOf[PropositionApi])

  lazy val makeRoutes: Route = handleExceptions(MakeApi.exceptionHandler)(
    handleRejections(MakeApi.rejectionHandler)(
      new MakeDocumentation(actorSystem, apiTypes).routes ~
        swagger ~
        login ~
        userRoutes ~
        propositionRoutes ~
//    voteRoutes ~
        accessTokenRoute ~
        buildRoutes ~
        mailJetRoutes
    )
  )
}

object MakeApi extends StrictLogging with Directives with CirceHttpSupport {
  def defaultError(id: String): String =
    s"""
      |{
      |  "error": "an error occurred, it has been logged with id $id"
      |}
    """.stripMargin

  def requestId: String = {
    Tracer.currentContext.tags.getOrElse("id", "<unknown>")
  }

  def routeId: String = {
    Tracer.currentContext.name
  }

  val exceptionHandler = ExceptionHandler {
    case e: EmailAlreadyRegistredException =>
      complete(StatusCodes.BadRequest -> Seq(ValidationError("email", e.getMessage)))
    case e =>
      logger.error(s"Error on request ${MakeApi.routeId} with id ${MakeApi.requestId}", e)
      complete(
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(ContentTypes.`application/json`, MakeApi.defaultError(MakeApi.requestId))
        )
      )
  }

  val rejectionHandler: RejectionHandler = RejectionHandler
    .newBuilder()
    .handle {
      case MalformedRequestContentRejection(_, ValidationFailedError(messages)) =>
        complete(
          HttpResponse(
            status = StatusCodes.BadRequest,
            entity = HttpEntity(ContentTypes.`application/json`, messages.asJson.toString),
            headers = immutable.Seq[HttpHeader](`Content-Type`(ContentTypes.`application/json`))
          )
        )
    }
    .result()
    .withFallback(RejectionHandler.default)

}
