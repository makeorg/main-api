package org.make.api.extensions

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config

class MailJetTemplateConfiguration(config: Config) extends Extension {
  val from: String = config.getString("from")
  val fromName: String = config.getString("from-name")

  def registration(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("registration"), operation, country, language)
  def welcome(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("welcome"), operation, country, language)
  def resendAccountValidationLink(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("resend-validation-link"), operation, country, language)
  def forgottenPassword(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("forgotten-password"), operation, country, language)
  def proposalRefused(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("proposal-refused"), operation, country, language)
  def proposalAccepted(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("proposal-accepted"), operation, country, language)

  private def parseTemplateConfiguration(config: Config,
                                         operation: String,
                                         country: String,
                                         language: String): TemplateConfiguration = {

    var defaultedConfig: Config = config

    if (config.hasPath(operation)) {
      defaultedConfig = config.getConfig(operation).withFallback(defaultedConfig)
    }
    if (config.hasPath(s"$operation.$country")) {
      defaultedConfig = config.getConfig(s"$operation.$country").withFallback(defaultedConfig)
    }
    if (config.hasPath(s"$operation.$country.$language")) {
      defaultedConfig = config.getConfig(s"$operation.$country.$language").withFallback(defaultedConfig)
    }

    TemplateConfiguration(
      templateId = defaultedConfig.getInt("template-id"),
      customCampaign = defaultedConfig.getString("custom-campaign"),
      monitoringCategory = defaultedConfig.getString("monitoring-category")
    )
  }
}

case class TemplateConfiguration(templateId: Int, customCampaign: String, monitoringCategory: String)

object MailJetTemplateConfiguration extends ExtensionId[MailJetTemplateConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): MailJetTemplateConfiguration =
    new MailJetTemplateConfiguration(system.settings.config.getConfig("make-api.mail-jet.templates"))

  override def lookup(): ExtensionId[MailJetTemplateConfiguration] = MailJetTemplateConfiguration
  override def get(system: ActorSystem): MailJetTemplateConfiguration = super.get(system)
}

trait MailJetTemplateConfigurationComponent {
  def mailJetTemplateConfiguration: MailJetTemplateConfiguration
}

trait MailJetTemplateConfigurationExtension extends MailJetTemplateConfigurationComponent { this: Actor =>
  val mailJetTemplateConfiguration: MailJetTemplateConfiguration = MailJetTemplateConfiguration(context.system)
}
