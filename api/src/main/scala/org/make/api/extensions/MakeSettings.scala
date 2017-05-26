package org.make.api.extensions

import akka.actor.{Actor, ActorSystem, Extension}
import com.typesafe.config.Config
import org.make.api.Predef._

import scala.concurrent.duration.{Duration, FiniteDuration}

class MakeSettings(config: Config) extends Extension {

  val passivateTimeout: Duration = Duration(
    config.getString("passivate-timeout")
  )
  val useEmbeddedElasticSearch: Boolean =
    if (config.hasPath("dev.embedded-elasticsearch")) {
      config.getBoolean("dev.embedded-elasticsearch")
    }
    else {
      false
    }

  val sendTestData: Boolean =
    if (config.hasPath("dev.send-test-data")) {
      config.getBoolean("dev.send-test-data")
    }
    else {
      false
    }

  object http {
    val host: String = config.getString("http.host")
    val port: Int = config.getInt("http.port")
  }

  object cluster {
    val name: String = config.getString("cluster.name")

    object consul {
      val httpUrl: String = config.getString("cluster.consul.http-url")
    }

    val heartbeatInterval: FiniteDuration =
      config.getDuration("cluster.heartbeat-interval").toScala
    val sessionTimeout: FiniteDuration =
      config.getDuration("cluster.session-timeout").toScala
    val sessionRenewInterval: FiniteDuration =
      config.getDuration("cluster.session-renew-interval").toScala
    val retriesBeforeSeeding: Int =
      config.getInt("cluster.retries-before-seeding")
    val nodeTimeout: FiniteDuration =
      config.getDuration("cluster.node-timeout").toScala
    val cleanupInterval: FiniteDuration =
      config.getDuration("cluster.cleanup-interval").toScala

  }

}

object MakeSettings {
  def apply(system: ActorSystem) =
    new MakeSettings(system.settings.config.getConfig("make-api"))
}

trait MakeSettingsExtension { self: Actor =>

  val settings = MakeSettings(context.system)
}
