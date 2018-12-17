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

package org.make.api.technical.elasticsearch

import akka.actor.Extension
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.extensions.ConfigurationSupport

class ElasticsearchConfiguration(override protected val configuration: Config)
    extends Extension
    with ConfigurationSupport
    with StrictLogging {

  lazy val connectionString: String = configuration.getString("connection-string")
  lazy val indexName: String = configuration.getString("index-name")
  lazy val entityBufferSize: Int = configuration.getInt("buffer-size")
  lazy val entityBulkSize: Int = configuration.getInt("bulk-size")

  lazy val ideaAliasName: String = configuration.getString("idea-alias-name")
  lazy val proposalAliasName: String = configuration.getString("proposal-alias-name")
  lazy val organisationAliasName: String = configuration.getString("organisation-alias-name")
}

trait ElasticsearchConfigurationComponent {
  def elasticsearchConfiguration: ElasticsearchConfiguration
}

trait DefaultElasticsearchConfigurationComponent extends ElasticsearchConfigurationComponent {
  this: ActorSystemComponent =>

  override lazy val elasticsearchConfiguration: ElasticsearchConfiguration =
    new ElasticsearchConfiguration(actorSystem.settings.config.getConfig("make-api.elasticSearch"))
}
