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

  def getLegacyFrontendUrl(): String = {
    config.getString("front-url")
  }

  def getMainFrontendUrl(): String = {
    config.getString("front-main-url")
  }

  def registration(questionSlug: String, country: Country, language: Language): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("registration"), questionSlug, country, language)
  def welcome(questionSlug: String, country: Country, language: Language): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("welcome"), questionSlug, country, language)
  def resendAccountValidationLink(questionSlug: String, country: Country, language: Language): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("resend-validation-link"), questionSlug, country, language)
  def forgottenPassword(questionSlug: String, country: Country, language: Language): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("forgotten-password"), questionSlug, country, language)
  def organisationInitialization(questionSlug: String, country: Country, language: Language): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("organisation-initialization"), questionSlug, country, language)
  def proposalRefused(questionSlug: String,
                      country: Country,
                      language: Language,
                      isOrganisation: Boolean = false): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("proposal-refused"), questionSlug, country, language, isOrganisation)
  def proposalAccepted(questionSlug: String,
                       country: Country,
                       language: Language,
                       isOrganisation: Boolean = false): TemplateConfiguration =
    parseTemplateConfiguration(config.getConfig("proposal-accepted"), questionSlug, country, language, isOrganisation)

  private def parseTemplateConfiguration(config: Config,
                                         questionSlug: String,
                                         country: Country,
                                         language: Language,
                                         isOrganisation: Boolean = false): TemplateConfiguration = {

    var templateConfiguration: Config = config

    if (config.hasPath(country.value)) {
      templateConfiguration = config.getConfig(country.value).withFallback(templateConfiguration)
    }

    if (config.hasPath(s"${country.value}.${language.value}")) {
      templateConfiguration =
        config.getConfig(s"${country.value}.${language.value}").withFallback(templateConfiguration)
    }

    if (config.hasPath(questionSlug)) {
      templateConfiguration = config.getConfig(questionSlug).withFallback(templateConfiguration)
    }

    if (config.hasPath(s"$questionSlug.${country.value}")) {
      templateConfiguration = config.getConfig(s"$questionSlug.${country.value}").withFallback(templateConfiguration)
    }

    if (config.hasPath(s"$questionSlug.${country.value}.${language.value}")) {
      templateConfiguration =
        config.getConfig(s"$questionSlug.${country.value}.${language.value}").withFallback(templateConfiguration)
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
