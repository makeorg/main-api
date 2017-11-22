package org.make.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import kamon.Kamon
import org.make.api.extensions.{DatabaseConfiguration, MakeSettings}
import org.make.api.technical.elasticsearch.ElasticsearchConfiguration

import scala.concurrent.{ExecutionContextExecutor, Future}

object MakeMain extends App with StrictLogging with MakeApi {

  Kamon.start()

  val envName: Option[String] = Option(System.getenv("ENV_NAME"))

  val configuration: Config = envName match {
    case Some(name) if !name.isEmpty => ConfigFactory.load(s"$name-application.conf")
    case None                        => ConfigFactory.load("default-application.conf")
  }

  override implicit val actorSystem: ActorSystem = ActorSystem.apply("make-api", configuration)

  actorSystem.registerExtension(DatabaseConfiguration)
  actorSystem.registerExtension(ElasticsearchConfiguration)
  actorSystem.actorOf(
    MakeGuardian.props(
      userService = userService,
      tagService = tagService,
      themeService = themeService,
      sequenceService = sequenceService
    ),
    MakeGuardian.name
  )

  private val settings = MakeSettings(actorSystem)
  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val host = settings.Http.host
  val port = settings.Http.port

  val bindingFuture: Future[ServerBinding] =
    Http().bindAndHandle(makeRoutes, host, port)

  bindingFuture.map { serverBinding =>
    logger.info(s"Make API bound to ${serverBinding.localAddress} ")
  }.onComplete {
    case util.Failure(ex) =>
      logger.error(s"Failed to bind to $host:$port!", ex)
      actorSystem.terminate()
    case _ =>
  }

}
