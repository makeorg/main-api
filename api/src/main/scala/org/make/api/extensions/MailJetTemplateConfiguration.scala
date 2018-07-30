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
import org.make.core.reference.{Country, Language}

class MailJetTemplateConfiguration(config: Config) extends Extension with ConfigurationSupport {
  val from: String = config.getString("from")
  val fromName: String = config.getString("from-name")

  override protected def configuration: Config = config

  def getFrontUrl(): String = {
    config.getString("front-url")
  }

  def registration(operation: String, country: Country, language: Language): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("registration"), operation, country, language)
  def welcome(operation: String, country: Country, language: Language): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("welcome"), operation, country, language)
  def resendAccountValidationLink(operation: String, country: Country, language: Language): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("resend-validation-link"), operation, country, language)
  def forgottenPassword(operation: String, country: Country, language: Language): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("forgotten-password"), operation, country, language)
  def proposalRefused(operation: String,
                      country: Country,
                      language: Language,
                      isOrganisation: Boolean = false): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("proposal-refused"), operation, country, language, isOrganisation)
  def proposalAccepted(operation: String,
                       country: Country,
                       language: Language,
                       isOrganisation: Boolean = false): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("proposal-accepted"), operation, country, language, isOrganisation)

  private def parseTemplateConfiguration(config: Config,
                                         operation: String,
                                         country: Country,
                                         language: Language,
                                         isOrganisation: Boolean = false): TemplateConfiguration = {

    var templateConfiguration: Config = config

    if (config.hasPath(operation)) {
      templateConfiguration = config.getConfig(operation).withFallback(templateConfiguration)
    }

    if (config.hasPath(country.value)) {
      templateConfiguration = config.getConfig(country.value).withFallback(templateConfiguration)
    }

    if (config.hasPath(s"${country.value}.${language.value}")) {
      templateConfiguration =
        config.getConfig(s"${country.value}.${language.value}").withFallback(templateConfiguration)
    }

    if (config.hasPath(s"$operation.${country.value}")) {
      templateConfiguration = config.getConfig(s"$operation.${country.value}").withFallback(templateConfiguration)
    }

    if (config.hasPath(s"$operation.${country.value}.${language.value}")) {
      templateConfiguration =
        config.getConfig(s"$operation.${country.value}.${language.value}").withFallback(templateConfiguration)
    }

    if (isOrganisation) {
      if (config.hasPath("organisation")) {
        templateConfiguration = config.getConfig("organisation").withFallback(templateConfiguration)
      }

      if (config.hasPath(s"organisation.${country.value}")) {
        templateConfiguration = config.getConfig(s"organisation.${country.value}").withFallback(templateConfiguration)
      }

      if (config.hasPath(s"organisation.${country.value}.${language.value}")) {
        templateConfiguration =
          config.getConfig(s"organisation.${country.value}.${language.value}").withFallback(templateConfiguration)
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
