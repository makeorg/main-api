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

package org.make.api.technical.webflow

import java.net.URL

import com.typesafe.config.Config
import org.make.api.ActorSystemComponent

class WebflowConfiguration(config: Config) {
  val apiUrl: URL = new URL(config.getString("api-url"))
  val token: String = config.getString("token")
  val httpBufferSize: Int = config.getInt("http-buffer-size")
  val blogUrl: URL = new URL(config.getString("blog-url"))
  val rateLimitPerMinute: Int = config.getInt("rate-limit-per-minute")
  val collectionsIds: Map[String, String] = Map("posts" -> config.getString("collections-ids.posts"))
}

trait WebflowConfigurationComponent {
  def webflowConfiguration: WebflowConfiguration
}

trait DefaultWebflowConfigurationComponent extends WebflowConfigurationComponent { this: ActorSystemComponent =>
  override lazy val webflowConfiguration: WebflowConfiguration =
    new WebflowConfiguration(actorSystem.settings.config.getConfig("make-api.webflow"))
}
