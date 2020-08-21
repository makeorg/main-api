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

package org.make.api.user.social

import com.typesafe.config.Config
import org.make.api.ConfigComponent

trait SocialProvidersConfiguration {
  def google: GoogleConfiguration
}

trait GoogleConfiguration {
  def apiKey: String
  def clientId: String
  def scopes: String
}

trait SocialProvidersConfigurationComponent {
  def socialProvidersConfiguration: SocialProvidersConfiguration
}

trait DefaultSocialProvidersConfigurationComponent extends SocialProvidersConfigurationComponent {
  self: ConfigComponent =>

  override lazy val socialProvidersConfiguration: SocialProvidersConfiguration =
    new DefaultSocialProvidersConfiguration(config.getConfig("make-api.social-providers"))

  class DefaultSocialProvidersConfiguration(socialConfig: Config) extends SocialProvidersConfiguration {
    override val google = new DefaultGoogleConfiguration(socialConfig.getConfig("google"))
  }

  class DefaultGoogleConfiguration(googleConfig: Config) extends GoogleConfiguration {
    override val apiKey: String = googleConfig.getString("api-key")
    override val clientId: String = googleConfig.getString("client-id")
    override val scopes: String = googleConfig.getString("scopes")
  }
}
