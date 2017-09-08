package org.make.api.extensions

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config

class MailJetTemplateConfiguration(config: Config) extends Extension {
  val from: String = config.getString("from")
  val fromName: String = config.getString("from-name")

  val userRegisteredTemplate: Int = config.getInt("registration-template-id")
  val resetPasswordTemplate: Int = config.getInt("reset-password-template-id")
  val resendValidationEmailTemplate: Int = config.getInt("resend-validation-email-template-id")
  val proposalValidatedTemplate: Int = config.getInt("proposal-validated-template-id")
  val proposalSentTemplate: Int = config.getInt("proposal-sent-template-id")
}

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
