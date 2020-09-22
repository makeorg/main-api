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

import java.net.InetAddress
import java.nio.file.{Files, Paths}

import akka.actor.{ActorSystem, ExtendedActorSystem, PoisonPill}
import akka.cluster.Cluster
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.pattern.ask
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import kamon.Kamon
import org.make.api.MakeGuardian.Ping
import org.make.api.extensions.ThreadPoolMonitoringActor.MonitorThreadPool
import org.make.api.extensions.{DatabaseConfiguration, MakeSettings, ThreadPoolMonitoringActor}
import org.make.api.proposal.ShardedProposal
import org.make.api.sessionhistory.ShardedSessionHistory
import org.make.api.technical.MakePersistentActor.StartShard
import org.make.api.technical.{ClusterShardingMonitor, MemoryMonitoringActor}
import org.make.api.userhistory.ShardedUserHistory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.jdk.CollectionConverters._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.HttpConnectionContext

@SuppressWarnings(Array("org.wartremover.warts.While"))
object MakeMain extends App with StrictLogging with MakeApi {

  Thread.setDefaultUncaughtExceptionHandler { (thread, exception) =>
    logger.error(s"Error in thread ${thread.getName}:", exception)
  }

  val envName: Option[String] = Option(System.getenv("ENV_NAME"))

  private val resourceName = envName match {
    case Some(name) if !name.isEmpty => s"$name-application.conf"
    case None                        => "default-application.conf"
  }

  private val configuration: Config = {
    val defaultConfiguration = ConfigFactory.load(resourceName)
    val configurationPath = defaultConfiguration.getString("make-api.secrets-configuration-path")
    val extraConfigPath = Paths.get(configurationPath)
    val configWithSecrets = if (Files.exists(extraConfigPath) && !Files.isDirectory(extraConfigPath)) {
      ConfigFactory.parseFile(extraConfigPath.toFile).resolve().withFallback(defaultConfiguration)
    } else {
      defaultConfiguration
    }

    val bindAddress = configWithSecrets.getString("akka.remote.artery.canonical.hostname")
    val resolvedAddress: String = InetAddress.getByName(bindAddress).getHostAddress

    ConfigFactory
      .parseMap(Map("akka.remote.artery.canonical.hostname" -> resolvedAddress).asJava)
      .withFallback(configWithSecrets)
      .resolve()
  }

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  override implicit val actorSystem: ExtendedActorSystem =
    ActorSystem.apply("make-api", configuration).asInstanceOf[ExtendedActorSystem]

  // Wait until cluster is connected before initializing clustered actors
  while (Cluster(actorSystem).state.leader.isEmpty) {
    Thread.sleep(100)
  }

  Kamon.init(configuration)

  private val databaseConfiguration = actorSystem.registerExtension(DatabaseConfiguration)

  Await.result(elasticsearchClient.initialize(), 10.seconds)

  Await.result(swiftClient.init(), 10.seconds)

  private val guardian = actorSystem.actorOf(MakeGuardian.props(makeApi = this), MakeGuardian.name)

  Await.result(guardian ? Ping, atMost = 5.seconds)

  actorSystem.systemActorOf(ClusterShardingMonitor.props, ClusterShardingMonitor.name)
  actorSystem.systemActorOf(MemoryMonitoringActor.props, MemoryMonitoringActor.name)
  private val threadPoolMonitor =
    actorSystem.systemActorOf(ThreadPoolMonitoringActor.props, ThreadPoolMonitoringActor.name)
  threadPoolMonitor ! MonitorThreadPool(databaseConfiguration.readExecutor, "db-read-pool")
  threadPoolMonitor ! MonitorThreadPool(databaseConfiguration.writeExecutor, "db-write-pool")

  // Start the shards
  (0 until 100).foreach { i =>
    proposalCoordinator ! StartShard(i.toString)
    userHistoryCoordinator ! StartShard(i.toString)
    sessionHistoryCoordinator ! StartShard(i.toString)
  }

  private val settings = MakeSettings(actorSystem)

  // Initialize journals
  actorSystem.actorOf(ShardedProposal.props(sessionHistoryCoordinatorService, settings.lockDuration), "fake-proposal") ! PoisonPill
  actorSystem.actorOf(ShardedUserHistory.props, "fake-user") ! PoisonPill
  actorSystem.actorOf(ShardedSessionHistory.props(userHistoryCoordinator, 1.second), "fake-session") ! PoisonPill

  // Ensure database stuff is initialized
  Await.result(userService.getUserByEmail("admin@make.org"), atMost = 20.seconds)

  implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher

  private val host = settings.Http.host
  private val port = settings.Http.port

  val bindingFuture: Future[ServerBinding] =
    Http().bindAndHandleAsync(
      handler = Route.asyncHandler(makeRoutes),
      interface = host,
      port = port,
      connectionContext = HttpConnectionContext()
    )

  bindingFuture.map { serverBinding =>
    logger.info(s"Make API bound to ${serverBinding.localAddress} ")
  }.onComplete {
    case util.Failure(ex) =>
      logger.error(s"Failed to bind to $host:$port!", ex)
      actorSystem.terminate()
    case _ =>
  }

}
