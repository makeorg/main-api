package org.make.api

import java.util.concurrent.Executors

import akka.actor.{ActorSystem, Extension}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import com.typesafe.config.Config
import org.make.api.auth.{MakeDataHandlerComponent, TokenServiceComponent}
import org.make.api.citizen.{CitizenApi, CitizenServiceComponent, PersistentCitizenServiceComponent}
import org.make.api.database.DatabaseConfiguration
import org.make.api.kafka.ConsumerActor.Consume
import org.make.api.kafka.{AvroSerializers, ConsumerActor, ProducerActor}
import org.make.api.proposition.{PropositionApi, PropositionCoordinator, PropositionServiceComponent}
import org.make.api.swagger.MakeDocumentation
import org.make.core.citizen.CitizenEvent.CitizenEventWrapper

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
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
  with RequestTimeout
  with AvroSerializers {

  implicit val ctx: EC = ECGlobal

  val swagger =
    path("swagger") {
      getFromResource("META-INF/resources/webjars/swagger-ui/2.2.8/index.html")
    } ~
      getFromResourceDirectory("META-INF/resources/webjars/swagger-ui/2.2.8")

  val login = path("login.html") {
    getFromResource("auth/login.html")
  }

  private implicit val actorSystem = ActorSystem("make-api")
  actorSystem.registerExtension(DatabaseConfiguration)

  private val propositionCoordinator = actorSystem.actorOf(PropositionCoordinator.props, PropositionCoordinator.name)
  override val idGenerator: IdGenerator = new UUIDIdGenerator
  override val citizenService: CitizenService = new CitizenService()
  override val propositionService: PropositionService = new PropositionService(propositionCoordinator)
  override val persistentCitizenService: PersistentCitizenService = new PersistentCitizenService()
  override val oauth2DataHandler: MakeDataHandler = new MakeDataHandler()
  override val tokenService: TokenService = new TokenService()

  override def readExecutionContext: EC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(50))
  override def writeExecutionContext: EC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(20))

  override val tokenEndpoint: TokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.IMPLICIT -> new Implicit,
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials,
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode,
      OAuthGrantType.PASSWORD -> new Password,
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken
    )
  }

  val config = actorSystem.settings.config
  val settings = new MakeSettings(actorSystem.settings.config)

  if (settings.useEmbeddedElasticSearch) {
    org.make.api.EmbeddedApplication.embeddedElastic.start()
  }

  val host = settings.http.host
  val port = settings.http.port

  implicit val ec = actorSystem.dispatcher
  implicit val materializer = ActorMaterializer()

  val apiTypes: Seq[ru.Type] = Seq(ru.typeOf[CitizenApi], ru.typeOf[PropositionApi])
  val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(
    new MakeDocumentation(actorSystem, apiTypes).routes ~
      swagger ~
      login ~
      citizenRoutes ~
      propositionRoutes ~
      accessTokenRoute,
    host, port)

  val producer = actorSystem.actorOf(ProducerActor.props, ProducerActor.name)
  val citizenConsumer = actorSystem.actorOf(ConsumerActor.props(RecordFormat[CitizenEventWrapper]), ConsumerActor.name)

  citizenConsumer ! Consume

  val log = Logging(actorSystem.eventStream, "make-api")
  bindingFuture.map { serverBinding =>
    log.info(s"Shoppers API bound to ${serverBinding.localAddress} ")
  }.onComplete {
    case util.Failure(ex) =>
      log.error(ex, "Failed to bind to {}:{}!", host, port)
      actorSystem.terminate()
    case _ =>
  }

}

trait RequestTimeout {

  import scala.concurrent.duration._

  def requestTimeout(config: Config): Timeout = {
    val t = config.getString("akka.http.server.request-timeout")
    val d = Duration(t)
    FiniteDuration(d.length, d.unit)
  }
}

class MakeSettings(config: Config) extends Extension {

  val passivateTimeout: Duration = Duration(config.getString("make-api.passivate-timeout"))
  val useEmbeddedElasticSearch: Boolean =
    if (config.hasPath("make-api.dev.embeddedElasticSearch")) config.getBoolean("make-api.dev.embeddedElasticSearch")
    else false

  object http {
    val host: String = config.getString("make-api.http.host")
    val port: Int = config.getInt("make-api.http.port")
  }

}



