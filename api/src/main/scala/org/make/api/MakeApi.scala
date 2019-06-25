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
import org.make.api.crmTemplates.{
  AdminCrmTemplateApi,
  DefaultAdminCrmTemplatesApiComponent,
  DefaultCrmTemplatesServiceComponent,
  DefaultPersistentCrmTemplatesServiceComponent
}
import org.make.api.extensions._
import org.make.api.idea._
import org.make.api.operation._
import org.make.api.organisation._
import org.make.api.partner.{
  AdminPartnerApi,
  DefaultAdminPartnerApiComponent,
  DefaultPartnerServiceComponent,
  DefaultPersistentPartnerServiceComponent
}
import org.make.api.proposal._
import org.make.api.question._
import org.make.api.semantic.{DefaultSemanticComponent, DefaultSemanticConfigurationComponent}
import org.make.api.sequence.{SequenceApi, _}
import org.make.api.sessionhistory.{
  ConcurrentModification,
  DefaultSessionHistoryCoordinatorServiceComponent,
  SessionHistoryCoordinator,
  SessionHistoryCoordinatorComponent
}
import org.make.api.tag._
import org.make.api.tagtype.{
  DefaultModerationTagTypeApiComponent,
  DefaultPersistentTagTypeServiceComponent,
  DefaultTagTypeServiceComponent,
  ModerationTagTypeApi
}
import org.make.api.technical._
import org.make.api.technical.auth._
import org.make.api.technical.businessconfig.{ConfigurationsApi, DefaultConfigurationsApiComponent}
import org.make.api.technical.crm.{
  CrmApi,
  DefaultCrmApiComponent,
  DefaultCrmServiceComponent,
  DefaultSendMailPublisherServiceComponent
}
import org.make.api.technical.elasticsearch.{
  DefaultElasticSearchApiComponent,
  DefaultElasticsearchClientComponent,
  DefaultElasticsearchConfigurationComponent,
  DefaultIndexationComponent,
  ElasticSearchApi
}
import org.make.api.technical.healthcheck._
import org.make.api.technical.monitoring.DefaultMonitoringService
import org.make.api.technical.security.{DefaultSecurityApiComponent, DefaultSecurityConfigurationComponent, SecurityApi}
import org.make.api.technical.storage.{
  DefaultStorageConfigurationComponent,
  DefaultStorageServiceComponent,
  DefaultSwiftClientComponent
}
import org.make.api.technical.tracking.{DefaultTrackingApiComponent, TrackingApi}
import org.make.api.theme.{DefaultPersistentThemeServiceComponent, DefaultThemeServiceComponent}
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user._
import org.make.api.user.social.{DefaultFacebookApiComponent, DefaultGoogleApiComponent, DefaultSocialServiceComponent}
import org.make.api.userhistory.{
  DefaultUserHistoryCoordinatorServiceComponent,
  UserHistoryCoordinator,
  UserHistoryCoordinatorComponent
}
import org.make.api.views.{DefaultViewApiComponent, ViewApi}
import org.make.api.widget.{DefaultWidgetApiComponent, DefaultWidgetServiceComponent, WidgetApi}
import org.make.core.{ValidationError, ValidationFailedError}
import org.mdedetrich.akka.http.support.CirceHttpSupport
import org.mdedetrich.akka.stream.support.CirceStreamSupport.JsonParsingException
import scalaoauth2.provider.{OAuthGrantType, _}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait MakeApi
    extends ActorSystemComponent
    with AvroSerializers
    with BuildInfoRoutes
    with DefaultAdminCrmTemplatesApiComponent
    with DefaultAdminCurrentOperationApiComponent
    with DefaultAdminFeaturedOperationApiComponent
    with DefaultAdminIdeaMappingApiComponent
    with DefaultAdminPartnerApiComponent
    with DefaultAdminUserApiComponent
    with DefaultAuthenticationApiComponent
    with DefaultConfigurationsApiComponent
    with DefaultCrmApiComponent
    with DefaultCrmServiceComponent
    with DefaultCrmTemplatesServiceComponent
    with DefaultCurrentOperationServiceComponent
    with DefaultElasticSearchApiComponent
    with DefaultElasticsearchClientComponent
    with DefaultElasticsearchConfigurationComponent
    with DefaultEventBusServiceComponent
    with DefaultFacebookApiComponent
    with DefaultFeaturedOperationServiceComponent
    with DefaultGoogleApiComponent
    with DefaultViewApiComponent
    with DefaultHealthCheckApiComponent
    with DefaultHealthCheckServiceComponent
    with DefaultIdGeneratorComponent
    with DefaultIdeaMappingServiceComponent
    with DefaultIdeaSearchEngineComponent
    with DefaultIdeaServiceComponent
    with DefaultIndexationComponent
    with DefaultMailJetConfigurationComponent
    with DefaultMailJetTemplateConfigurationComponent
    with DefaultMakeDataHandlerComponent
    with DefaultMakeSettingsComponent
    with DefaultMigrationApiComponent
    with DefaultModerationIdeaApiComponent
    with DefaultModerationOperationApiComponent
    with DefaultModerationOperationOfQuestionApiComponent
    with DefaultModerationOrganisationApiComponent
    with DefaultModerationProposalApiComponent
    with DefaultModerationQuestionComponent
    with DefaultModerationSequenceApiComponent
    with DefaultModerationTagApiComponent
    with DefaultModerationTagTypeApiComponent
    with DefaultMonitoringService
    with DefaultOauthTokenGeneratorComponent
    with DefaultOperationApiComponent
    with DefaultOperationOfQuestionServiceComponent
    with DefaultOperationServiceComponent
    with DefaultOrganisationApiComponent
    with DefaultOrganisationSearchEngineComponent
    with DefaultOrganisationServiceComponent
    with DefaultPartnerServiceComponent
    with DefaultPersistentAuthCodeServiceComponent
    with DefaultPersistentClientServiceComponent
    with DefaultPersistentCrmTemplatesServiceComponent
    with DefaultPersistentCurrentOperationServiceComponent
    with DefaultPersistentFeaturedOperationServiceComponent
    with DefaultPersistentIdeaMappingServiceComponent
    with DefaultPersistentIdeaServiceComponent
    with DefaultPersistentOperationOfQuestionServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentPartnerServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentSequenceConfigurationServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentTagTypeServiceComponent
    with DefaultPersistentThemeServiceComponent
    with DefaultPersistentTokenServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentUserToAnonymizeServiceComponent
    with DefaultProposalApiComponent
    with DefaultProposalCoordinatorServiceComponent
    with DefaultProposalIndexerServiceComponent
    with DefaultProposalSearchEngineComponent
    with DefaultProposalServiceComponent
    with DefaultQuestionApiComponent
    with DefaultQuestionService
    with DefaultReadJournalComponent
    with DefaultSecurityApiComponent
    with DefaultSecurityConfigurationComponent
    with DefaultSelectionAlgorithmComponent
    with DefaultSemanticComponent
    with DefaultSemanticConfigurationComponent
    with DefaultSendMailPublisherServiceComponent
    with DefaultSequenceApiComponent
    with DefaultSequenceConfigurationComponent
    with DefaultSequenceServiceComponent
    with DefaultSessionHistoryCoordinatorServiceComponent
    with DefaultSocialServiceComponent
    with DefaultStorageConfigurationComponent
    with DefaultStorageServiceComponent
    with DefaultSwiftClientComponent
    with DefaultTagApiComponent
    with DefaultTagServiceComponent
    with DefaultTagTypeServiceComponent
    with DefaultThemeServiceComponent
    with DefaultTokenGeneratorComponent
    with DefaultTrackingApiComponent
    with DefaultUserApiComponent
    with DefaultUserHistoryCoordinatorServiceComponent
    with DefaultUserServiceComponent
    with DefaultUserTokenGeneratorComponent
    with DefaultWidgetApiComponent
    with DefaultWidgetServiceComponent
    with HealthCheckComponent
    with MakeAuthentication
    with MakeDBExecutionContextComponent
    with ProposalCoordinatorComponent
    with SequenceConfigurationActorComponent
    with SessionHistoryCoordinatorComponent
    with StrictLogging
    with UserHistoryCoordinatorComponent {

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

    private val password: Password = new Password {
      override val clientCredentialRequired = false
    }

    override val handlers: Map[String, GrantHandler] =
      Map[String, GrantHandler](
        OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode,
        OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials,
        OAuthGrantType.PASSWORD -> password,
        OAuthGrantType.REFRESH_TOKEN -> new RefreshToken
      )
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
      classOf[AdminCrmTemplateApi],
      classOf[AuthenticationApi],
      classOf[UserApi],
      classOf[TagApi],
      classOf[ModerationTagApi],
      classOf[ModerationTagTypeApi],
      classOf[ProposalApi],
      classOf[OperationApi],
      classOf[ConfigurationsApi],
      classOf[SequenceApi],
      classOf[ModerationSequenceApi],
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
      classOf[OrganisationApi],
      classOf[QuestionApi],
      classOf[WidgetApi],
      classOf[AdminUserApi],
      classOf[AdminIdeaMappingApi],
      classOf[SecurityApi],
      classOf[AdminPartnerApi],
      classOf[AdminFeaturedOperationApi],
      classOf[AdminCurrentOperationApi],
      classOf[ViewApi],
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
    (documentation ~
      swagger ~
      optionsCors ~
      optionsAuthorized ~
      buildRoutes ~

      adminCrmTemplateApi.routes ~
      adminCurrentOperationApi.routes ~
      adminFeaturedOperationApi.routes ~
      adminIdeaMappingApi.routes ~
      adminUserApi.routes ~
      authenticationApi.routes ~
      configurationsApi.routes ~
      crmApi.routes ~
      elasticSearchApi.routes ~
      healthCheckApi.routes ~
      viewApi.routes ~
      migrationApi.routes ~
      moderationIdeaApi.routes ~
      moderationOperationApi.routes ~
      moderationOperationOfQuestionApi.routes ~
      moderationOrganisationApi.routes ~
      moderationPartnerApi.routes ~
      moderationProposalApi.routes ~
      moderationQuestionApi.routes ~
      moderationSequenceApi.routes ~
      moderationTagApi.routes ~
      moderationTagTypeApi.routes ~
      operationApi.routes ~
      organisationApi.routes ~
      proposalApi.routes ~
      questionApi.routes ~
      securityApi.routes ~
      sequenceApi.routes ~
      tagApi.routes ~
      trackingApi.routes ~
      userApi.routes ~
      widgetApi.routes)
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
    case ConcurrentModification(message) =>
      complete(StatusCodes.Conflict -> message)
    case e: ClientAccessUnauthorizedException =>
      complete(StatusCodes.Forbidden -> ValidationError("aurhentication", Some(e.getMessage)))
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
