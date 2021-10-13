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

class KafkaConfiguration(override protected val configuration: Config) extends Extension with ConfigurationSupport {

  val connectionString: String = configuration.getString("connection-string")
  def topic(name: String): String = configuration.getString(s"topics.$name")

  val pollTimeout: Long = configuration.getLong("poll-timeout")
  val schemaRegistry: String = configuration.getString("schema-registry")

}

object KafkaConfiguration extends ExtensionId[KafkaConfiguration] {
  override def createExtension(system: ActorSystem[_]): KafkaConfiguration =
    new KafkaConfiguration(system.settings.config.getConfig("make-api.kafka"))
}

trait KafkaConfigurationExtension { this: Actor =>
  val kafkaConfiguration: KafkaConfiguration = KafkaConfiguration(context.system.toTyped)
}

trait KafkaConfigurationComponent {
  def kafkaConfiguration: KafkaConfiguration
}

trait DefaultKafkaConfigurationComponent extends KafkaConfigurationComponent { this: ActorSystemTypedComponent =>
  override lazy val kafkaConfiguration: KafkaConfiguration = KafkaConfiguration(actorSystemTyped)
}
