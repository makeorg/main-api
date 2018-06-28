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

import java.security.MessageDigest
import java.time.format.DateTimeFormatter

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.sksamuel.elastic4s.ElasticsearchClientUri
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.http.index.admin.AliasExistsResponse
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.ConfigurationSupport
import org.make.core.DateHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.{Codec, Source}
import scala.util.{Failure, Success}

class ElasticsearchConfiguration(override protected val configuration: Config)
    extends Extension
    with ConfigurationSupport
    with StrictLogging {

  val connectionString: String = configuration.getString("connection-string")
  val indexName: String = configuration.getString("index-name")
  val aliasName: String = configuration.getString("alias-name")

  // create index
  val elasticsearchMapping: String = Source.fromResource("elasticsearch-mapping.json").getLines().mkString("")

  val client = HttpClient(ElasticsearchClientUri(s"elasticsearch://$connectionString"))

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  def createIndexName: String = {
    val localMapping: String = Source.fromResource("elasticsearch-mapping.json")(Codec.UTF8).getLines().mkString("")
    val hash: String =
      MessageDigest
        .getInstance("SHA-1")
        .digest(localMapping.getBytes)
        .map("%02X".format(_))
        .mkString
        .take(12)
        .toLowerCase()

    indexName + "-" + dateFormatter.format(DateHelper.now()) + "-" + hash
  }

  def getHashFromIndex(index: String): String =
    index.split("-").lastOption.getOrElse("")

  def getCurrentIndexName: Future[String] = {
    client
      .executeAsFuture(getAliases(Seq.empty, Seq(aliasName)))
      .map(_.mappings.headOption.map { case (index, _) => index.name }.getOrElse(""))
      .recover {
        case e: Exception =>
          logger.error("fail to retrieve ES alias", e)
          ""
      }
  }

  private def createInitialIndexAndAlias(): Unit = {
    val newIndexName = createIndexName
    client
      .executeAsFuture(createIndex(newIndexName).source(elasticsearchMapping))
      .onComplete {
        case Success(_) =>
          logger.info(s"Elasticsearch index $indexName created")
          client
            .executeAsFuture(aliases(addAlias(aliasName).on(newIndexName)))
            .onComplete {
              case Success(_) =>
                logger.info(s"Elasticsearch alias $aliasName created")
              case Failure(e) =>
                logger.error(s"Error when creating Elasticsearch alias $aliasName", e)
            }
        case Failure(e) =>
          logger.error(s"Error when creating Elasticsearch index $indexName", e)
      }
  }

  client.executeAsFuture(aliasExists(aliasName)).onComplete {
    case Success(AliasExistsResponse(true)) =>
      logger.info(s"Elasticsearch alias $aliasName exist")
    case Success(AliasExistsResponse(false)) =>
      logger.info(s"Elasticsearch alias $aliasName not found")
      createInitialIndexAndAlias()
    case Failure(e) =>
      logger.error(s"Error when checking if elasticsearch alias $aliasName exists", e)
  }

}

object ElasticsearchConfiguration extends ExtensionId[ElasticsearchConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ElasticsearchConfiguration =
    new ElasticsearchConfiguration(system.settings.config.getConfig("make-api.elasticSearch"))

  override def lookup(): ExtensionId[ElasticsearchConfiguration] =
    ElasticsearchConfiguration
  override def get(system: ActorSystem): ElasticsearchConfiguration =
    super.get(system)
}

trait ElasticsearchConfigurationExtension extends ElasticsearchConfigurationComponent { this: Actor =>
  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    ElasticsearchConfiguration(context.system)
}

trait ElasticsearchConfigurationComponent {
  def elasticsearchConfiguration: ElasticsearchConfiguration
}
