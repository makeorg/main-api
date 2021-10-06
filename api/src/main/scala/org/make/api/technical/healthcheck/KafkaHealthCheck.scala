/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

import akka.util.Timeout
import org.apache.kafka.clients.admin.{AdminClient, DescribeClusterOptions}
import org.apache.kafka.clients.producer.ProducerConfig
import org.make.api.extensions.KafkaConfiguration
import org.make.api.technical.TimeSettings
import org.make.api.technical.healthcheck.HealthCheck.Status

import java.util.Properties
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Using}

class KafkaHealthCheck(kafkaConfiguration: KafkaConfiguration) extends HealthCheck {

  override val techno: String = "kafka"

  val timeout: Timeout = TimeSettings.defaultTimeout

  private def createClient() = {
    val properties = new Properties
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    AdminClient.create(properties)
  }

  override def healthCheck()(implicit ctx: ExecutionContext): Future[Status] = {
    Future {
      Using(createClient()) { client =>
        client
          .describeCluster(new DescribeClusterOptions().timeoutMs(kafkaConfiguration.pollTimeout.toInt))
          .clusterId()
          .get(timeout.duration.length, timeout.duration.unit)
      } match {
        case Success(_) => Status.OK
        case Failure(e) => Status.NOK(Some(e.getMessage))
      }
    }
  }
}
