package org.make.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{
  `Access-Control-Allow-Headers`,
  `Access-Control-Allow-Methods`,
  `Access-Control-Allow-Origin`,
  `Access-Control-Request-Headers`
}
import akka.http.scaladsl.server._
import akka.util.Timeout
import buildinfo.BuildInfo
import com.typesafe.scalalogging.StrictLogging
import de.knutwalker.akka.http.support.CirceHttpSupport
import de.knutwalker.akka.stream.support.CirceStreamSupport.JsonParsingException
import io.circe.CursorOp.DownField
import io.circe.generic.auto._
import io.circe.syntax._
import kamon.trace.Tracer
import org.make.api.extensions._
import org.make.api.proposal._
import org.make.api.tag.{DefaultPersistentTagServiceComponent, DefaultTagServiceComponent, TagApi}
import org.make.api.technical._
import org.make.api.technical.auth._
import org.make.api.technical.businessconfig.BusinessConfigApi
import org.make.api.technical.elasticsearch.{ElasticsearchConfiguration, ElasticsearchConfigurationComponent}
import org.make.api.technical.mailjet.MailJetApi
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.social.{DefaultFacebookApiComponent, DefaultGoogleApiComponent, DefaultSocialServiceComponent}
import org.make.api.user.{DefaultPersistentUserServiceComponent, DefaultUserServiceComponent, UserApi, UserSupervisor}
import org.make.api.userhistory.{
  DefaultUserHistoryServiceComponent,
  UserHistoryCoordinator,
  UserHistoryCoordinatorComponent
}
import org.make.api.vote._
import org.make.core.{ValidationError, ValidationFailedError}

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scalaoauth2.provider._

trait MakeApi
    extends DefaultIdGeneratorComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentClientServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentTokenServiceComponent
    with DefaultSocialServiceComponent
    with DefaultGoogleApiComponent
    with DefaultFacebookApiComponent
    with DefaultUserServiceComponent
    with DefaultTagServiceComponent
    with DefaultProposalServiceComponent
    with DefaultVoteServiceComponent
    with DefaultMakeDataHandlerComponent
    with DefaultMakeSettingsComponent
    with DefaultEventBusServiceComponent
    with DefaultTokenGeneratorComponent
    with DefaultUserTokenGeneratorComponent
    with DefaultOauthTokenGeneratorComponent
    with DefaultProposalSearchEngineComponent
    with DefaultUserHistoryServiceComponent
    with DefaultProposalCoordinatorServiceComponent
    with ElasticsearchConfigurationComponent
    with ProposalCoordinatorComponent
    with UserHistoryCoordinatorComponent
    with VoteCoordinatorComponent
    with ProposalApi
    with VoteApi
    with MailJetApi
    with AuthenticationApi
    with BusinessConfigApi
    with UserApi
    with TagApi
    with BuildInfoRoutes
    with MailJetConfigurationComponent
    with StrictLogging
    with AvroSerializers
    with MakeAuthentication
    with ActorSystemComponent {

  override lazy val mailJetConfiguration: MailJetConfiguration = MailJetConfiguration(actorSystem)

  override lazy val elasticsearchConfiguration: ElasticsearchConfiguration = ElasticsearchConfiguration(actorSystem)

  override lazy val proposalCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / ProposalSupervisor.name / ProposalCoordinator.name)
      .resolveOne()(Timeout(2.seconds)),
    atMost = 2.seconds
  )

  override lazy val userHistoryCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / UserSupervisor.name / UserHistoryCoordinator.name)
      .resolveOne()(Timeout(2.seconds)),
    atMost = 2.seconds
  )

  override lazy val voteCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / VoteSupervisor.name / VoteCoordinator.name)
      .resolveOne()(Timeout(2.seconds)),
    atMost = 2.seconds
  )

  override lazy val readExecutionContext: EC = actorSystem.extension(DatabaseConfiguration).readThreadPool
  override lazy val writeExecutionContext: EC = actorSystem.extension(DatabaseConfiguration).writeThreadPool

  override lazy val tokenEndpoint: TokenEndpoint = new TokenEndpoint {
    override val handlers = Map(OAuthGrantType.PASSWORD -> new Password)
  }

  private lazy val swagger: Route =
    path("swagger") {
      parameters('url.?) {
        case None => redirect(Uri("/swagger?url=/api-docs/swagger.json"), StatusCodes.PermanentRedirect)
        case _    => getFromResource(s"META-INF/resources/webjars/swagger-ui/${BuildInfo.swaggerUiVersion}/index.html")
      }
    } ~ getFromResourceDirectory(s"META-INF/resources/webjars/swagger-ui/${BuildInfo.swaggerUiVersion}")

  private lazy val apiClasses: Set[Class[_]] =
    Set(classOf[AuthenticationApi], classOf[UserApi], classOf[TagApi], classOf[ProposalApi], classOf[BusinessConfigApi])

  private lazy val optionsCors: Route = options {
    MakeApi.corsHeaders() {
      complete(StatusCodes.OK)
    }
  }
  private lazy val optionsAuthorized: Route =
    options {
      MakeApi.corsHeaders() {
        complete(StatusCodes.OK)
      }
    }

  lazy val makeRoutes: Route =
    MakeApi.makeDefaultHeadersAndHandlers() {
      new MakeDocumentation(actorSystem, apiClasses, makeSettings.Http.ssl).routes ~
        swagger ~
        optionsCors ~
        userRoutes ~
        tagRoutes ~
        proposalRoutes ~
        optionsAuthorized ~
        buildRoutes ~
        mailJetRoutes ~
        authenticationRoutes ~
        businessConfigRoutes
    }
}

object MakeApi extends StrictLogging with Directives with CirceHttpSupport {
  def defaultError(id: String): String =
    s"""
      |{
      |  "error": "an error occurred, it has been logged with id $id"
      |}
    """.stripMargin

  private def getFromTrace(key: String, default: String = "<unknown>"): String =
    Tracer.currentContext.tags.getOrElse(key, default)

  def requestIdFromTrace: String = getFromTrace("id")
  def startTimeFromTrace: Long = getFromTrace("start-time", "0").toLong
  def routeNameFromTrace: String = getFromTrace("route-name")
  def externalIdFromTrace: String = getFromTrace("external-id")
  def routeIdFromTrace: String = Tracer.currentContext.name

  def defaultHeadersFromTrace: immutable.Seq[HttpHeader] = immutable.Seq(
    RequestIdHeader(requestIdFromTrace),
    RequestTimeHeader(startTimeFromTrace),
    RouteNameHeader(routeNameFromTrace),
    ExternalIdHeader(externalIdFromTrace)
  )

  def defaultCorsHeaders: immutable.Seq[HttpHeader] = immutable.Seq(
    `Access-Control-Allow-Methods`(
      HttpMethods.POST,
      HttpMethods.GET,
      HttpMethods.PUT,
      HttpMethods.PATCH,
      HttpMethods.DELETE
    ),
    `Access-Control-Allow-Origin`.*
  )

  def makeDefaultHeadersAndHandlers(): Directive0 =
    mapInnerRoute { route =>
      respondWithDefaultHeaders(MakeApi.defaultHeadersFromTrace ++ MakeApi.defaultCorsHeaders) {
        handleExceptions(MakeApi.exceptionHandler) {
          handleRejections(MakeApi.rejectionHandler) {
            route
          }
        }
      }
    }

  def corsHeaders(): Directive0 =
    mapInnerRoute { route =>
      respondWithDefaultHeaders(MakeApi.defaultCorsHeaders) {
        optionalHeaderValueByType[`Access-Control-Request-Headers`]() {
          case Some(requestHeader) =>
            respondWithDefaultHeaders(`Access-Control-Allow-Headers`(requestHeader.value)) { route }
          case None => route
        }
      }
    }

  val exceptionHandler = ExceptionHandler {
    case e: EmailAlreadyRegisteredException =>
      complete(StatusCodes.BadRequest -> Seq(ValidationError("email", Option(e.getMessage))))
    case ValidationFailedError(messages) =>
      complete(
        HttpResponse(
          status = StatusCodes.BadRequest,
          entity = HttpEntity(ContentTypes.`application/json`, messages.asJson.toString)
        )
      )
    case e =>
      logger.error(s"Error on request ${MakeApi.routeIdFromTrace} with id ${MakeApi.requestIdFromTrace}", e)
      complete(
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(ContentTypes.`application/json`, MakeApi.defaultError(MakeApi.requestIdFromTrace))
        )
      )
  }

  val rejectionHandler: RejectionHandler = RejectionHandler
    .newBuilder()
    .handle {
      case MalformedRequestContentRejection(_, ValidationFailedError(messages)) =>
        complete(StatusCodes.BadRequest -> messages)
      case MalformedRequestContentRejection(_, JsonParsingException(failure, _)) =>
        val errors = failure.history.flatMap {
          case DownField(f) => Seq(ValidationError(f, Option(failure.message)))
          case _            => Nil
        }
        complete(StatusCodes.BadRequest -> errors)
      case MalformedRequestContentRejection(_, e) =>
        complete(StatusCodes.BadRequest -> Seq(ValidationError("unknown", Option(e.getMessage))))
    }
    .result()
    .withFallback(RejectionHandler.default)
    .mapRejectionResponse { res =>
      //TODO: change Content-Type to `application/json`
      res
    }

}

trait ActorSystemComponent {
  def actorSystem: ActorSystem
}
