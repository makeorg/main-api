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
  val ideaAliasName: String = configuration.getString("idea-alias-name")
  val proposalAliasName: String = configuration.getString("proposal-alias-name")
  val sequenceAliasName: String = configuration.getString("sequence-alias-name")

  // create index
  val elasticsearchIdeaMapping: String =
    Source.fromResource("elasticsearch-mappings/idea.json")(Codec.UTF8).getLines().mkString("")
  val elasticsearchProposalMapping: String =
    Source.fromResource("elasticsearch-mappings/proposal.json")(Codec.UTF8).getLines().mkString("")
  val elasticsearchSequenceMapping: String =
    Source.fromResource("elasticsearch-mappings/sequence.json")(Codec.UTF8).getLines().mkString("")

  val client = HttpClient(ElasticsearchClientUri(s"elasticsearch://$connectionString"))

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  def mappingForAlias: String => String = {
    case `ideaAliasName`     => elasticsearchIdeaMapping
    case `proposalAliasName` => elasticsearchProposalMapping
    case `sequenceAliasName` => elasticsearchSequenceMapping
  }

  def hashForAlias(aliasName: String): String = {
    val localMapping: String = mappingForAlias(aliasName)
    MessageDigest
      .getInstance("SHA-1")
      .digest(localMapping.getBytes)
      .map("%02X".format(_))
      .mkString
      .take(12)
      .toLowerCase()
  }

  def createIndexName(aliasName: String): String = {
    Seq(indexName, aliasName, dateFormatter.format(DateHelper.now()), hashForAlias(aliasName)).mkString("-")
  }

  def getHashFromIndex(index: String): String =
    index.split("-").lastOption.getOrElse("")

  def getCurrentIndicesName: Future[Seq[String]] = {
    client
      .executeAsFuture(getAliases(Seq.empty, Seq(ideaAliasName, proposalAliasName, sequenceAliasName)))
      .map(_.mappings.map { case (index, _) => index.name }.toSeq)
      .recover {
        case e: Exception =>
          logger.error("fail to retrieve ES alias", e)
          Seq.empty
      }
  }

  private def createInitialIndexAndAlias(aliasName: String): Unit = {
    val newIndexName = createIndexName(aliasName)
    client
      .executeAsFuture(createIndex(newIndexName).source(mappingForAlias(aliasName)))
      .onComplete {
        case Success(_) =>
          logger.info(s"Elasticsearch index $newIndexName created")
          client
            .executeAsFuture(aliases(addAlias(aliasName).on(newIndexName)))
            .onComplete {
              case Success(_) => logger.info(s"Elasticsearch alias $aliasName created")
              case Failure(e) => logger.error(s"Error when creating Elasticsearch alias $aliasName", e)
            }
        case Failure(e) =>
          logger.error(s"Error when creating Elasticsearch index $newIndexName", e)
      }
  }

  Seq(ideaAliasName, proposalAliasName, sequenceAliasName).foreach { aliasName =>
    client.executeAsFuture(aliasExists(aliasName)).onComplete {
      case Success(AliasExistsResponse(true)) =>
        logger.info(s"Elasticsearch alias $aliasName exist")
      case Success(AliasExistsResponse(false)) =>
        logger.info(s"Elasticsearch alias $aliasName not found")
        createInitialIndexAndAlias(aliasName)
      case Failure(e) =>
        logger.error(s"Error when checking if elasticsearch alias $aliasName exists", e)
    }
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

trait ElasticsearchConfigurationExtension extends ElasticsearchConfigurationComponent {
  this: Actor =>
  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    ElasticsearchConfiguration(context.system)
}

trait ElasticsearchConfigurationComponent {
  def elasticsearchConfiguration: ElasticsearchConfiguration
}
