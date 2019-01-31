package org.make.api.technical.security

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import org.make.api.ActorSystemComponent

class SecurityConfiguration(config: Config) extends Extension {
  val secureHashSalt: String = config.getString("secure-hash-salt")
}

object SecurityConfiguration extends ExtensionId[SecurityConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): SecurityConfiguration =
    new SecurityConfiguration(system.settings.config.getConfig("make-api.security"))

  override def lookup(): ExtensionId[SecurityConfiguration] = SecurityConfiguration
  override def get(system: ActorSystem): SecurityConfiguration = super.get(system)
}

trait SecurityConfigurationComponent {
  def securityConfiguration: SecurityConfiguration
}

trait SecurityConfigurationExtension extends SecurityConfigurationComponent { this: Actor =>
  override val securityConfiguration: SecurityConfiguration = SecurityConfiguration(context.system)
}

trait DefaultSecurityConfigurationComponent extends SecurityConfigurationComponent {
  this: ActorSystemComponent =>
  override lazy val securityConfiguration: SecurityConfiguration = SecurityConfiguration(actorSystem)
}
