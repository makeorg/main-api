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
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import kamon.Kamon
import kamon.prometheus.PrometheusReporter
import kamon.system.SystemMetrics
import org.make.api.extensions.ThreadPoolMonitoringActor.MonitorThreadPool
import org.make.api.extensions.{DatabaseConfiguration, MakeSettings, ThreadPoolMonitoringActor}
import org.make.api.migrations._
import org.make.api.proposal.ShardedProposal
import org.make.api.sequence.ShardedSequence
import org.make.api.sessionhistory.ShardedSessionHistory
import org.make.api.technical.MakePersistentActor.StartShard
import org.make.api.technical.{ClusterShardingMonitor, MemoryMonitoringActor}
import org.make.api.userhistory.ShardedUserHistory
import org.make.core.DateHelper
import akka.pattern.ask
import org.make.api.MakeGuardian.Ping

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object MakeMain extends App with StrictLogging with MakeApi {

  val envName: Option[String] = Option(System.getenv("ENV_NAME"))

  val resourceName = envName match {
    case Some(name) if !name.isEmpty => s"$name-application.conf"
    case None                        => "default-application.conf"
  }
  System.setProperty("config.resource", resourceName)

  Kamon.addReporter(new PrometheusReporter())
  SystemMetrics.startCollecting()

  private val configuration: Config = {
    val defaultConfiguration = ConfigFactory.load()
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

  override implicit val actorSystem: ExtendedActorSystem =
    ActorSystem.apply("make-api", configuration).asInstanceOf[ExtendedActorSystem]

  // Wait until cluster is connected before initializing clustered actors
  while (Cluster(actorSystem).state.leader.isEmpty) {
    Thread.sleep(100)
  }

  val databaseConfiguration = actorSystem.registerExtension(DatabaseConfiguration)
  val guardian = actorSystem.actorOf(MakeGuardian.props(makeApi = this), MakeGuardian.name)

  Await.result(guardian ? Ping, atMost = 5.seconds)

  actorSystem.systemActorOf(ClusterShardingMonitor.props, ClusterShardingMonitor.name)
  actorSystem.systemActorOf(MemoryMonitoringActor.props, MemoryMonitoringActor.name)
  val threadPoolMonitor = actorSystem.systemActorOf(ThreadPoolMonitoringActor.props, ThreadPoolMonitoringActor.name)
  threadPoolMonitor ! MonitorThreadPool(databaseConfiguration.readThreadPool, "db-read-pool")
  threadPoolMonitor ! MonitorThreadPool(databaseConfiguration.writeThreadPool, "db-write-pool")

  // Start the shards
  (0 until 100).foreach { i =>
    proposalCoordinator ! StartShard(i.toString)
    userHistoryCoordinator ! StartShard(i.toString)
    sessionHistoryCoordinator ! StartShard(i.toString)
    sequenceCoordinator ! StartShard(i.toString)
  }

  // Initialize journals
  actorSystem.actorOf(ShardedProposal.props(sessionHistoryCoordinator), "fake-proposal") ! PoisonPill
  actorSystem.actorOf(ShardedUserHistory.props, "fake-user") ! PoisonPill
  actorSystem.actorOf(ShardedSessionHistory.props(userHistoryCoordinator), "fake-session") ! PoisonPill
  actorSystem.actorOf(ShardedSequence.props(DateHelper), "fake-sequence") ! PoisonPill

  // Ensure database stuff is initialized
  Await.result(userService.getUserByEmail("admin@make.org"), atMost = 20.seconds)

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

  val migrations: Seq[Migration] =
    Seq(
      VffOperation,
      ClimatParisOperation,
      LpaeOperation,
      MVEOperation,
      MakeEuropeOperation,
      ChanceAuxJeunesOperation,
      CultureOperation,
      CreateQuestions,
      CoreData,
      VffData,
      VffITData,
      VffGBData,
      ClimatParisData,
      LpaeData,
      MVEData,
      MakeEuropeData,
      ChanceAuxJeunesData,
//      Removed CultureData as proposals created by this migration are created several times for no apparent reasons
//      CultureData,
      CultureImportTagsData
    )
  val migrationsToRun = migrations.filter(_.runInProduction || settings.Dev.environmentType == "dynamic")

  // Run migrations sequentially
  sequentially(migrationsToRun) { migration =>
    migration.initialize(this).flatMap(_ => migration.migrate(this))
  }

}
