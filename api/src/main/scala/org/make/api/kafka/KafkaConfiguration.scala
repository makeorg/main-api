package org.make.api.kafka

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import org.make.api.ConfigurationSupport


class KafkaConfiguration(override protected val configuration: Config) extends Extension with ConfigurationSupport {

  val connectionString: String = configuration.getString("connection-string")
  val topic: String = configuration.getString("topic")
  val pollTimeout: Long = configuration.getLong("poll-timeout")
  val schemaRegistry: String = configuration.getString("schema-registry")

}

object KafkaConfiguration extends ExtensionId[KafkaConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): KafkaConfiguration =
    new KafkaConfiguration(system.settings.config.getConfig("make-api.kafka"))

  override def lookup(): ExtensionId[KafkaConfiguration] = KafkaConfiguration
  override def get(system: ActorSystem): KafkaConfiguration = super.get(system)
}

trait KafkaConfigurationExtension { this: Actor =>
  val kafkaConfiguration: KafkaConfiguration = KafkaConfiguration(context.system)
}