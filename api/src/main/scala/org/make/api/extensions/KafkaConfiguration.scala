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

class KafkaConfiguration(override protected val configuration: Config) extends Extension with ConfigurationSupport {

  val connectionString: String = configuration.getString("connection-string")
  val topics: Map[String, String] = Map(
    "users" -> configuration.getString("topics.users"),
    "emails" -> configuration.getString("topics.emails"),
    "proposals" -> configuration.getString("topics.proposals"),
    "mailjet-events" -> configuration.getString("topics.mailjet-events"),
    "duplicates-predicted" -> configuration.getString("topics.duplicates-predicted"),
    "tracking-events" -> configuration.getString("topics.tracking-events"),
    "ideas" -> configuration.getString("topics.ideas"),
    "predictions" -> configuration.getString("topics.predictions")
  )

  val pollTimeout: Long = configuration.getLong("poll-timeout")
  val schemaRegistry: String = configuration.getString("schema-registry")

}

object KafkaConfiguration extends ExtensionId[KafkaConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): KafkaConfiguration =
    new KafkaConfiguration(system.settings.config.getConfig("make-api.kafka"))

  override def lookup: ExtensionId[KafkaConfiguration] = KafkaConfiguration
}

trait KafkaConfigurationExtension { this: Actor =>
  val kafkaConfiguration: KafkaConfiguration = KafkaConfiguration(context.system)
}
