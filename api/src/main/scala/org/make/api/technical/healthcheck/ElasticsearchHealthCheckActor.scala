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

package org.make.api.technical.healthcheck

import akka.actor.{ActorSystem, Props}
import com.sksamuel.elastic4s.IndexAndType
import com.sksamuel.elastic4s.http.ElasticClient
import com.sksamuel.elastic4s.http.ElasticDsl.{searchWithType, _}
import org.make.api.ActorSystemComponent
import org.make.api.proposal.ProposalSearchEngine
import org.make.api.technical.ShortenedNames
import org.make.api.technical.elasticsearch._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class ElasticsearchHealthCheckActor(healthCheckExecutionContext: ExecutionContext)
    extends HealthCheck
    with ShortenedNames
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with ActorSystemComponent {

  override def actorSystem: ActorSystem = context.system

  override lazy val elasticsearchConfiguration = new ElasticsearchConfiguration(
    context.system.settings.config.getConfig("make-api.elasticSearch")
  )

  lazy val client: ElasticClient = elasticsearchClient.client

  private val proposalAlias
    : IndexAndType = elasticsearchConfiguration.proposalAliasName / ProposalSearchEngine.proposalIndexName

  override val techno: String = "elasticsearch"

  override def preStart(): Unit = {
    Await.result(elasticsearchClient.initialize(), atMost = 10.seconds)
  }

  override def healthCheck(): Future[String] = {
    implicit val cxt: EC = healthCheckExecutionContext

    val futureResults: Future[Long] = client
      .executeAsFuture(searchWithType(proposalAlias).bool(must(matchAllQuery())).limit(1))
      .map { response =>
        response.totalHits
      }

    futureResults.map {
      case l if l > 0 => "OK"
      case _ =>
        log.warning(s"""Unexpected result in elasticsearch health check: expected result greater than 0""")
        "NOK"
    }
  }

}

object ElasticsearchHealthCheckActor extends HealthCheckActorDefinition {
  override val name: String = "elasticsearch-health-check"

  override def props(healthCheckExecutionContext: ExecutionContext): Props =
    Props(new ElasticsearchHealthCheckActor(healthCheckExecutionContext))
}
