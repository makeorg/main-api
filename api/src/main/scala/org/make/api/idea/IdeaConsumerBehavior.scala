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

package org.make.api.idea

import akka.Done
import akka.actor.typed.Behavior
import akka.util.Timeout
import grizzled.slf4j.Logging
import org.make.api.technical.KafkaConsumerBehavior
import org.make.api.technical.KafkaConsumerBehavior.Protocol
import org.make.core.idea.IdeaId
import org.make.core.idea.indexed.IndexedIdea

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class IdeaConsumerBehavior(ideaService: IdeaService, elasticsearchIdeaAPI: IdeaSearchEngine)
    extends KafkaConsumerBehavior[IdeaEventWrapper]
    with Logging {

  override protected val topicKey: String = IdeaProducerBehavior.topicKey
  implicit val timeout: Timeout = Timeout(5.seconds)

  override def handleMessage(message: IdeaEventWrapper): Future[_] = {
    message.event match {
      case event: IdeaCreatedEvent => onCreateOrUpdate(event)
      case event: IdeaUpdatedEvent => onCreateOrUpdate(event)
    }
  }

  def onCreateOrUpdate(event: IdeaEvent): Future[Done] = {
    retrieveAndShapeIdea(event.ideaId).flatMap(indexOrUpdate)
  }

  def indexOrUpdate(idea: IndexedIdea): Future[Done] = {
    debug(s"Indexing $idea")
    elasticsearchIdeaAPI
      .findIdeaById(idea.ideaId)
      .flatMap {
        case None        => elasticsearchIdeaAPI.indexIdea(idea)
        case Some(found) => elasticsearchIdeaAPI.updateIdea(idea.copy(proposalsCount = found.proposalsCount))
      }
  }

  private def retrieveAndShapeIdea(id: IdeaId): Future[IndexedIdea] = {
    ideaService.fetchOne(id).flatMap {
      case None       => Future.failed(new IllegalArgumentException(s"Idea ${id.value} doesn't exist"))
      case Some(idea) => Future.successful(IndexedIdea.createFromIdea(idea, proposalsCount = 0))
    }
  }

  override val groupId = "idea-consumer"
}

object IdeaConsumerBehavior {
  def apply(ideaService: IdeaService, elasticsearchIdeaAPI: IdeaSearchEngine): Behavior[Protocol] =
    new IdeaConsumerBehavior(ideaService, elasticsearchIdeaAPI).createBehavior(name)
  val name: String = "idea-consumer"
}
