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
import org.make.api.ActorSystemComponent

class MailJetConfiguration(config: Config) extends Extension {
  val url: String = config.getString("url")
  val apiKey: String = config.getString("api-key")
  val secretKey: String = config.getString("secret-key")
  val basicAuthLogin: String = config.getString("basic-auth-login")
  val basicAuthPassword: String = config.getString("basic-auth-password")
  val campaignApiKey: String = config.getString("campaign-api-key")
  val campaignSecretKey: String = config.getString("campaign-secret-key")

  val hardBounceListId: String = config.getString("user-list.hard-bounce-list-id")
  val unsubscribeListId: String = config.getString("user-list.unsubscribe-list-id")
  val optInListId: String = config.getString("user-list.opt-in-list-id")
  val userListBatchSize: Int = config.getInt("user-list.batch-size")
  val httpBufferSize: Int = config.getInt("http-buffer-size")

  val errorReportingRecipient: String = config.getString("error-reporting.recipient")
  val errorReportingRecipientName: String = config.getString("error-reporting.recipient-name")
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
  override val mailJetConfiguration: MailJetConfiguration = MailJetConfiguration(context.system)
}

trait DefaultMailJetConfigurationComponent extends MailJetConfigurationComponent { this: ActorSystemComponent =>
  override lazy val mailJetConfiguration: MailJetConfiguration = MailJetConfiguration(actorSystem)
}
