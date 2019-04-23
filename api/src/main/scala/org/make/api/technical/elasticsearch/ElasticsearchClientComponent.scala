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
import java.time.format.DateTimeFormatter

import com.sksamuel.elastic4s.http.ElasticDsl.{addAlias, aliasExists, aliases, createIndex, getAliases, _}
import com.sksamuel.elastic4s.http.index.admin.AliasExistsResponse
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticProperties}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.technical.security.SecurityHelper
import org.make.core.DateHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.{Codec, Source}

trait ElasticsearchClient {
  def client: ElasticClient
  def createIndexName(aliasName: String): String
  def hashForAlias(aliasName: String): String
  def getCurrentIndicesName: Future[Seq[String]]
  def getHashFromIndex(index: String): String
  def mappingForAlias: String => String
  def initialize(): Future[Unit]
}

trait ElasticsearchClientComponent {
  def elasticsearchClient: ElasticsearchClient
}

trait DefaultElasticsearchClientComponent extends ElasticsearchClientComponent with StrictLogging {
  self: ElasticsearchConfigurationComponent with ActorSystemComponent =>

  override lazy val elasticsearchClient: ElasticsearchClient = new ElasticsearchClient {

    private lazy val allAliases =
      Seq(
        elasticsearchConfiguration.ideaAliasName,
        elasticsearchConfiguration.proposalAliasName,
        elasticsearchConfiguration.organisationAliasName
      )

    // create index
    lazy val elasticsearchIdeaMapping: String =
      Source.fromResource("elasticsearch-mappings/idea.json")(Codec.UTF8).getLines().mkString("")
    lazy val elasticsearchProposalMapping: String =
      Source.fromResource("elasticsearch-mappings/proposal.json")(Codec.UTF8).getLines().mkString("")
    lazy val elasticsearchOrganisationMapping: String =
      Source.fromResource("elasticsearch-mappings/organisation.json")(Codec.UTF8).getLines().mkString("")

    override lazy val client: ElasticClient = ElasticClient(
      ElasticProperties(s"http://${elasticsearchConfiguration.connectionString}")
    )

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    override def mappingForAlias: String => String = {
      case alias if alias == elasticsearchConfiguration.ideaAliasName         => elasticsearchIdeaMapping
      case alias if alias == elasticsearchConfiguration.proposalAliasName     => elasticsearchProposalMapping
      case alias if alias == elasticsearchConfiguration.organisationAliasName => elasticsearchOrganisationMapping
    }

    override def hashForAlias(aliasName: String): String = {
      val shortHashLength = 12
      val localMapping: String = mappingForAlias(aliasName)
      SecurityHelper.defaultHash(localMapping).take(shortHashLength).toLowerCase()
    }

    override def createIndexName(aliasName: String): String = {
      Seq(
        elasticsearchConfiguration.indexName,
        aliasName,
        dateFormatter.format(DateHelper.now()),
        hashForAlias(aliasName)
      ).mkString("-")
    }

    override def getHashFromIndex(index: String): String =
      index.split("-").lastOption.getOrElse("")

    override def getCurrentIndicesName: Future[Seq[String]] = {
      client
        .executeAsFuture(getAliases(Seq.empty, allAliases))
        .map(_.mappings.map { case (index, _) => index.name }.toSeq)
        .recover {
          case e: Exception =>
            logger.error("fail to retrieve ES alias", e)
            Seq.empty
        }
    }

    private def createInitialIndexAndAlias(aliasName: String): Future[Unit] = {
      val newIndexName = createIndexName(aliasName)
      client
        .executeAsFuture(createIndex(newIndexName).source(mappingForAlias(aliasName)))
        .flatMap { _ =>
          logger.info(s"Elasticsearch index $newIndexName created")
          client
            .executeAsFuture(aliases(addAlias(aliasName).on(newIndexName)))
            .map { _ =>
              logger.info(s"Elasticsearch alias $aliasName created")
            }
        }
    }

    override def initialize(): Future[Unit] = {
      Future
        .traverse(allAliases) { aliasName =>
          client.executeAsFuture(aliasExists(aliasName)).flatMap {
            case AliasExistsResponse(true) =>
              logger.info(s"Elasticsearch alias $aliasName exist")
              Future.successful {}
            case AliasExistsResponse(false) =>
              logger.info(s"Elasticsearch alias $aliasName not found")
              createInitialIndexAndAlias(aliasName)
          }
        }
        .map(_ => ())
    }
  }
}
