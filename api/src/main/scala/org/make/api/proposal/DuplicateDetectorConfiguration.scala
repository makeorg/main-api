package org.make.api.proposal

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import org.make.api.extensions.ConfigurationSupport

class DuplicateDetectorConfiguration(override protected val configuration: Config)
    extends Extension
    with ConfigurationSupport {

  val maxResults: Int = configuration.getInt("max-results")
}

object DuplicateDetectorConfiguration extends ExtensionId[DuplicateDetectorConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): DuplicateDetectorConfiguration =
    new DuplicateDetectorConfiguration(system.settings.config.getConfig("make-api.duplicate-detector"))

  override def lookup(): ExtensionId[DuplicateDetectorConfiguration] =
    DuplicateDetectorConfiguration

  override def get(system: ActorSystem): DuplicateDetectorConfiguration = super.get(system)
}

trait DuplicateDetectorConfigurationExtension extends DuplicateDetectorConfigurationComponent { this: Actor =>
  override val duplicateDetectorConfiguration: DuplicateDetectorConfiguration =
    DuplicateDetectorConfiguration(context.system)
}

trait DuplicateDetectorConfigurationComponent {
  def duplicateDetectorConfiguration: DuplicateDetectorConfiguration
}
