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
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport.DecodingFailures
import enumeratum.NoSuchMember
import io.circe.CursorOp.DownField
import io.circe.syntax._
import org.make.api.article.DefaultArticleSearchEngineComponent
import org.make.api.crmTemplates.{AdminCrmTemplateApi, DefaultAdminCrmTemplatesApiComponent, DefaultCrmTemplatesServiceComponent, DefaultPersistentCrmTemplatesServiceComponent}
import org.make.api.extensions._
import org.make.api.feature._
import org.make.api.idea._
import org.make.api.idea.topIdeaComments.{DefaultPersistentTopIdeaCommentServiceComponent, DefaultTopIdeaCommentServiceComponent}
import org.make.api.operation._
import org.make.api.organisation._
import org.make.api.partner.{AdminPartnerApi, DefaultAdminPartnerApiComponent, DefaultPartnerServiceComponent, DefaultPersistentPartnerServiceComponent}
import org.make.api.personality._
import org.make.api.proposal._
import org.make.api.question._
import org.make.api.segment.DefaultSegmentServiceComponent
import org.make.api.semantic.{DefaultSemanticComponent, DefaultSemanticConfigurationComponent}
import org.make.api.sequence._
import org.make.api.sessionhistory.{ConcurrentModification, DefaultSessionHistoryCoordinatorServiceComponent, SessionHistoryCoordinator, SessionHistoryCoordinatorComponent}
import org.make.api.tag._
import org.make.api.tagtype.{DefaultModerationTagTypeApiComponent, DefaultPersistentTagTypeServiceComponent, DefaultTagTypeServiceComponent, ModerationTagTypeApi}
import org.make.api.technical._
import org.make.api.technical.auth._
import org.make.api.technical.businessconfig.{ConfigurationsApi, DefaultConfigurationsApiComponent}
import org.make.api.technical.crm._
import org.make.api.technical.elasticsearch.{DefaultElasticSearchApiComponent, DefaultElasticsearchClientComponent, DefaultElasticsearchConfigurationComponent, DefaultIndexationComponent, ElasticSearchApi}
import org.make.api.technical.generator.fixtures.{DefaultFixturesApiComponent, DefaultFixturesServiceComponent, FixturesApi}
import org.make.api.technical.healthcheck._
import org.make.api.technical.job.{DefaultJobApiComponent, DefaultJobCoordinatorServiceComponent, JobApi, JobCoordinator, JobCoordinatorComponent}
import org.make.api.technical.monitoring.DefaultMonitoringService
import org.make.api.technical.security.{DefaultSecurityApiComponent, DefaultSecurityConfigurationComponent, SecurityApi}
import org.make.api.technical.storage._
import org.make.api.technical.tracking.{DefaultTrackingApiComponent, TrackingApi}
import org.make.api.technical.webflow.DefaultWebflowClientComponent
import org.make.api.user.UserExceptions.{EmailAlreadyRegisteredException, EmailNotAllowed}
import org.make.api.user._
import org.make.api.user.social.{DefaultFacebookApiComponent, DefaultGoogleApiComponent, DefaultSocialServiceComponent}
import org.make.api.user.validation.DefaultUserRegistrationValidatorComponent
import org.make.api.userhistory.{DefaultUserHistoryCoordinatorServiceComponent, UserHistoryCoordinator, UserHistoryCoordinatorComponent}
import org.make.api.views._
import org.make.api.widget.{DefaultWidgetApiComponent, DefaultWidgetServiceComponent, WidgetApi}
import org.make.core.{AvroSerializers, ValidationError, ValidationFailedError}
import scalaoauth2.provider.{OAuthGrantType, _}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait MakeApi
    extends ActorSystemComponent
    with AvroSerializers
    with BuildInfoRoutes
    with DefaultActiveFeatureServiceComponent
    with DefaultAdminActiveFeatureApiComponent
    with DefaultAdminClientApiComponent
    with DefaultAdminCrmTemplatesApiComponent
    with DefaultAdminCurrentOperationApiComponent
    with DefaultAdminFeatureApiComponent
    with DefaultAdminFeaturedOperationApiComponent
    with DefaultAdminIdeaMappingApiComponent
    with DefaultAdminOperationOfQuestionApiComponent
    with DefaultAdminPartnerApiComponent
    with DefaultAdminPersonalityApiComponent
    with DefaultAdminPersonalityRoleApiComponent
    with DefaultAdminProposalApiComponent
    with DefaultAdminQuestionPersonalityApiComponent
    with DefaultAdminTopIdeaApiComponent
    with DefaultAdminUserApiComponent
    with DefaultAdminViewApiComponent
    with DefaultArticleSearchEngineComponent
    with DefaultAuthenticationApiComponent
    with DefaultClientServiceComponent
    with DefaultConfigurationsApiComponent
    with DefaultCrmApiComponent
    with DefaultCrmClientComponent
    with DefaultCrmServiceComponent
    with DefaultCrmTemplatesServiceComponent
    with DefaultCurrentOperationServiceComponent
    with DefaultDownloadServiceComponent
    with DefaultElasticSearchApiComponent
    with DefaultElasticsearchClientComponent
    with DefaultElasticsearchConfigurationComponent
    with DefaultEventBusServiceComponent
    with DefaultFacebookApiComponent
    with DefaultFeaturedOperationServiceComponent
    with DefaultFeatureServiceComponent
    with DefaultFixturesApiComponent
    with DefaultFixturesServiceComponent
    with DefaultGoogleApiComponent
    with DefaultHomeViewServiceComponent
    with DefaultViewApiComponent
    with DefaultHealthCheckApiComponent
    with DefaultHealthCheckServiceComponent
    with DefaultIdGeneratorComponent
    with DefaultIdeaMappingServiceComponent
    with DefaultIdeaSearchEngineComponent
    with DefaultIdeaServiceComponent
    with DefaultIndexationComponent
    with DefaultJobApiComponent
    with DefaultJobCoordinatorServiceComponent
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
    with DefaultOperationOfQuestionSearchEngineComponent
    with DefaultOperationOfQuestionServiceComponent
    with DefaultOperationServiceComponent
    with DefaultOrganisationApiComponent
    with DefaultOrganisationSearchEngineComponent
    with DefaultOrganisationServiceComponent
    with DefaultPartnerServiceComponent
    with DefaultPersistentActiveFeatureServiceComponent
    with DefaultPersistentAuthCodeServiceComponent
    with DefaultPersistentClientServiceComponent
    with DefaultPersistentCrmTemplatesServiceComponent
    with DefaultPersistentCrmUserServiceComponent
    with DefaultPersistentCurrentOperationServiceComponent
    with DefaultPersistentFeaturedOperationServiceComponent
    with DefaultPersistentFeatureServiceComponent
    with DefaultPersistentIdeaMappingServiceComponent
    with DefaultPersistentIdeaServiceComponent
    with DefaultPersistentOperationOfQuestionServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentPartnerServiceComponent
    with DefaultPersistentPersonalityRoleServiceComponent
    with DefaultPersistentPersonalityRoleFieldServiceComponent
    with DefaultPersistentQuestionPersonalityServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentSequenceConfigurationServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentTagTypeServiceComponent
    with DefaultPersistentTokenServiceComponent
    with DefaultPersistentTopIdeaServiceComponent
    with DefaultPersistentTopIdeaCommentServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentUserToAnonymizeServiceComponent
    with DefaultQuestionPersonalityServiceComponent
    with DefaultPersonalityApiComponent
    with DefaultPersonalityRoleServiceComponent
    with DefaultPersonalityRoleFieldServiceComponent
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
    with DefaultSegmentServiceComponent
    with DefaultSelectionAlgorithmComponent
    with DefaultSemanticComponent
    with DefaultSemanticConfigurationComponent
    with DefaultSendMailPublisherServiceComponent
    with DefaultSequenceConfigurationComponent
    with DefaultSequenceServiceComponent
    with DefaultSessionHistoryCoordinatorServiceComponent
    with DefaultSocialServiceComponent
    with DefaultSortAlgorithmConfigurationComponent
    with DefaultStorageConfigurationComponent
    with DefaultStorageServiceComponent
    with DefaultSwiftClientComponent
    with DefaultTagApiComponent
    with DefaultTagServiceComponent
    with DefaultTagTypeServiceComponent
    with DefaultTokenGeneratorComponent
    with DefaultTopIdeaServiceComponent
    with DefaultTopIdeaCommentServiceComponent
    with DefaultTrackingApiComponent
    with DefaultUserApiComponent
    with DefaultUserHistoryCoordinatorServiceComponent
    with DefaultUserRegistrationValidatorComponent
    with DefaultUserServiceComponent
    with DefaultUserTokenGeneratorComponent
    with DefaultWidgetApiComponent
    with DefaultWebflowClientComponent
    with DefaultWebflowConfigurationComponent
    with DefaultWidgetServiceComponent
    with HealthCheckComponent
    with JobCoordinatorComponent
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
      .resolveOne()(Timeout(10.seconds)),
    atMost = 10.seconds
  )

  override lazy val userHistoryCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / UserHistoryCoordinator.name)
      .resolveOne()(Timeout(10.seconds)),
    atMost = 10.seconds
  )

  override lazy val sessionHistoryCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / SessionHistoryCoordinator.name)
      .resolveOne()(Timeout(10.seconds)),
    atMost = 10.seconds
  )

  override lazy val jobCoordinator: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / s"${JobCoordinator.name}-backoff")
      .resolveOne()(Timeout(5.seconds)),
    atMost = 5.seconds
  )

  override lazy val sequenceConfigurationActor: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / SequenceConfigurationActor.name)
      .resolveOne()(Timeout(10.seconds)),
    atMost = 10.seconds
  )

  override lazy val healthCheckSupervisor: ActorRef = Await.result(
    actorSystem
      .actorSelection(actorSystem / MakeGuardian.name / HealthCheckSupervisor.name)
      .resolveOne()(Timeout(10.seconds)),
    atMost = 10.seconds
  )

  override lazy val readExecutionContext: EC = actorSystem.extension(DatabaseConfiguration).readThreadPool
  override lazy val writeExecutionContext: EC = actorSystem.extension(DatabaseConfiguration).writeThreadPool

  override lazy val tokenEndpoint: TokenEndpoint = new TokenEndpoint {

    private val password: Password = new Password {
      override val clientCredentialRequired = false
    }

    private val reconnect: Reconnect = new Reconnect {
      override val clientCredentialRequired = false
    }

    override val handlers: Map[String, GrantHandler] =
      Map[String, GrantHandler](
        OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode,
        OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials,
        OAuthGrantType.PASSWORD -> password,
        OAuthGrantType.REFRESH_TOKEN -> new RefreshToken,
        Reconnect.RECONNECT_TOKEN -> reconnect
      )
  }

  private lazy val swagger: Route =
    path("swagger") {
      parameters("url".?) {
        case None => redirect(Uri("/swagger?url=/api-docs/swagger.json"), StatusCodes.PermanentRedirect)
        case _    => getFromResource(s"META-INF/resources/webjars/swagger-ui/${BuildInfo.swaggerUiVersion}/index.html")
      }
    } ~ getFromResourceDirectory(s"META-INF/resources/webjars/swagger-ui/${BuildInfo.swaggerUiVersion}")

  private lazy val envDependentApiClasses: Set[Class[_]] =
    makeSettings.environment match {
      case "production" => Set.empty[Class[_]]
      case _            => Set[Class[_]](classOf[FixturesApi])
    }

  private lazy val apiClasses: Set[Class[_]] =
    Set(
      classOf[AdminActiveFeatureApi],
      classOf[AdminClientApi],
      classOf[AdminCrmTemplateApi],
      classOf[AdminCurrentOperationApi],
      classOf[AdminFeatureApi],
      classOf[AdminFeaturedOperationApi],
      classOf[AdminIdeaMappingApi],
      classOf[AdminOperationOfQuestionApi],
      classOf[AdminPartnerApi],
      classOf[AdminPersonalityApi],
      classOf[AdminPersonalityRoleApi],
      classOf[AdminQuestionPersonalityApi],
      classOf[AdminProposalApi],
      classOf[AdminTopIdeaApi],
      classOf[AdminUserApi],
      classOf[AdminViewApi],
      classOf[AuthenticationApi],
      classOf[ConfigurationsApi],
      classOf[CrmApi],
      classOf[ElasticSearchApi],
      classOf[HealthCheckApi],
      classOf[JobApi],
      classOf[MigrationApi],
      classOf[ModerationIdeaApi],
      classOf[ModerationOperationApi],
      classOf[ModerationOperationOfQuestionApi],
      classOf[ModerationOrganisationApi],
      classOf[ModerationProposalApi],
      classOf[ModerationQuestionApi],
      classOf[ModerationSequenceApi],
      classOf[ModerationTagApi],
      classOf[ModerationTagTypeApi],
      classOf[OperationApi],
      classOf[OrganisationApi],
      classOf[PersonalityApi],
      classOf[ProposalApi],
      classOf[QuestionApi],
      classOf[SecurityApi],
      classOf[TagApi],
      classOf[TrackingApi],
      classOf[UserApi],
      classOf[ViewApi],
      classOf[WidgetApi]
    ) ++ envDependentApiClasses

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

  lazy val envDependantRoutes: Route =
    makeSettings.environment match {
      case "production" => swagger
      case _            => swagger ~ fixturesApi.routes
    }

  lazy val makeRoutes: Route =
    (documentation ~
      optionsCors ~
      optionsAuthorized ~
      buildRoutes ~
      envDependantRoutes ~

      adminActiveFeatureApi.routes ~
      adminClientApi.routes ~
      adminCrmTemplateApi.routes ~
      adminCurrentOperationApi.routes ~
      adminFeatureApi.routes ~
      adminFeaturedOperationApi.routes ~
      adminIdeaMappingApi.routes ~
      adminOperationOfQuestionApi.routes ~
      adminPartnerApi.routes ~
      adminPersonalityApi.routes ~
      adminPersonalityRoleApi.routes ~
      adminProposalApi.routes ~
      adminQuestionPersonalityApi.routes ~
      adminTopIdeaApi.routes ~
      adminUserApi.routes ~
      adminViewApi.routes ~
      authenticationApi.routes ~
      configurationsApi.routes ~
      crmApi.routes ~
      elasticSearchApi.routes ~
      healthCheckApi.routes ~
      jobApi.routes ~
      migrationApi.routes ~
      moderationIdeaApi.routes ~
      moderationOperationApi.routes ~
      moderationOperationOfQuestionApi.routes ~
      moderationOrganisationApi.routes ~
      moderationProposalApi.routes ~
      moderationQuestionApi.routes ~
      moderationSequenceApi.routes ~
      moderationTagApi.routes ~
      moderationTagTypeApi.routes ~
      operationApi.routes ~
      organisationApi.routes ~
      personalityApi.routes ~
      proposalApi.routes ~
      questionApi.routes ~
      securityApi.routes ~
      tagApi.routes ~
      trackingApi.routes ~
      userApi.routes ~
      viewApi.routes ~
      widgetApi.routes)
}

object MakeApi extends StrictLogging with Directives with ErrorAccumulatingCirceSupport {

  def defaultError(id: String): String =
    s"""
      |{
      |  "error": "an error occurred, it has been logged with id $id"
      |}
    """.stripMargin

  def exceptionHandler(routeName: String, requestId: String): ExceptionHandler = ExceptionHandler {
    case e: EmailAlreadyRegisteredException =>
      complete(StatusCodes.BadRequest -> Seq(ValidationError("email", "already_registered", Option(e.getMessage))))
    case e: EmailNotAllowed =>
      complete(StatusCodes.Forbidden -> Seq(ValidationError("email", "not_allowed_to_register", Option(e.getMessage))))
    case ValidationFailedError(messages) =>
      complete(
        HttpResponse(
          status = StatusCodes.BadRequest,
          entity = HttpEntity(ContentTypes.`application/json`, messages.asJson.toString)
        )
      )
    case ConcurrentModification(message) => complete(StatusCodes.Conflict -> message)
    case TokenAlreadyRefreshed(message)  => complete(StatusCodes.PreconditionFailed -> message)
    case _: EntityStreamSizeException    => complete(StatusCodes.PayloadTooLarge)
    case e: ClientAccessUnauthorizedException =>
      complete(StatusCodes.Forbidden -> ValidationError("authentication", "forbidden", Some(e.getMessage)))
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
      case MalformedRequestContentRejection(_, DecodingFailures(failures)) =>
        val errors: Seq[ValidationError] = failures.toList.flatMap { failure =>
          failure.history.flatMap {
            case DownField(field) =>
              val errorMessage: String = if (failure.message == "Attempt to decode value on failed cursor") {
                s"The field [.$field] is missing."
              } else {
                failure.message
              }
              Seq(ValidationError(field, "malformed", Option(errorMessage)))

            case _ => Nil
          }
        }
        complete(StatusCodes.BadRequest -> errors)
      case MalformedRequestContentRejection(_, e) =>
        complete(StatusCodes.BadRequest -> Seq(ValidationError("unknown", "malformed", Option(e.getMessage))))
      case MalformedQueryParamRejection(name, _, Some(e: NoSuchMember[_])) =>
        complete(StatusCodes.BadRequest -> Seq(ValidationError(name, "malformed", Some(e.getMessage))))
      case EmailNotVerifiedRejection =>
        complete(
          StatusCodes.Forbidden ->
            Seq(ValidationError("unknown", "email_not_verified", Some("Your email must be verified first")))
        )
    }
    .result()
    .withFallback(RejectionHandler.default)
    .mapRejectionResponse { res =>
      //TODO: change Content-Type to `application/json`
      res
    }

}

trait ActorSystemComponent {
  implicit def actorSystem: ActorSystem
}
