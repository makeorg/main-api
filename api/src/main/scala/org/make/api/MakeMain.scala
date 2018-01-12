package org.make.api

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
import kamon.system.SystemMetrics
import org.make.api.extensions.{DatabaseConfiguration, MakeSettings}
import org.make.api.technical.elasticsearch.ElasticsearchConfiguration
import org.make.api.technical.{ClusterShardingMonitor, MemoryMonitoringActor}

import scala.concurrent.{ExecutionContextExecutor, Future}

object MakeMain extends App with StrictLogging with MakeApi {

  val envName: Option[String] = Option(System.getenv("ENV_NAME"))

  val resourceName = envName match {
    case Some(name) if !name.isEmpty => s"$name-application.conf"
    case None                        => "default-application.conf"
  }
  System.setProperty("config.resource", resourceName)

  Kamon.addReporter(new PrometheusReporter())
  SystemMetrics.startCollecting()

  override implicit val actorSystem: ExtendedActorSystem =
    ActorSystem.apply("make-api").asInstanceOf[ExtendedActorSystem]

  actorSystem.registerExtension(DatabaseConfiguration)
  actorSystem.registerExtension(ElasticsearchConfiguration)
  actorSystem.actorOf(
    MakeGuardian.props(
      persistentSequenceConfigurationService = persistentSequenceConfigurationService,
      userService = userService,
      tagService = tagService,
      themeService = themeService,
      sequenceService = sequenceService,
      operationService = operationService
    ),
    MakeGuardian.name
  )
  actorSystem.systemActorOf(ClusterShardingMonitor.props, ClusterShardingMonitor.name)
  actorSystem.systemActorOf(MemoryMonitoringActor.props, MemoryMonitoringActor.name)

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
