package org.make.api.idea

import akka.Done
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.HttpClient
import com.sksamuel.elastic4s.searches.queries.BoolQueryDefinition
import com.sksamuel.elastic4s.{ElasticsearchClientUri, IndexAndType}
import com.typesafe.scalalogging.StrictLogging
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationComponent
import org.make.core.CirceFormatters
import org.make.core.idea.indexed.{IdeaSearchResult, IndexedIdea}
import org.make.core.idea.{IdeaId, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait IdeaSearchEngineComponent {
  def elasticsearchIdeaAPI: IdeaSearchEngine
}

trait IdeaSearchEngine {
  def findIdeaById(ideaId: IdeaId): Future[Option[IndexedIdea]]
  def searchIdeas(query: IdeaSearchQuery): Future[IdeaSearchResult]
  def indexIdea(record: IndexedIdea, mayBeIndex: Option[IndexAndType] = None): Future[Done]
  def updateIdea(record: IndexedIdea, mayBeIndex: Option[IndexAndType] = None): Future[Done]
}

object IdeaSearchEngine {
  val ideaIndexName = "idea"
}

trait DefaultIdeaSearchEngineComponent extends IdeaSearchEngineComponent with CirceFormatters {
  self: ElasticsearchConfigurationComponent =>

  override lazy val elasticsearchIdeaAPI: IdeaSearchEngine = new IdeaSearchEngine with StrictLogging {

    private val client = HttpClient(
      ElasticsearchClientUri(s"elasticsearch://${elasticsearchConfiguration.connectionString}")
    )

    private val ideaAlias: IndexAndType = elasticsearchConfiguration.aliasName / IdeaSearchEngine.ideaIndexName

    override def findIdeaById(ideaId: IdeaId): Future[Option[IndexedIdea]] = {
      client.execute(get(id = ideaId.value).from(ideaAlias)).map(_.toOpt[IndexedIdea])
    }

    override def searchIdeas(ideaSearchQuery: IdeaSearchQuery): Future[IdeaSearchResult] = {
      // parse json string to build search query
      val searchFilters = IdeaSearchFilters.getIdeaSearchFilters(ideaSearchQuery)
      val request = search(ideaAlias)
        .bool(BoolQueryDefinition(must = searchFilters))
        .sortBy(IdeaSearchFilters.getSort(ideaSearchQuery))
        .size(IdeaSearchFilters.getLimitSearch(ideaSearchQuery))
        .from(IdeaSearchFilters.getSkipSearch(ideaSearchQuery))

      logger.debug(client.show(request))

      client.execute {
        request
      }.map { response =>
        IdeaSearchResult(total = response.totalHits, results = response.to[IndexedIdea])
      }
    }

    override def indexIdea(record: IndexedIdea, maybeIndex: Option[IndexAndType] = None): Future[Done] = {
      logger.debug(s"Saving in Elasticsearch: $record")
      val index = maybeIndex.getOrElse(ideaAlias)
      client.execute {
        indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.ideaId.value)
      }.map { _ =>
        Done
      }
    }

    override def updateIdea(record: IndexedIdea, maybeIndex: Option[IndexAndType] = None): Future[Done] = {
      val index = maybeIndex.getOrElse(ideaAlias)
      logger.debug(s"$index -> Updating in Elasticsearch: $record")
      client
        .execute((update(id = record.ideaId.value) in index).doc(record).refresh(RefreshPolicy.IMMEDIATE))
        .map(_ => Done)
    }
  }
}
