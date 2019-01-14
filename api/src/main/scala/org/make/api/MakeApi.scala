/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.util.Timeout
import buildinfo.BuildInfo
import com.typesafe.scalalogging.StrictLogging
import io.circe.CursorOp.DownField
import io.circe.syntax._
import org.make.api.extensions._
import org.make.api.idea._
import org.make.api.operation._
import org.make.api.organisation._
import org.make.api.proposal._
import org.make.api.question._
import org.make.api.semantic.{DefaultSemanticComponent, DefaultSemanticConfigurationComponent}
import org.make.api.sequence.{SequenceApi, _}
import org.make.api.sessionhistory.{
  DefaultSessionHistoryCoordinatorServiceComponent,
  SessionHistoryCoordinator,
  SessionHistoryCoordinatorComponent
}
import org.make.api.tag._
import org.make.api.tagtype.{
  DefaultPersistentTagTypeServiceComponent,
  DefaultTagTypeServiceComponent,
  ModerationTagTypeApi
}
import org.make.api.technical._
import org.make.api.technical.auth._
import org.make.api.technical.businessconfig.ConfigurationsApi
import org.make.api.technical.crm.{CrmApi, DefaultCrmServiceComponent}
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  DefaultElasticsearchConfigurationComponent,
  DefaultIndexationComponent,
  ElasticSearchApi
}
import org.make.api.technical.healthcheck.{
  DefaultHealthCheckServiceComponent,
  HealthCheckApi,
  HealthCheckComponent,
  HealthCheckSupervisor
}
import org.make.api.technical.monitoring.DefaultMonitoringService
import org.make.api.technical.storage.{
  DefaultStorageConfigurationComponent,
  DefaultStorageServiceComponent,
  DefaultSwiftClientComponent
}
import org.make.api.technical.tracking.{DefaultTrackingApiComponent, TrackingApi}
import org.make.api.theme.{DefaultPersistentThemeServiceComponent, DefaultThemeServiceComponent}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.social.{DefaultFacebookApiComponent, DefaultGoogleApiComponent, DefaultSocialServiceComponent}
import org.make.api.user._
import org.make.api.userhistory.{
  DefaultUserHistoryCoordinatorServiceComponent,
  UserHistoryCoordinator,
  UserHistoryCoordinatorComponent
}
import org.make.api.widget.{DefaultWidgetApiComponent, DefaultWidgetServiceComponent, WidgetApi}
import org.make.core.{ValidationError, ValidationFailedError}
import org.mdedetrich.akka.http.support.CirceHttpSupport
import org.mdedetrich.akka.stream.support.CirceStreamSupport.JsonParsingException
import scalaoauth2.provider._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait MakeApi
    extends DefaultIdGeneratorComponent
    with DefaultPersistentClientServiceComponent
    with DefaultPersistentIdeaServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentOperationOfQuestionServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentSequenceConfigurationServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentThemeServiceComponent
    with DefaultPersistentTokenServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultUserServiceComponent
    with DefaultAdminUserApiComponent
    with DefaultTagServiceComponent
    with DefaultTagTypeServiceComponent
    with DefaultIdeaServiceComponent
    with DefaultThemeServiceComponent
    with DefaultProposalServiceComponent
    with DefaultSequenceServiceComponent
    with DefaultSocialServiceComponent
    with DefaultGoogleApiComponent
    with DefaultFacebookApiComponent
    with DefaultSequenceConfigurationComponent
    with DefaultQuestionService
    with DefaultOperationOfQuestionServiceComponent
    with SequenceConfigurationActorComponent
    with DefaultSelectionAlgorithmComponent
    with DefaultSemanticComponent
    with DefaultSemanticConfigurationComponent
    with DefaultMakeDataHandlerComponent
    with DefaultMakeSettingsComponent
    with DefaultEventBusServiceComponent
    with DefaultTokenGeneratorComponent
    with DefaultUserTokenGeneratorComponent
    with DefaultOauthTokenGeneratorComponent
    with DefaultProposalSearchEngineComponent
    with DefaultIdeaSearchEngineComponent
    with DefaultOrganisationSearchEngineComponent
    with DefaultUserHistoryCoordinatorServiceComponent
    with DefaultSessionHistoryCoordinatorServiceComponent
    with DefaultProposalCoordinatorServiceComponent
    with DefaultProposalIndexerServiceComponent
    with DefaultOperationServiceComponent
    with DefaultReadJournalComponent
    with DefaultHealthCheckServiceComponent
    with DefaultCrmServiceComponent
    with DefaultOrganisationServiceComponent
    with DefaultElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with DefaultSwiftClientComponent
    with DefaultStorageServiceComponent
    with DefaultStorageConfigurationComponent
    with DefaultQuestionApiComponent
    with DefaultModerationQuestionComponent
    with DefaultMonitoringService
    with DefaultModerationOperationOfQuestionApiComponent
    with ProposalCoordinatorComponent
    with UserHistoryCoordinatorComponent
    with SessionHistoryCoordinatorComponent
    with DefaultIndexationComponent
    with DefaultPersistentTagTypeServiceComponent
    with HealthCheckComponent
    with MakeDBExecutionContextComponent
    with ElasticSearchApi
    with DefaultOperationApiComponent
    with ProposalApi
    with SequenceApi
    with CrmApi
    with AuthenticationApi
    with ConfigurationsApi
    with DefaultUserApiComponent
    with TagApi
    with DefaultModerationTagApiComponent
    with ModerationTagTypeApi
    with DefaultModerationIdeaApiComponent
    with DefaultTrackingApiComponent
    with DefaultMigrationApiComponent
    with HealthCheckApi
    with DefaultModerationOperationApiComponent
    with DefaultModerationOrganisationApiComponent
    with OrganisationApi
    with DefaultModerationProposalApiComponent
    with DefaultWidgetApiComponent
    with DefaultWidgetServiceComponent
    with BuildInfoRoutes
    with DefaultMailJetConfigurationComponent
    with StrictLogging
    with AvroSerializers
    with MakeAuthentication
    with ActorSystemComponent {

  override lazy val proposalCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / ProposalSupervisor.name / ProposalCoordinator.name)
      .resolveOne()(Timeout(5.seconds)),
    atMost = 5.seconds
  )

  override lazy val userHistoryCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / UserHistoryCoordinator.name)
      .resolveOne()(Timeout(5.seconds)),
    atMost = 5.seconds
  )

  override lazy val sessionHistoryCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / SessionHistoryCoordinator.name)
      .resolveOne()(Timeout(5.seconds)),
    atMost = 5.seconds
  )

  override lazy val sequenceConfigurationActor: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / SequenceConfigurationActor.name)
      .resolveOne()(Timeout(5.seconds)),
    atMost = 5.seconds
  )

  override lazy val healthCheckSupervisor: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / HealthCheckSupervisor.name)
      .resolveOne()(Timeout(5.seconds)),
    atMost = 5.seconds
  )

  override lazy val readExecutionContext: EC = actorSystem.extension(DatabaseConfiguration).readThreadPool
  override lazy val writeExecutionContext: EC = actorSystem.extension(DatabaseConfiguration).writeThreadPool

  override lazy val tokenEndpoint: TokenEndpoint = new TokenEndpoint {
    override val handlers: Map[String, Password] = Map(OAuthGrantType.PASSWORD -> new Password)
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
      classOf[ModerationTagApi],
      classOf[ModerationTagTypeApi],
      classOf[ProposalApi],
      classOf[OperationApi],
      classOf[ConfigurationsApi],
      classOf[SequenceApi],
      classOf[ModerationIdeaApi],
      classOf[ElasticSearchApi],
      classOf[TrackingApi],
      classOf[MigrationApi],
      classOf[HealthCheckApi],
      classOf[CrmApi],
      classOf[ModerationOperationApi],
      classOf[ModerationOperationOfQuestionApi],
      classOf[ModerationOrganisationApi],
      classOf[ModerationProposalApi],
      classOf[ModerationQuestionApi],
      classOf[ModerationTagApi],
      classOf[OrganisationApi],
      classOf[QuestionApi],
      classOf[WidgetApi],
      classOf[AdminUserApi]
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

  private lazy val documentation = new MakeDocumentation(apiClasses, makeSettings.Http.ssl).routes

  lazy val makeRoutes: Route =
    documentation ~
      swagger ~
      optionsCors ~
      elasticsearchRoutes ~
      userApi.routes ~
      adminUserApi.routes ~
      tagRoutes ~
      moderationTagApi.routes ~
      moderationTagTypeRoutes ~
      proposalRoutes ~
      moderationProposalApi.routes ~
      sequenceRoutes ~
      optionsAuthorized ~
      buildRoutes ~
      crmRoutes ~
      authenticationRoutes ~
      businessConfigRoutes ~
      moderationIdeaApi.routes ~
      operationApi.routes ~
      moderationOperationApi.routes ~
      trackingApi.routes ~
      migrationApi.routes ~
      healthCheckRoutes ~
      moderationOrganisationApi.routes ~
      organisationRoutes ~
      widgetApi.routes ~
      questionApi.routes ~
      moderationQuestionApi.routes ~
      moderationOperationOfQuestionApi.routes
}

object MakeApi extends StrictLogging with Directives with CirceHttpSupport {

  def defaultError(id: String): String =
    s"""
      |{
      |  "error": "an error occurred, it has been logged with id $id"
      |}
    """.stripMargin

  def exceptionHandler(routeName: String, requestId: String): ExceptionHandler = ExceptionHandler {
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
      logger.error(s"Error on request $routeName with id $requestId", e)
      complete(
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(ContentTypes.`application/json`, MakeApi.defaultError(requestId))
        )
      )
  }

  val rejectionHandler: RejectionHandler = RejectionHandler
    .newBuilder()
    .handle {
      case MalformedRequestContentRejection(_, ValidationFailedError(messages)) =>
        complete(StatusCodes.BadRequest -> messages)
      case MalformedRequestContentRejection(message, JsonParsingException(failure, _)) =>
        val errors = failure.history.flatMap {
          case DownField(f) => Seq(ValidationError(f, Option(message)))
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
