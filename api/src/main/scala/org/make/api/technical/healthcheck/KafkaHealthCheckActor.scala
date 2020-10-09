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

import java.util.Properties

import akka.actor.Props
import org.apache.kafka.clients.admin.{AdminClient, DescribeClusterOptions}
import org.apache.kafka.clients.producer.ProducerConfig
import org.make.api.extensions.KafkaConfigurationExtension

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Using
import scala.util.Success
import scala.util.Failure

class KafkaHealthCheckActor extends HealthCheck with KafkaConfigurationExtension {

  override val techno: String = "kafka"

  private def createClient() = {
    val properties = new Properties
    properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfiguration.connectionString)
    AdminClient.create(properties)
  }

  override def healthCheck(): Future[String] = {

    val promise = Promise[String]()

    Using(createClient()) { client =>
      client
        .describeCluster(new DescribeClusterOptions().timeoutMs(kafkaConfiguration.pollTimeout.toInt))
        .clusterId()
        .whenComplete((_, throwable) => {
          if (throwable != null) {
            promise.failure(throwable)
          } else {
            promise.success("OK")
          }
        })

      promise.future
    } match {
      case Success(result) => result
      case Failure(e)      => Future.failed(e)
    }

  }
}

object KafkaHealthCheckActor extends HealthCheckActorDefinition {

  override val name: String = "kafka-health-check"

  override def props(healthCheckExecutionContext: ExecutionContext): Props = Props(new KafkaHealthCheckActor)

}
