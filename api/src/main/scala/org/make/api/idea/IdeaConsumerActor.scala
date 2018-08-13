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

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.idea.IdeaEvent.{IdeaCreatedEvent, IdeaEventWrapper, IdeaUpdatedEvent}
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.elasticsearch.{ElasticsearchConfiguration, ElasticsearchConfigurationComponent}
import org.make.core.idea.IdeaId
import org.make.core.idea.indexed.IndexedIdea
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class IdeaConsumerActor(ideaService: IdeaService, override val elasticsearchConfiguration: ElasticsearchConfiguration)
    extends KafkaConsumerActor[IdeaEventWrapper]
    with KafkaConfigurationExtension
    with DefaultIdeaSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with ActorLogging {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(IdeaProducerActor.topicKey)
  override protected val format: RecordFormat[IdeaEventWrapper] = RecordFormat[IdeaEventWrapper]

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def handleMessage(message: IdeaEventWrapper): Future[Unit] = {
    message.event.fold(ToIdeaEvent) match {
      case event: IdeaCreatedEvent => onCreateOrUpdate(event)
      case event: IdeaUpdatedEvent => onCreateOrUpdate(event)
    }
  }

  object ToIdeaEvent extends Poly1 {
    implicit val atIdeaCreated: Case.Aux[IdeaCreatedEvent, IdeaCreatedEvent] = at(identity)
    implicit val atIdeaUpdated: Case.Aux[IdeaUpdatedEvent, IdeaUpdatedEvent] = at(identity)
  }

  def onCreateOrUpdate(event: IdeaEvent): Future[Unit] = {
    retrieveAndShapeIdea(event.ideaId).flatMap(indexOrUpdate)
  }

  def indexOrUpdate(idea: IndexedIdea): Future[Unit] = {
    log.debug(s"Indexing $idea")
    elasticsearchIdeaAPI
      .findIdeaById(idea.ideaId)
      .flatMap {
        case None    => elasticsearchIdeaAPI.indexIdea(idea)
        case Some(_) => elasticsearchIdeaAPI.updateIdea(idea)
      }
      .map { _ =>
        }
  }

  private def retrieveAndShapeIdea(id: IdeaId): Future[IndexedIdea] = {
    ideaService.fetchOne(id).flatMap {
      case None       => Future.failed(new IllegalArgumentException(s"Idea ${id.value} doesn't exist"))
      case Some(idea) => Future.successful(IndexedIdea.createFromIdea(idea))
    }
  }

  override val groupId = "idea-consumer"
}

object IdeaConsumerActor {
  def props(ideaService: IdeaService, elasticsearchConfiguration: ElasticsearchConfiguration): Props =
    Props(new IdeaConsumerActor(ideaService, elasticsearchConfiguration))
  val name: String = "idea-consumer"
}
