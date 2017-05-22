package org.make.api

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.make.api.auth.{MakeDataHandlerComponent, TokenServiceComponent}
import org.make.api.citizen.{CitizenApi, CitizenServiceComponent, PersistentCitizenServiceComponent}
import org.make.api.kafka._
import org.make.api.proposition._
import org.make.api.swagger.MakeDocumentation

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.runtime.{universe => ru}
import scalaoauth2.provider._



trait MakeApi extends CitizenServiceComponent
  with IdGeneratorComponent
  with PersistentCitizenServiceComponent
  with CitizenApi
  with PropositionServiceComponent
  with PropositionApi
  with BuildInfoRoutes
  with AvroSerializers
  with MakeDataHandlerComponent
  with TokenServiceComponent {

  def actorSystem: ActorSystem

  override lazy val idGenerator: IdGenerator = new UUIDIdGenerator
  override lazy val citizenService: CitizenService = new CitizenService()
  override lazy val propositionService: PropositionService = new PropositionService(
    Await.result(
      actorSystem.actorSelection(actorSystem / MakeGuardian.name / PropositionSupervisor.name / PropositionCoordinator.name)
        .resolveOne()(Timeout(2.seconds)),
      atMost = 2.seconds
    )

  )
  override lazy val persistentCitizenService: PersistentCitizenService = new PersistentCitizenService()
  override lazy val oauth2DataHandler: MakeDataHandler = new MakeDataHandler()(ECGlobal)
  override lazy val tokenService: TokenService = new TokenService()
  override lazy val readExecutionContext: EC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(50))
  override lazy val writeExecutionContext: EC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(20))
  override lazy val tokenEndpoint: TokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.IMPLICIT -> new Implicit,
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials,
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode,
      OAuthGrantType.PASSWORD -> new Password,
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken
    )
  }


  private lazy val swagger: Route =
    path("swagger") {
      getFromResource("META-INF/resources/webjars/swagger-ui/2.2.8/index.html")
    } ~ getFromResourceDirectory("META-INF/resources/webjars/swagger-ui/2.2.8")

  private lazy val login: Route = path("login.html") {
    getFromResource("auth/login.html")
  }

  private lazy val apiTypes: Seq[ru.Type] = Seq(ru.typeOf[CitizenApi], ru.typeOf[PropositionApi])

  lazy val makeRoutes: Route =
    new MakeDocumentation(actorSystem, apiTypes).routes ~
    swagger ~
    login ~
    citizenRoutes ~
    propositionRoutes ~
    accessTokenRoute ~
    buildRoutes
}



