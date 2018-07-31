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
