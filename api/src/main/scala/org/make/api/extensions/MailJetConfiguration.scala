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

import akka.actor.Actor
import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.typesafe.config.Config
import org.make.api.ActorSystemTypedComponent

import scala.concurrent.duration.{DurationInt, FiniteDuration}

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
  val csvDirectory: String = config.getString("user-list.csv-directory")
  val csvSize: Int = config.getInt("user-list.csv-bytes-size")
  def tickInterval: FiniteDuration = 10.seconds
  def delayBeforeResend: FiniteDuration = 15.seconds
}

object MailJetConfiguration extends ExtensionId[MailJetConfiguration] {
  override def createExtension(system: ActorSystem[_]): MailJetConfiguration =
    new MailJetConfiguration(system.settings.config.getConfig("make-api.mail-jet"))
}

trait MailJetConfigurationComponent {
  def mailJetConfiguration: MailJetConfiguration
}

trait MailJetConfigurationExtension extends MailJetConfigurationComponent { this: Actor =>
  override val mailJetConfiguration: MailJetConfiguration =
    new MailJetConfiguration(context.system.settings.config.getConfig("make-api.mail-jet"))
}

trait DefaultMailJetConfigurationComponent extends MailJetConfigurationComponent { this: ActorSystemTypedComponent =>
  override lazy val mailJetConfiguration: MailJetConfiguration = MailJetConfiguration(actorSystemTyped)
}
