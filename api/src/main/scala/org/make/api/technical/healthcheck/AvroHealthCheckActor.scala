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
