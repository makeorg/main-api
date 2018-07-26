package org.make.api.technical.elasticsearch

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import com.sksamuel.elastic4s.IndexAndType
import com.typesafe.scalalogging.StrictLogging
import org.make.api.idea.{IdeaSearchEngine, IdeaSearchEngineComponent, PersistentIdeaServiceComponent}
import org.make.api.tagtype.PersistentTagTypeServiceComponent
import org.make.core.idea.Idea
import org.make.core.idea.indexed.IndexedIdea
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait IdeaIndexationStream
    extends IndexationStream
    with IdeaSearchEngineComponent
    with PersistentIdeaServiceComponent
    with PersistentTagTypeServiceComponent
    with StrictLogging {
  object IdeaStream {
    def runIndexIdeas(ideaIndexName: String): Flow[Seq[Idea], Done, NotUsed] =
      Flow[Seq[Idea]].mapAsync(parallelism)(ideas => executeIndexIdeas(ideas, ideaIndexName))

    def flowIndexIdeas(ideaIndexName: String): Flow[Idea, Done, NotUsed] =
      grouped[Idea].via(runIndexIdeas(ideaIndexName))

  }

  private def executeIndexIdeas(ideas: Seq[Idea], ideaIndexName: String): Future[Done] = {
    elasticsearchIdeaAPI
      .indexIdeas(
        ideas.map(IndexedIdea.createFromIdea),
        Some(IndexAndType(ideaIndexName, IdeaSearchEngine.ideaIndexName))
      )
      .recoverWith {
        case e =>
          logger.error("Indexing ideas failed", e)
          Future.successful(Done)
      }
  }
}
