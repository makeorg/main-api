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

import akka.actor.Extension
import com.typesafe.config.Config
import org.make.api.ActorSystemComponent

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

}

trait MailJetTemplateConfigurationComponent {
  def mailJetTemplateConfiguration: MailJetTemplateConfiguration
}

trait DefaultMailJetTemplateConfigurationComponent extends MailJetTemplateConfigurationComponent {
  this: ActorSystemComponent =>
  override lazy val mailJetTemplateConfiguration: MailJetTemplateConfiguration =
    new MailJetTemplateConfiguration(actorSystem.settings.config.getConfig("make-api.mail-jet.templates"))

}
