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

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration

class MakeSettings(config: Config) extends Extension {

  val passivateTimeout: Duration = Duration(config.getString("passivate-timeout"))
  val maxUserHistoryEvents: Int = config.getInt("max-user-history-events")

  object SessionCookie {
    val lifetime: Duration = Duration(config.getString("cookie-session.lifetime"))
    val name: String = "make-secure"
    val isSecure: Boolean = config.getBoolean("cookie-session.is-secure")
    val domain: String = config.getString("cookie-session.domain")
  }

  object VisitorCookie {
    val name: String = "make-visitor"
    val isSecure: Boolean = config.getBoolean("cookie-visitor.is-secure")
    val domain: String = config.getString("cookie-visitor.domain")
  }

  object Oauth {
    val accessTokenLifetime: Int = config.getInt("oauth.access-token-lifetime")
    val refreshTokenLifetime: Int = config.getInt("oauth.refresh-token-lifetime")
  }

  object Http {
    val host: String = config.getString("http.host")
    val port: Int = config.getInt("http.port")
    val ssl: Boolean = config.getBoolean("http.ssl")
  }

  val newsletterUrl: String = config.getString("newsletter-url")
  val authorizedCorsUri: Seq[String] =
    config.getStringList("authorized-cors-uri").asScala

  object Authentication {
    val defaultClientId: String = config.getString("authentication.default-client-id")

    val defaultClientSecret: String = config.getString("authentication.default-client-secret")
  }

  object Dev {
    val environmentType: String = config.getString("dev.environment-type")
  }

}
object MakeSettings extends ExtensionId[MakeSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): MakeSettings =
    new MakeSettings(system.settings.config.getConfig("make-api"))

  override def lookup(): ExtensionId[MakeSettings] = MakeSettings
  override def get(system: ActorSystem): MakeSettings = super.get(system)
}

trait MakeSettingsExtension { self: Actor =>

  val settings = MakeSettings(context.system)
}

trait MakeSettingsComponent {
  def makeSettings: MakeSettings
}

trait DefaultMakeSettingsComponent extends MakeSettingsComponent {
  self: ActorSystemComponent =>

  override lazy val makeSettings: MakeSettings = MakeSettings(actorSystem)
}
