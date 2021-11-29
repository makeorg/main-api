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

import com.sksamuel.elastic4s.IndexAndType
import com.sksamuel.elastic4s.http.ElasticDsl.{searchWithType, _}
import org.make.api.proposal.ProposalSearchEngine
import org.make.api.technical.elasticsearch._
import org.make.api.technical.healthcheck.HealthCheck.Status

import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchHealthCheck(
  elasticsearchConfiguration: ElasticsearchConfiguration,
  elasticsearchClient: ElasticsearchClient
) extends HealthCheck {

  override val techno: String = "elasticsearch"

  private val proposalAlias: IndexAndType =
    elasticsearchConfiguration.proposalAliasName / ProposalSearchEngine.proposalIndexName

  override def healthCheck()(implicit ctx: ExecutionContext): Future[Status] = {
    elasticsearchClient.client
      .executeAsFuture(searchWithType(proposalAlias).bool(must(matchAllQuery())).limit(1))
      .map { response =>
        if (response.totalHits > 0) {
          Status.OK
        } else {
          Status.NOK(Some("Unexpected result in elasticsearch health check: expected result greater than 0"))
        }
      }
  }

}
