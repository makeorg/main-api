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

package org.make.api.organisation

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.queries.BoolQuery
import com.sksamuel.elastic4s.{IndexAndType, RefreshPolicy}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.elasticsearch.{ElasticsearchConfigurationComponent, _}
import org.make.core.CirceFormatters
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult}
import org.make.core.user.{OrganisationSearchFilters, OrganisationSearchQuery, SlugSearchFilter, UserId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OrganisationSearchEngineComponent {
  def elasticsearchOrganisationAPI: OrganisationSearchEngine
}

trait OrganisationSearchEngine {
  def findOrganisationById(organisationId: UserId): Future[Option[IndexedOrganisation]]
  def findOrganisationBySlug(slug: String): Future[Option[IndexedOrganisation]]
  def searchOrganisations(query: OrganisationSearchQuery): Future[OrganisationSearchResult]
  def indexOrganisation(record: IndexedOrganisation, mayBeIndex: Option[IndexAndType] = None): Future[Done]
  def indexOrganisations(records: Seq[IndexedOrganisation], mayBeIndex: Option[IndexAndType] = None): Future[Done]
  def updateOrganisation(record: IndexedOrganisation, mayBeIndex: Option[IndexAndType] = None): Future[Done]
}

object OrganisationSearchEngine {
  val organisationIndexName = "organisation"
}

trait DefaultOrganisationSearchEngineComponent extends OrganisationSearchEngineComponent with CirceFormatters {
  self: ElasticsearchConfigurationComponent with ElasticsearchClientComponent =>

  override lazy val elasticsearchOrganisationAPI: OrganisationSearchEngine = new DefaultOrganisationSearchEngine

  class DefaultOrganisationSearchEngine extends OrganisationSearchEngine with StrictLogging {

    private lazy val client = elasticsearchClient.client

    private val organisationAlias: IndexAndType =
      elasticsearchConfiguration.organisationAliasName / OrganisationSearchEngine.organisationIndexName

    override def findOrganisationById(organisationId: UserId): Future[Option[IndexedOrganisation]] = {
      client.executeAsFuture(get(id = organisationId.value).from(organisationAlias)).map(_.toOpt[IndexedOrganisation])
    }

    override def findOrganisationBySlug(slug: String): Future[Option[IndexedOrganisation]] = {
      val query = OrganisationSearchQuery(
        filters = OrganisationSearchFilters.parse(slug = Some(SlugSearchFilter(slug))),
        limit = Some(1)
      )
      val searchFilters = OrganisationSearchFilters.getOrganisationSearchFilters(query)
      val request = searchWithType(organisationAlias)
        .bool(BoolQuery(must = searchFilters))
        .from(0)
        .size(1)

      client.executeAsFuture {
        request
      }.map(_.to[IndexedOrganisation]).map {
        case indexedSeq if indexedSeq.isEmpty => None
        case other                            => Some(other.head)
      }
    }

    override def searchOrganisations(query: OrganisationSearchQuery): Future[OrganisationSearchResult] = {
      val searchFilters = OrganisationSearchFilters.getOrganisationSearchFilters(query)
      val request: SearchRequest = searchWithType(organisationAlias)
        .bool(BoolQuery(must = searchFilters))
        .sortBy(OrganisationSearchFilters.getSort(query))
        .size(OrganisationSearchFilters.getLimitSearch(query))
        .from(OrganisationSearchFilters.getSkipSearch(query))

      val requestSorted = query.sortAlgorithm match {
        case None                => request
        case Some(sortAlgorithm) => sortAlgorithm.sortDefinition(request)
      }

      client
        .executeAsFuture(requestSorted)
        .map { response =>
          OrganisationSearchResult(total = response.totalHits, results = response.to[IndexedOrganisation])
        }
    }

    override def indexOrganisation(record: IndexedOrganisation, mayBeIndex: Option[IndexAndType]): Future[Done] = {
      val index = mayBeIndex.getOrElse(organisationAlias)
      client
        .executeAsFuture(indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.organisationId.value))
        .map { _ =>
          Done
        }
    }

    override def indexOrganisations(
      records: Seq[IndexedOrganisation],
      mayBeIndex: Option[IndexAndType]
    ): Future[Done] = {
      val index = mayBeIndex.getOrElse(organisationAlias)
      client
        .executeAsFuture(bulk(records.map { record =>
          indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.organisationId.value)
        }))
        .map { _ =>
          Done
        }
    }

    override def updateOrganisation(record: IndexedOrganisation, mayBeIndex: Option[IndexAndType]): Future[Done] = {
      val index = mayBeIndex.getOrElse(organisationAlias)
      client
        .executeAsFuture(
          (update(id = record.organisationId.value) in index).doc(record).refresh(RefreshPolicy.IMMEDIATE)
        )
        .map(_ => Done)
    }

  }
}
