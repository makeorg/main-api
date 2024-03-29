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

import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.sksamuel.elastic4s.Index
import grizzled.slf4j.Logging
import org.make.api.idea.IdeaSearchEngineComponent
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.core.idea.indexed.IndexedIdea
import org.make.core.idea.{Idea, IdeaId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait IdeaIndexationStream
    extends IndexationStream
    with IdeaSearchEngineComponent
    with ProposalSearchEngineComponent
    with Logging {
  object IdeaStream {
    def runIndexIdeas(ideaIndexName: String): Flow[Seq[Idea], Done, NotUsed] =
      Flow[Seq[Idea]].mapAsync(parallelism)(ideas => executeIndexIdeas(ideas, ideaIndexName))

    def flowIndexIdeas(ideaIndexName: String): Flow[Idea, Done, NotUsed] =
      grouped[Idea].via(runIndexIdeas(ideaIndexName))

  }

  private def executeIndexIdeas(ideas: Seq[Idea], ideaIndexName: String): Future[Done] = {
    elasticsearchProposalAPI
      .countProposalsByIdea(ideas.map(_.ideaId))
      .flatMap { countProposalsByIdea =>
        def proposalsCount(ideaId: IdeaId): Int = countProposalsByIdea.getOrElse(ideaId, 0L).toInt
        elasticsearchIdeaAPI
          .indexIdeas(
            ideas.map(idea => IndexedIdea.createFromIdea(idea, proposalsCount(idea.ideaId))),
            Some(Index(ideaIndexName))
          )
      }
      .recoverWith {
        case e =>
          logger.error("Indexing ideas failed", e)
          Future.successful(Done)
      }
  }
}
