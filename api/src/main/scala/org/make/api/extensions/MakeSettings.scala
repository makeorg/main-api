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

import akka.actor.{Actor, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import org.make.api.ActorSystemComponent

import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

class MakeSettings(config: Config) extends Extension {

  val passivateTimeout: Duration = Duration(config.getString("passivate-timeout"))
  val maxUserHistoryEvents: Int = config.getInt("max-user-history-events")
  val mandatoryConnection: Boolean = config.getBoolean("mandatory-connection")
  val defaultUserAnonymousParticipation: Boolean = config.getBoolean("default-user-anonymous-participation")

  object SessionCookie {
    val lifetime: Duration = Duration(config.getString("cookie-session.lifetime"))
    val name: String = config.getString("cookie-session.name")
    val expirationName: String = config.getString("cookie-session.expiration-name")
    val isSecure: Boolean = config.getBoolean("cookie-session.is-secure")
    val domain: String = config.getString("cookie-session.domain")
  }

  object SecureCookie {
    val lifetime: Duration = Duration(config.getString("cookie-secure.lifetime"))
    val name: String = config.getString("cookie-secure.name")
    val expirationName: String = config.getString("cookie-secure.expiration-name")
    val isSecure: Boolean = config.getBoolean("cookie-secure.is-secure")
    val domain: String = config.getString("cookie-secure.domain")
  }

  object VisitorCookie {
    val name: String = config.getString("cookie-visitor.name")
    val createdAtName: String = config.getString("cookie-visitor.created-at-name")
    val isSecure: Boolean = config.getBoolean("cookie-visitor.is-secure")
    val domain: String = config.getString("cookie-visitor.domain")
  }

  object UserIdCookie {
    val name: String = config.getString("cookie-user-id.name")
    val isSecure: Boolean = config.getBoolean("cookie-user-id.is-secure")
    val domain: String = config.getString("cookie-user-id.domain")
  }

  object Oauth {
    val refreshTokenLifetime: Int = config.getInt("oauth.refresh-token-lifetime")
    val reconnectTokenLifetime: Int = config.getInt("oauth.reconnect-token-lifetime")
  }

  object Http {
    val host: String = config.getString("http.host")
    val port: Int = config.getInt("http.port")
    val ssl: Boolean = config.getBoolean("http.ssl")
  }

  val newsletterUrl: String = config.getString("newsletter-url")
  val authorizedCorsUri: Seq[String] =
    config.getStringList("authorized-cors-uri").asScala.toSeq

  object Authentication {
    val defaultClientId: String = config.getString("authentication.default-client-id")

    val defaultClientSecret: String = config.getString("authentication.default-client-secret")
  }

  object Dev {
    val environmentType: String = config.getString("dev.environment-type")
  }

  val environment: String = config.getString("environment")
}
object MakeSettings extends ExtensionId[MakeSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): MakeSettings =
    new MakeSettings(system.settings.config.getConfig("make-api"))

  override def lookup(): ExtensionId[MakeSettings] = MakeSettings
}

trait MakeSettingsExtension { self: Actor =>

  val settings: MakeSettings = MakeSettings(context.system)
}

trait MakeSettingsComponent {
  def makeSettings: MakeSettings
}

trait DefaultMakeSettingsComponent extends MakeSettingsComponent {
  self: ActorSystemComponent =>

  override lazy val makeSettings: MakeSettings = MakeSettings(actorSystem)
}
