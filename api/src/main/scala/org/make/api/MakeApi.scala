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

import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.actor.typed.{SpawnProtocol, ActorRef => TypedActorRef, ActorSystem => ActorSystemTyped}
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.util.Timeout
import buildinfo.BuildInfo
import com.typesafe.config.Config
import grizzled.slf4j.Logging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport.DecodingFailures
import enumeratum.NoSuchMember
import io.circe.CursorOp.DownField
import io.circe.syntax._
import org.make.api.crmTemplates.{
  AdminCrmLanguageTemplatesApi,
  AdminCrmQuestionTemplatesApi,
  DefaultAdminCrmLanguageTemplatesApiComponent,
  DefaultAdminCrmQuestionTemplatesApiComponent,
  DefaultCrmTemplatesServiceComponent,
  DefaultPersistentCrmLanguageTemplateServiceComponent,
  DefaultPersistentCrmQuestionTemplateServiceComponent
}
import org.make.api.extensions._
import org.make.api.feature._
import org.make.api.idea._
import org.make.api.idea.topIdeaComments.{
  DefaultPersistentTopIdeaCommentServiceComponent,
  DefaultTopIdeaCommentServiceComponent
}
import org.make.api.operation._
import org.make.api.organisation._
import org.make.api.partner.{
  AdminPartnerApi,
  DefaultAdminPartnerApiComponent,
  DefaultPartnerServiceComponent,
  DefaultPersistentPartnerServiceComponent
}
import org.make.api.personality._
import org.make.api.post.{DefaultPostSearchEngineComponent, DefaultPostServiceComponent}
import org.make.api.proposal._
import org.make.api.question._
import org.make.api.segment.DefaultSegmentServiceComponent
import org.make.api.semantic.{DefaultSemanticComponent, DefaultSemanticConfigurationComponent}
import org.make.api.sequence._
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
import org.make.api.technical.ActorSystemHelper._
import org.make.api.technical._
import org.make.api.technical.auth._
import org.make.api.technical.businessconfig.{ConfigurationsApi, DefaultConfigurationsApiComponent}
import org.make.api.technical.crm._
import org.make.api.technical.directives.ClientDirectives
import org.make.api.technical.elasticsearch.{
  DefaultElasticSearchApiComponent,
  DefaultElasticsearchClientComponent,
  DefaultElasticsearchConfigurationComponent,
  DefaultIndexationComponent,
  ElasticSearchApi
}
import org.make.api.technical.generator.fixtures.{
  DefaultFixturesApiComponent,
  DefaultFixturesServiceComponent,
  FixturesApi
}
import org.make.api.technical.graphql._
import org.make.api.technical.healthcheck._
import org.make.api.technical.job._
import org.make.api.technical.monitoring.DefaultMonitoringService
import org.make.api.technical.security.{DefaultSecurityApiComponent, DefaultSecurityConfigurationComponent, SecurityApi}
import org.make.api.technical.storage._
import org.make.api.technical.tracking.{DefaultTrackingApiComponent, TrackingApi}
import org.make.api.technical.webflow.{DefaultWebflowClientComponent, DefaultWebflowConfigurationComponent}
import org.make.api.user.UserExceptions.{EmailAlreadyRegisteredException, EmailNotAllowed}
import org.make.api.user._
import org.make.api.user.social.{
  DefaultFacebookApiComponent,
  DefaultGoogleApiComponent,
  DefaultSocialProvidersConfigurationComponent,
  DefaultSocialServiceComponent
}
import org.make.api.user.validation.DefaultUserRegistrationValidatorComponent
import org.make.api.userhistory.{
  DefaultUserHistoryCoordinatorServiceComponent,
  UserHistoryCoordinator,
  UserHistoryCoordinatorComponent
}
import org.make.api.views._
import org.make.api.widget.{DefaultWidgetApiComponent, DefaultWidgetServiceComponent, WidgetApi}
import org.make.core.{AvroSerializers, DefaultDateHelperComponent, ValidationError, ValidationFailedError}
import scalaoauth2.provider.{OAuthGrantType, _}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait MakeApi
    extends ActorSystemComponent
    with ActorSystemTypedComponent
    with AvroSerializers
    with BuildInfoRoutes
    with ClientDirectives
    with DefaultActiveFeatureServiceComponent
    with DefaultAdminActiveFeatureApiComponent
    with DefaultAdminClientApiComponent
    with DefaultAdminCrmLanguageTemplatesApiComponent
    with DefaultAdminCrmQuestionTemplatesApiComponent
    with DefaultAdminFeatureApiComponent
    with DefaultAdminIdeaMappingApiComponent
    with DefaultAdminOperationOfQuestionApiComponent
    with DefaultAdminPartnerApiComponent
    with DefaultAdminPersonalityApiComponent
    with DefaultAdminPersonalityRoleApiComponent
    with DefaultAdminProposalApiComponent
    with DefaultAdminQuestionPersonalityApiComponent
    with DefaultAdminTopIdeaApiComponent
    with DefaultAdminUserApiComponent
    with DefaultAuthenticationApiComponent
    with DefaultClientServiceComponent
    with DefaultConfigComponent
    with DefaultConfigurationsApiComponent
    with DefaultCrmApiComponent
    with DefaultCrmClientComponent
    with DefaultCrmServiceComponent
    with DefaultCrmTemplatesServiceComponent
    with DefaultDateHelperComponent
    with DefaultDownloadServiceComponent
    with DefaultElasticSearchApiComponent
    with DefaultElasticsearchClientComponent
    with DefaultElasticsearchConfigurationComponent
    with DefaultEventBusServiceComponent
    with DefaultFacebookApiComponent
    with DefaultFeatureServiceComponent
    with DefaultFixturesApiComponent
    with DefaultFixturesServiceComponent
    with DefaultGoogleApiComponent
    with DefaultGraphQLApiComponent
    with DefaultGraphQLRuntimeComponent
    with DefaultGraphQLAuthorServiceComponent
    with DefaultGraphQLIdeaServiceComponent
    with DefaultGraphQLOrganisationServiceComponent
    with DefaultGraphQLQuestionServiceComponent
    with DefaultGraphQLTagServiceComponent
    with DefaultHomeViewServiceComponent
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
    with DefaultPersistentCrmLanguageTemplateServiceComponent
    with DefaultPersistentCrmQuestionTemplateServiceComponent
    with DefaultPersistentCrmUserServiceComponent
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
    with DefaultPostSearchEngineComponent
    with DefaultPostServiceComponent
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
    with DefaultSocialProvidersConfigurationComponent
    with DefaultSocialServiceComponent
    with DefaultSortAlgorithmConfigurationComponent
    with DefaultSpawnActorServiceComponent
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
    with DefaultViewApiComponent
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
    with SpawnActorRefComponent
    with Logging
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

  override lazy val jobCoordinator: TypedActorRef[JobActor.Protocol.Command] = Await.result({
    actorSystemTyped.findRefByKey(JobCoordinator.Key)
  }, atMost = 5.seconds)

  override lazy val spawnActorRef: TypedActorRef[SpawnProtocol.Command] = Await.result({
    actorSystemTyped.findRefByKey(MakeGuardian.SpawnActorKey)
  }, atMost = 5.seconds)

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
      classOf[AdminCrmLanguageTemplatesApi],
      classOf[AdminCrmQuestionTemplatesApi],
      classOf[AdminFeatureApi],
      classOf[AdminIdeaMappingApi],
      classOf[AdminOperationOfQuestionApi],
      classOf[AdminPartnerApi],
      classOf[AdminPersonalityApi],
      classOf[AdminPersonalityRoleApi],
      classOf[AdminQuestionPersonalityApi],
      classOf[AdminProposalApi],
      classOf[AdminTopIdeaApi],
      classOf[AdminUserApi],
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
      adminCrmLanguageTemplatesApi.routes ~
      adminCrmQuestionTemplatesApi.routes ~
      adminFeatureApi.routes ~
      adminIdeaMappingApi.routes ~
      adminOperationOfQuestionApi.routes ~
      adminPartnerApi.routes ~
      adminPersonalityApi.routes ~
      adminPersonalityRoleApi.routes ~
      adminProposalApi.routes ~
      adminQuestionPersonalityApi.routes ~
      adminTopIdeaApi.routes ~
      adminUserApi.routes ~
      authenticationApi.routes ~
      configurationsApi.routes ~
      crmApi.routes ~
      elasticSearchApi.routes ~
      graphQLApi.routes ~
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

object MakeApi extends Logging with Directives with ErrorAccumulatingCirceSupport {

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
    case e: OAuthError =>
      complete(
        StatusCodes.getForKey(e.statusCode).getOrElse(StatusCodes.Unauthorized) ->
          ValidationError("authentication", e.errorType, Some(e.description))
      )
    case ConcurrentModification(message) => complete(StatusCodes.Conflict -> message)
    case TokenAlreadyRefreshed(message)  => complete(StatusCodes.PreconditionFailed -> message)
    case _: EntityStreamSizeException    => complete(StatusCodes.PayloadTooLarge)
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
        val errors: Seq[ValidationError] = failures.toList.map { failure =>
          val path = failure.history.collect { case DownField(field) => field }.reverse.mkString(".")
          val errorMessage: String = if (failure.message == "Attempt to decode value on failed cursor") {
            s"The field [.$path] is missing."
          } else {
            failure.message
          }
          ValidationError(path, "malformed", Option(errorMessage))
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

trait ActorSystemTypedComponent {
  implicit def actorSystemTyped: ActorSystemTyped[Nothing]
}

trait ActorSystemComponent {
  implicit def actorSystem: ActorSystem
}

trait ConfigComponent {
  def config: Config
}

trait DefaultConfigComponent extends ConfigComponent {
  self: ActorSystemComponent =>

  override def config: Config = actorSystem.settings.config
}
