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
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.typesafe.config.Config
import org.make.api.ActorSystemTypedComponent
import org.make.api.extensions.MakeSettings.DefaultAdmin

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.jdk.CollectionConverters._

class MakeSettings(config: Config) extends Extension {
  val passivateTimeout: Duration = Duration(config.getString("passivate-timeout"))
  val maxUserHistoryEvents: Int = config.getInt("max-user-history-events")
  val mandatoryConnection: Boolean = config.getBoolean("mandatory-connection")
  val defaultAdmin: DefaultAdmin = DefaultAdmin(
    firstName = config.getString("default-admin.first-name"),
    email = config.getString("default-admin.email"),
    password = config.getString("default-admin.password")
  )
  val defaultUserAnonymousParticipation: Boolean = config.getBoolean("default-user-anonymous-participation")
  val lockDuration: FiniteDuration =
    FiniteDuration(Duration(config.getString("lock-duration")).toMillis, TimeUnit.MILLISECONDS)
  val maxHistoryProposalsPerPage: Int = config.getInt("max-history-proposals-per-page")
  val validationTokenExpiresIn: Duration = Duration(config.getString("user-token.validation-token-expires-in"))
  val resetTokenExpiresIn: Duration = Duration(config.getString("user-token.reset-token-expires-in"))
  val resetTokenB2BExpiresIn: Duration = Duration(config.getString("user-token.reset-token-b2b-expires-in"))

  object SessionCookie {
    val lifetime: Duration = Duration(config.getString("cookie-session.lifetime"))
    val name: String = config.getString("cookie-session.name")
    val expirationName: String = config.getString("cookie-session.expiration-name")
    val isSecure: Boolean = config.getBoolean("cookie-session.is-secure")
    val domain: String = config.getString("cookie-session.domain")
  }

  object SecureCookie {
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

  val environment: String = config.getString("environment")
}

object MakeSettings extends ExtensionId[MakeSettings] {
  override def createExtension(system: ActorSystem[_]): MakeSettings =
    new MakeSettings(system.settings.config.getConfig("make-api"))

  final case class DefaultAdmin(firstName: String, email: String, password: String) {
    override def toString: String = s"default admin account ($email)"
  }
}

trait MakeSettingsExtension { self: Actor =>
  val settings: MakeSettings = MakeSettings(context.system.toTyped)
}

trait MakeSettingsComponent {
  def makeSettings: MakeSettings
}

trait DefaultMakeSettingsComponent extends MakeSettingsComponent {
  self: ActorSystemTypedComponent =>

  override lazy val makeSettings: MakeSettings = MakeSettings(actorSystemTyped)
}
