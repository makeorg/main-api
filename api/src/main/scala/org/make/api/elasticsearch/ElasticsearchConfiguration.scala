package org.make.api.elasticsearch

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import org.make.api.ConfigurationSupport

class ElasticsearchConfiguration(override protected val configuration: Config) extends Extension with ConfigurationSupport {
  val host: String = configuration.getString("host")
  val port: String = configuration.getString("port")
}

object ElasticsearchConfiguration extends ExtensionId[ElasticsearchConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ElasticsearchConfiguration =
    new ElasticsearchConfiguration(system.settings.config.getConfig("make-api.elasticSearch"))

  override def lookup(): ExtensionId[ElasticsearchConfiguration] = ElasticsearchConfiguration
  override def get(system: ActorSystem): ElasticsearchConfiguration = super.get(system)
}

trait ElasticsearchConfigurationExtension { this: Actor =>
  val elasticsearchConfiguration: ElasticsearchConfiguration = ElasticsearchConfiguration(context.system)
}
