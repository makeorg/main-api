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

import akka.actor.Props
import org.make.api.extensions.KafkaConfigurationExtension
import io.confluent.kafka.schemaregistry.client.rest.RestService

import scala.concurrent.{ExecutionContext, Future}

class AvroHealthCheckActor(healthCheckExecutionContext: ExecutionContext)
    extends HealthCheck
    with KafkaConfigurationExtension {

  override val techno: String = "avro"

  val _ = healthCheckExecutionContext

  override def healthCheck(): Future[String] = {
    val restService = new RestService(kafkaConfiguration.schemaRegistry)
    if (restService.getAllSubjects().size() > 0) {
      Future.successful("OK")
    } else {
      Future.successful("NOK")
    }
  }

}

object AvroHealthCheckActor extends HealthCheckActorDefinition {
  override val name: String = "avro-health-check"

  override def props(healthCheckExecutionContext: ExecutionContext): Props =
    Props(new AvroHealthCheckActor(healthCheckExecutionContext))
}
