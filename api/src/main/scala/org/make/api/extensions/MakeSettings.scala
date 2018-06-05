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
  }

  object VisitorCookie {
    val name: String = "make-visitor"
    val isSecure: Boolean = config.getBoolean("cookie-visitor.is-secure")
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
