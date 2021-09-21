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
import akka.actor.typed.{ActorRef, SpawnProtocol}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.util.Timeout
import buildinfo.BuildInfo
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport.DecodingFailures
import enumeratum.NoSuchMember
import grizzled.slf4j.Logging
import io.circe.CursorOp.DownField
import io.circe.syntax._
import org.make.api.crmTemplates._
import org.make.api.demographics._
import org.make.api.extensions._
import org.make.api.feature._
import org.make.api.idea._
import org.make.api.idea.topIdeaComments.{
  DefaultPersistentTopIdeaCommentServiceComponent,
  DefaultTopIdeaCommentServiceComponent
}
import org.make.api.keyword.{DefaultKeywordServiceComponent, DefaultPersistentKeywordServiceComponent}
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
import org.make.api.sequence.SequenceConfigurationActor.SequenceConfigurationActorProtocol
import org.make.api.sequence._
import org.make.api.sessionhistory.{
  ConcurrentModification,
  DefaultSessionHistoryCoordinatorServiceComponent,
  SessionHistoryCommand,
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
import org.make.api.technical.security.{
  DefaultAESEncryptionComponent,
  DefaultSecurityApiComponent,
  DefaultSecurityConfigurationComponent,
  SecurityApi
}
import org.make.api.technical.storage._
import org.make.api.technical.tracking.{DefaultTrackingApiComponent, TrackingApi}
import org.make.api.technical.webflow.{DefaultWebflowClientComponent, DefaultWebflowConfigurationComponent}
import org.make.api.user.UserExceptions.{EmailAlreadyRegisteredException, EmailNotAllowed}
import org.make.api.user._
import org.make.api.user.social._
import org.make.api.user.validation.DefaultUserRegistrationValidatorComponent
import org.make.api.userhistory.{
  DefaultUserHistoryCoordinatorServiceComponent,
  UserHistoryCommand,
  UserHistoryCoordinator,
  UserHistoryCoordinatorComponent
}
import org.make.api.views._
import org.make.api.widget._
import org.make.core.{AvroSerializers, DefaultDateHelperComponent, ValidationError, ValidationFailedError}
import scalaoauth2.provider._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait MakeApi
    extends ActorSystemComponent
    with AvroSerializers
    with BuildInfoRoutes
    with ClientDirectives
    with DefaultActiveDemographicsCardServiceComponent
    with DefaultActiveFeatureServiceComponent
    with DefaultAdminActiveDemographicsCardApiComponent
    with DefaultAdminActiveFeatureApiComponent
    with DefaultAdminClientApiComponent
    with DefaultAdminCrmLanguageTemplatesApiComponent
    with DefaultAdminCrmQuestionTemplatesApiComponent
    with DefaultAdminDemographicsCardApiComponent
    with DefaultAdminFeatureApiComponent
    with DefaultAdminModeratorApiComponent
    with DefaultAdminOperationOfQuestionApiComponent
    with DefaultAdminPartnerApiComponent
    with DefaultAdminPersonalityApiComponent
    with DefaultAdminPersonalityRoleApiComponent
    with DefaultAdminProposalApiComponent
    with DefaultAdminQuestionPersonalityApiComponent
    with DefaultAdminSourceApiComponent
    with DefaultAdminTopIdeaApiComponent
    with DefaultAdminUserApiComponent
    with DefaultAdminWidgetApiComponent
    with DefaultAESEncryptionComponent
    with DefaultAuthenticationApiComponent
    with DefaultClientServiceComponent
    with DefaultConfigComponent
    with DefaultConfigurationsApiComponent
    with DefaultCrmApiComponent
    with DefaultCrmClientComponent
    with DefaultCrmServiceComponent
    with DefaultCrmTemplatesServiceComponent
    with DefaultDateHelperComponent
    with DefaultDemographicsCardServiceComponent
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
    with DefaultIdeaSearchEngineComponent
    with DefaultIdeaServiceComponent
    with DefaultIndexationComponent
    with DefaultJobApiComponent
    with DefaultJobCoordinatorServiceComponent
    with DefaultKafkaConfigurationComponent
    with DefaultKeywordServiceComponent
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
    with DefaultAdminSequenceApiComponent
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
    with DefaultPersistentActiveDemographicsCardServiceComponent
    with DefaultPersistentActiveFeatureServiceComponent
    with DefaultPersistentAuthCodeServiceComponent
    with DefaultPersistentClientServiceComponent
    with DefaultPersistentCrmLanguageTemplateServiceComponent
    with DefaultPersistentCrmQuestionTemplateServiceComponent
    with DefaultPersistentCrmSynchroUserServiceComponent
    with DefaultPersistentCrmUserServiceComponent
    with DefaultPersistentDemographicsCardServiceComponent
    with DefaultPersistentFeatureServiceComponent
    with DefaultPersistentIdeaServiceComponent
    with DefaultPersistentKeywordServiceComponent
    with DefaultPersistentOperationOfQuestionServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentPartnerServiceComponent
    with DefaultPersistentPersonalityRoleServiceComponent
    with DefaultPersistentPersonalityRoleFieldServiceComponent
    with DefaultPersistentQuestionPersonalityServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentSequenceConfigurationServiceComponent
    with DefaultPersistentSourceServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentTagTypeServiceComponent
    with DefaultPersistentTokenServiceComponent
    with DefaultPersistentTopIdeaServiceComponent
    with DefaultPersistentTopIdeaCommentServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentUserToAnonymizeServiceComponent
    with DefaultPersistentWidgetServiceComponent
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
    with DefaultQuestionServiceComponent
    with DefaultReadJournalComponent
    with DefaultSecurityApiComponent
    with DefaultSecurityConfigurationComponent
    with DefaultSegmentServiceComponent
    with DefaultSendMailPublisherServiceComponent
    with DefaultSequenceApiComponent
    with DefaultSequenceConfigurationComponent
    with DefaultSequenceServiceComponent
    with DefaultSessionHistoryCoordinatorServiceComponent
    with DefaultSocialProvidersConfigurationComponent
    with DefaultSocialServiceComponent
    with DefaultSourceServiceComponent
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
    with DefaultWidgetServiceComponent
    with DefaultWebflowClientComponent
    with DefaultWebflowConfigurationComponent
    with JobCoordinatorComponent
    with MakeAuthentication
    with MakeDBExecutionContextComponent
    with ProposalCoordinatorComponent
    with SequenceConfigurationActorComponent
    with SessionHistoryCoordinatorComponent
    with SpawnActorRefComponent
    with UserHistoryCoordinatorComponent
    with Logging {

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override lazy val proposalCoordinator: ActorRef[ProposalCommand] =
    Await.result(actorSystem.findRefByKey(ProposalCoordinator.Key), atMost = 10.seconds)

  override lazy val userHistoryCoordinator: ActorRef[UserHistoryCommand] =
    Await.result(actorSystem.findRefByKey(UserHistoryCoordinator.Key), atMost = 10.seconds)

  override lazy val sessionHistoryCoordinator: ActorRef[SessionHistoryCommand] =
    Await.result(actorSystem.findRefByKey(SessionHistoryCoordinator.Key), atMost = 10.seconds)

  override lazy val jobCoordinator: ActorRef[JobActor.Protocol.Command] = Await.result({
    actorSystem.findRefByKey(JobCoordinator.Key)
  }, atMost = 5.seconds)

  override lazy val spawnActorRef: ActorRef[SpawnProtocol.Command] = Await.result({
    actorSystem.findRefByKey(MakeGuardian.SpawnActorKey)
  }, atMost = 5.seconds)

  override lazy val sequenceConfigurationActor: ActorRef[SequenceConfigurationActorProtocol] = Await.result({
    actorSystem.findRefByKey(SequenceConfigurationActor.SequenceCacheActorKey)
  }, atMost = 5.seconds)

  override lazy val readExecutionContext: EC = DatabaseConfigurationExtension(actorSystem).readThreadPool
  override lazy val writeExecutionContext: EC = DatabaseConfigurationExtension(actorSystem).writeThreadPool

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
      classOf[AdminActiveDemographicsCardApi],
      classOf[AdminActiveFeatureApi],
      classOf[AdminClientApi],
      classOf[AdminCrmLanguageTemplatesApi],
      classOf[AdminCrmQuestionTemplatesApi],
      classOf[AdminDemographicsCardApi],
      classOf[AdminFeatureApi],
      classOf[AdminModeratorApi],
      classOf[AdminOperationOfQuestionApi],
      classOf[AdminPartnerApi],
      classOf[AdminPersonalityApi],
      classOf[AdminPersonalityRoleApi],
      classOf[AdminQuestionPersonalityApi],
      classOf[AdminProposalApi],
      classOf[AdminSourceApi],
      classOf[AdminTopIdeaApi],
      classOf[AdminUserApi],
      classOf[AdminWidgetApi],
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
      classOf[AdminSequenceApi],
      classOf[ModerationTagApi],
      classOf[ModerationTagTypeApi],
      classOf[OrganisationApi],
      classOf[PersonalityApi],
      classOf[ProposalApi],
      classOf[QuestionApi],
      classOf[SecurityApi],
      classOf[SequenceApi],
      classOf[TagApi],
      classOf[TrackingApi],
      classOf[UserApi],
      classOf[ViewApi]
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
    documentation ~
      optionsCors ~
      optionsAuthorized ~
      buildRoutes ~
      envDependantRoutes ~
      adminActiveDemographicsCardApi.routes ~
      adminActiveFeatureApi.routes ~
      adminClientApi.routes ~
      adminCrmLanguageTemplatesApi.routes ~
      adminCrmQuestionTemplatesApi.routes ~
      adminDemographicsCardApi.routes ~
      adminFeatureApi.routes ~
      adminModeratorApi.routes ~
      adminOperationOfQuestionApi.routes ~
      adminPartnerApi.routes ~
      adminPersonalityApi.routes ~
      adminPersonalityRoleApi.routes ~
      adminProposalApi.routes ~
      adminQuestionPersonalityApi.routes ~
      adminSourceApi.routes ~
      adminTopIdeaApi.routes ~
      adminUserApi.routes ~
      adminWidgetApi.routes ~
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
      adminSequenceApi.routes ~
      moderationTagApi.routes ~
      moderationTagTypeApi.routes ~
      organisationApi.routes ~
      personalityApi.routes ~
      proposalApi.routes ~
      questionApi.routes ~
      securityApi.routes ~
      sequenceApi.routes ~
      tagApi.routes ~
      trackingApi.routes ~
      userApi.routes ~
      viewApi.routes
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
    case e: SocialProviderException =>
      complete(StatusCodes.BadRequest -> Seq(ValidationError("token", "invalid_token", Option(e.getMessage))))
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

trait DefaultConfigComponent extends ConfigComponent {
  self: ActorSystemComponent =>

  override def config: Config = actorSystem.settings.config
}
