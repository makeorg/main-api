package org.make.api.extensions

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import org.make.api.ActorSystemComponent
import org.make.api.Predef._

import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}

class MakeSettings(config: Config) extends Extension {

  val passivateTimeout: Duration = Duration(config.getString("passivate-timeout"))

  object SessionCookie {
    val lifetime: Duration = Duration(config.getString("cookie-session.lifetime"))
    val name: String = "make-secure"
    val isSecure: Boolean = config.getBoolean("cookie-session.is-secure")
  }

  object Oauth {
    val accessTokenLifetime: Int = config.getInt("oauth.access-token-lifetime")
    val refreshTokenLifetime: Int = config.getInt("oauth.refresh-token-lifetime")
  }

  val useEmbeddedElasticSearch: Boolean =
    if (config.hasPath("dev.embedded-elasticsearch")) {
      config.getBoolean("dev.embedded-elasticsearch")
    } else {
      false
    }

  val sendTestData: Boolean =
    if (config.hasPath("dev.send-test-data")) {
      config.getBoolean("dev.send-test-data")
    } else {
      false
    }

  object Http {
    val host: String = config.getString("http.host")
    val port: Int = config.getInt("http.port")
    val ssl: Boolean = config.getBoolean("http.ssl")
  }

  val frontUrl: String = config.getString("front-url")
  val newsletterUrl: String = config.getString("newsletter-url")
  val authorizedCorsUri: Seq[String] =
    config.getStringList("authorized-cors-uri").asScala

  object Cluster {
    val name: String = config.getString("cluster.name")

    object Consul {
      val httpUrl: String = config.getString("cluster.consul.http-url")
    }

    val heartbeatInterval: FiniteDuration = {
      config.getDuration("cluster.heartbeat-interval").toScala
    }
    val sessionTimeout: FiniteDuration = {
      config.getDuration("cluster.session-timeout").toScala
    }
    val sessionRenewInterval: FiniteDuration = {
      config.getDuration("cluster.session-renew-interval").toScala
    }
    val retriesBeforeSeeding: Int = {
      config.getInt("cluster.retries-before-seeding")
    }
    val nodeTimeout: FiniteDuration = {
      config.getDuration("cluster.node-timeout").toScala
    }
    val cleanupInterval: FiniteDuration = {
      config.getDuration("cluster.cleanup-interval").toScala
    }

  }

  object Authentication {
    val defaultClientId: String = config.getString("authentication.default-client-id")

    val defaultClientSecret: String = config.getString("authentication.default-client-secret")
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
