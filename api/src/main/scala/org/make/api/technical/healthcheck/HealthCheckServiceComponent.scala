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

package org.make.api.technical.healthcheck

import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import org.make.api.ConfigComponent
import org.make.api.extensions.{KafkaConfigurationComponent, MakeSettingsComponent}
import org.make.api.technical.BaseReadJournalComponent
import org.make.api.technical.ExecutorServiceHelper.RichExecutorService
import org.make.api.technical.elasticsearch.{ElasticsearchClientComponent, ElasticsearchConfigurationComponent}
import org.make.api.technical.healthcheck.HealthCheck.HealthCheckResponse
import org.make.api.technical.storage.SwiftClientComponent

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

trait HealthCheckService {
  def runAllHealthChecks(): Future[Seq[HealthCheckResponse]]
}

trait HealthCheckServiceComponent {
  def healthCheckService: HealthCheckService
}

trait DefaultHealthCheckServiceComponent extends HealthCheckServiceComponent {
  self: BaseReadJournalComponent[CassandraReadJournal]
    with ConfigComponent
    with ElasticsearchClientComponent
    with ElasticsearchConfigurationComponent
    with KafkaConfigurationComponent
    with MakeSettingsComponent
    with SwiftClientComponent =>

  override lazy val healthCheckService: HealthCheckService = new DefaultHealthCheckService

  class DefaultHealthCheckService extends HealthCheckService {
    implicit val ctx: ExecutionContext =
      Executors.newFixedThreadPool(10).instrument("healthchecks").toExecutionContext

    val healthChecks: Seq[(String, HealthCheck)] = Seq(
      "avro-health-check" -> new AvroHealthCheck(kafkaConfiguration),
      "cassandra-health-check" -> new CassandraHealthCheck(proposalJournal, config),
      "cockroach-health-check" -> new CockroachHealthCheck(makeSettings.defaultAdmin.email),
      "elasticsearch-health-check" -> new ElasticsearchHealthCheck(elasticsearchConfiguration, elasticsearchClient),
      "kafka-health-check" -> new KafkaHealthCheck(kafkaConfiguration),
      "swift-health-check" -> new SwiftHealthCheck(swiftClient),
      "zookeeper-health-check" -> new ZookeeperHealthCheck(config)
    )

    override def runAllHealthChecks(): Future[Seq[HealthCheckResponse]] = {
      Future.traverse(healthChecks) {
        case (name, hc) =>
          hc.healthCheck().map(status => HealthCheckResponse(name, status.message)).recoverWith {
            case e => Future.successful(HealthCheckResponse(name, e.getMessage))
          }
      }
    }
  }
}
