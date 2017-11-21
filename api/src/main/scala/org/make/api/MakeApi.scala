package org.make.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
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
import org.make.api.sequence._
import org.make.api.sequence.SequenceApi
import org.make.api.tag.{DefaultPersistentTagServiceComponent, DefaultTagServiceComponent, TagApi}
import org.make.api.technical.elasticsearch.{
  DefaultElasticSearchComponent,
  ElasticSearchApi,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.api.technical._
import org.make.api.technical.auth._
import org.make.api.technical.businessconfig.ConfigurationsApi
import org.make.api.technical.mailjet.MailJetApi
import org.make.api.theme.{DefaultPersistentThemeServiceComponent, DefaultThemeServiceComponent}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.social.{DefaultFacebookApiComponent, DefaultGoogleApiComponent, DefaultSocialServiceComponent}
import org.make.api.user.{DefaultPersistentUserServiceComponent, DefaultUserServiceComponent, UserApi}
import org.make.api.userhistory.{
  DefaultUserHistoryCoordinatorServiceComponent,
  UserHistoryCoordinator,
  UserHistoryCoordinatorComponent
}
import org.make.api.sessionhistory.{
  DefaultSessionHistoryCoordinatorServiceComponent,
  SessionHistoryCoordinator,
  SessionHistoryCoordinatorComponent
}
import org.make.core.{ValidationError, ValidationFailedError}

import scala.concurrent.Await
import scala.concurrent.duration._
import scalaoauth2.provider._

trait MakeApi
    extends DefaultIdGeneratorComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentClientServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentThemeServiceComponent
    with DefaultPersistentTokenServiceComponent
    with DefaultSocialServiceComponent
    with DefaultGoogleApiComponent
    with DefaultFacebookApiComponent
    with DefaultUserServiceComponent
    with DefaultTagServiceComponent
    with DefaultThemeServiceComponent
    with DefaultProposalServiceComponent
    with DefaultSequenceServiceComponent
    with DuplicateDetectorConfigurationComponent
    with DefaultMakeDataHandlerComponent
    with DefaultMakeSettingsComponent
    with DefaultEventBusServiceComponent
    with DefaultTokenGeneratorComponent
    with DefaultUserTokenGeneratorComponent
    with DefaultOauthTokenGeneratorComponent
    with DefaultProposalSearchEngineComponent
    with DefaultUserHistoryCoordinatorServiceComponent
    with DefaultSessionHistoryCoordinatorServiceComponent
    with DefaultProposalCoordinatorServiceComponent
    with DefaultSequenceCoordinatorServiceComponent
    with DefaultSequenceSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with ProposalCoordinatorComponent
    with SequenceCoordinatorComponent
    with UserHistoryCoordinatorComponent
    with SessionHistoryCoordinatorComponent
    with DefaultElasticSearchComponent
    with ElasticSearchApi
    with ProposalApi
    with ModerationProposalApi
    with SequenceApi
    with MailJetApi
    with AuthenticationApi
    with ConfigurationsApi
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

  override lazy val duplicateDetectorConfiguration: DuplicateDetectorConfiguration = DuplicateDetectorConfiguration(
    actorSystem
  )

  override lazy val proposalCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / ProposalSupervisor.name / ProposalCoordinator.name)
      .resolveOne()(Timeout(2.seconds)),
    atMost = 2.seconds
  )

  override lazy val sequenceCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / SequenceSupervisor.name / SequenceCoordinator.name)
      .resolveOne()(Timeout(2.seconds)),
    atMost = 2.seconds
  )

  override lazy val userHistoryCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / UserHistoryCoordinator.name)
      .resolveOne()(Timeout(2.seconds)),
    atMost = 2.seconds
  )

  override lazy val sessionHistoryCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / SessionHistoryCoordinator.name)
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
    Set(
      classOf[AuthenticationApi],
      classOf[UserApi],
      classOf[TagApi],
      classOf[ProposalApi],
      classOf[ModerationProposalApi],
      classOf[ConfigurationsApi],
      classOf[SequenceApi],
      classOf[ElasticSearchApi]
    )

  private lazy val optionsCors: Route = options {
    corsHeaders() {
      complete(StatusCodes.OK)
    }
  }
  private lazy val optionsAuthorized: Route =
    options {
      corsHeaders() {
        complete(StatusCodes.OK)
      }
    }

  lazy val makeRoutes: Route =
    makeDefaultHeadersAndHandlers() {
      new MakeDocumentation(actorSystem, apiClasses, makeSettings.Http.ssl).routes ~
        swagger ~
        optionsCors ~
        elasticsearchRoutes ~
        userRoutes ~
        tagRoutes ~
        proposalRoutes ~
        moderationProposalRoutes ~
        sequenceRoutes ~
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
  def routeIdFromTrace: String = Tracer.currentContext.name

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
