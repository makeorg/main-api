/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
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

package org.make.api.extensions

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config

class MailJetTemplateConfiguration(config: Config) extends Extension with ConfigurationSupport {
  val from: String = config.getString("from")
  val fromName: String = config.getString("from-name")

  override protected def configuration: Config = config

  def getFrontUrl(): String = {
    config.getString("front-url")
  }

  def registration(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("registration"), operation, country, language)
  def welcome(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("welcome"), operation, country, language)
  def resendAccountValidationLink(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("resend-validation-link"), operation, country, language)
  def forgottenPassword(operation: String, country: String, language: String): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("forgotten-password"), operation, country, language)
  def proposalRefused(operation: String,
                      country: String,
                      language: String,
                      isOrganisation: Boolean = false): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("proposal-refused"), operation, country, language, isOrganisation)
  def proposalAccepted(operation: String,
                       country: String,
                       language: String,
                       isOrganisation: Boolean = false): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("proposal-accepted"), operation, country, language, isOrganisation)

  private def parseTemplateConfiguration(config: Config,
                                         operation: String,
                                         country: String,
                                         language: String,
                                         isOrganisation: Boolean = false): TemplateConfiguration = {

    var templateConfiguration: Config = config

    if (config.hasPath(operation)) {
      templateConfiguration = config.getConfig(operation).withFallback(templateConfiguration)
    }

    if (config.hasPath(s"$country")) {
      templateConfiguration = config.getConfig(s"$country").withFallback(templateConfiguration)
    }

    if (config.hasPath(s"$country.$language")) {
      templateConfiguration = config.getConfig(s"$country.$language").withFallback(templateConfiguration)
    }

    if (config.hasPath(s"$operation.$country")) {
      templateConfiguration = config.getConfig(s"$operation.$country").withFallback(templateConfiguration)
    }

    if (config.hasPath(s"$operation.$country.$language")) {
      templateConfiguration = config.getConfig(s"$operation.$country.$language").withFallback(templateConfiguration)
    }

    if (isOrganisation) {
      if (config.hasPath("organisation")) {
        templateConfiguration = config.getConfig("organisation").withFallback(templateConfiguration)
      }

      if (config.hasPath(s"organisation.$country")) {
        templateConfiguration = config.getConfig(s"organisation.$country").withFallback(templateConfiguration)
      }

      if (config.hasPath(s"organisation.$country.$language")) {
        templateConfiguration = config.getConfig(s"organisation.$country.$language").withFallback(templateConfiguration)
      }
    }

    TemplateConfiguration(
      templateId = templateConfiguration.getInt("template-id"),
      customCampaign = optionalString("custom-campaign"),
      monitoringCategory = optionalString("monitoring-category"),
      enabled = templateConfiguration.getBoolean("enabled")
    )
  }
}

case class TemplateConfiguration(templateId: Int,
                                 customCampaign: Option[String],
                                 monitoringCategory: Option[String],
                                 enabled: Boolean)

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
