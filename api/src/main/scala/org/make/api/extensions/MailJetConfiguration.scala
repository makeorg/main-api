package org.make.api.extensions

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config

class MailJetConfiguration(config: Config) extends Extension {
  val url: String = config.getString("url")
  val apiKey: String = config.getString("api-key")
  val secretKey: String = config.getString("secret-key")
  val basicAuthLogin: String = config.getString("basic-auth-login")
  val basicAuthPassword: String = config.getString("basic-auth-password")
  val campaignApiKey: String = config.getString("campaign-api-key")
  val campaignSecretKey: String = config.getString("campaign-secret-key")
}

object MailJetConfiguration extends ExtensionId[MailJetConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): MailJetConfiguration =
    new MailJetConfiguration(system.settings.config.getConfig("make-api.mail-jet"))

  override def lookup(): ExtensionId[MailJetConfiguration] = MailJetConfiguration
  override def get(system: ActorSystem): MailJetConfiguration = super.get(system)
}

trait MailJetConfigurationComponent {
  def mailJetConfiguration: MailJetConfiguration
}

trait MailJetConfigurationExtension extends MailJetConfigurationComponent { this: Actor =>
  val mailJetConfiguration: MailJetConfiguration = MailJetConfiguration(context.system)
}
