package org.make.api

import java.time.ZonedDateTime
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import org.make.api.auth.{MakeDataHandlerComponent, TokenServiceComponent}
import org.make.api.citizen.{CitizenApi, CitizenServiceComponent, PersistentCitizenServiceComponent}
import org.make.api.database.DatabaseConfiguration
import org.make.api.kafka._
import org.make.api.proposition._
import org.make.api.swagger.MakeDocumentation
import org.make.core.proposition.PropositionId

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.runtime.{universe => ru}
import scalaoauth2.provider._


object MakeApi extends App
  with CitizenServiceComponent
  with IdGeneratorComponent
  with PersistentCitizenServiceComponent
  with CitizenApi
  with PropositionServiceComponent
  with PropositionApi
  with MakeDataHandlerComponent
  with TokenServiceComponent
  with AvroSerializers
  with StrictLogging {

  private val actorSystem = ActorSystem("make-api")
  actorSystem.registerExtension(DatabaseConfiguration)
  actorSystem.actorOf(MakeGuardian.props, MakeGuardian.name)

  private val settings = MakeSettings(actorSystem)

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

  initHttpRoutes(actorSystem)

  if (settings.sendTestData) {
    Thread.sleep(10000)
    logger.debug("Proposing...")
    propositionService.propose(idGenerator.nextCitizenId(), ZonedDateTime.now, "Il faut que la demo soit fonctionnelle.")
    val propId: PropositionId = Await.result(propositionService
      .propose(idGenerator.nextCitizenId(), ZonedDateTime.now, "Il faut faire une proposition"), Duration.Inf) match {
      case Some(proposition) => proposition.propositionId
      case None => PropositionId("Invalid PropositionId")
    }
    propositionService.update(propId, ZonedDateTime.now, "Il faut mettre a jour une proposition")
    logger.debug("Sent propositions...")
  }


  private def initHttpRoutes(implicit actorSystem: ActorSystem) = {
    implicit val ec = actorSystem.dispatcher
    implicit val materializer = ActorMaterializer()

    val swagger =
      path("swagger") {
        getFromResource("META-INF/resources/webjars/swagger-ui/2.2.8/index.html")
      } ~
        getFromResourceDirectory("META-INF/resources/webjars/swagger-ui/2.2.8")

    val login = path("login.html") {
      getFromResource("auth/login.html")
    }

    val apiTypes: Seq[ru.Type] = Seq(ru.typeOf[CitizenApi], ru.typeOf[PropositionApi])

    val host = settings.http.host
    val port = settings.http.port

    val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(
      new MakeDocumentation(actorSystem, apiTypes).routes ~
        swagger ~
        login ~
        citizenRoutes ~
        propositionRoutes ~
        accessTokenRoute,
      host, port)

    bindingFuture.map { serverBinding =>
      logger.info(s"Make API bound to ${serverBinding.localAddress} ")
    }.onComplete {
      case util.Failure(ex) =>
        logger.error(s"Failed to bind to $host:$port!", ex)
        actorSystem.terminate()
      case _ =>
    }
  }
}



