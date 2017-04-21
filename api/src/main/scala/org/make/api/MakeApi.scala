package org.make.api

import akka.actor.{ActorSystem, Extension}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import org.make.api.citizen.{CitizenActors, CitizenApi, CitizenServiceComponent}
import org.make.api.kafka.ConsumerActor.Consume
import org.make.api.kafka.{ConsumerActor, ProducerActor}
import org.make.api.swagger.MakeDocumentation

import scala.concurrent.Future
import scala.concurrent.duration.Duration

object MakeApi extends App
  with CitizenServiceComponent
  with IdGeneratorComponent
  with CitizenApi
  with RequestTimeout {

  val site =
    path("swagger") { getFromResource("META-INF/resources/webjars/swagger-ui/2.2.8/index.html") } ~
      getFromResourceDirectory("META-INF/resources/webjars/swagger-ui/2.2.8")

  private implicit val actorSystem = ActorSystem("make-api")
  private val citizenCoordinator = actorSystem.actorOf(CitizenActors.props, CitizenActors.name)

  override val idGenerator: MakeApi.IdGenerator = new UUIDIdGenerator
  override val citizenService: MakeApi.CitizenService = new CitizenService(citizenCoordinator)


  val config = actorSystem.settings.config
  val settings = new MakeSettings(actorSystem.settings.config)

  if(settings.useEmbeddedElasticSearch) {
    org.make.api.EmbeddedApplication.embeddedElastic.start()
  }

  val host = settings.http.host
  val port = settings.http.port

  implicit val ec = actorSystem.dispatcher
  implicit val materializer = ActorMaterializer()

  val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(new MakeDocumentation(actorSystem).routes ~ route ~ site, host, port)

  val producer = actorSystem.actorOf(ProducerActor.props, ProducerActor.name)
  val consumer = actorSystem.actorOf(ConsumerActor.props, ConsumerActor.name)

  consumer ! Consume

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
    if(config.hasPath("make-api.dev.embeddedElasticSearch")) config.getBoolean("make-api.dev.embeddedElasticSearch")
    else false

  object http {
    val host: String = config.getString("make-api.http.host")
    val port: Int = config.getInt("make-api.http.port")
  }

}



