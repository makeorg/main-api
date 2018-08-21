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

package org.make.api.sequence

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.sequence.PublishedSequenceEvent._
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.elasticsearch.{ElasticsearchConfiguration, ElasticsearchConfigurationComponent}
import org.make.api.theme.ThemeService
import org.make.core.RequestContext
import org.make.core.reference.{Theme, ThemeId}
import org.make.core.sequence._
import org.make.core.sequence.indexed._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SequenceConsumerActor(sequenceCoordinator: ActorRef,
                            themeService: ThemeService,
                            override val elasticsearchConfiguration: ElasticsearchConfiguration)
    extends KafkaConsumerActor[SequenceEventWrapper]
    with KafkaConfigurationExtension
    with DefaultSequenceSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with ActorLogging {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(SequenceProducerActor.topicKey)
  override protected val format: RecordFormat[SequenceEventWrapper] = RecordFormat[SequenceEventWrapper]

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def handleMessage(message: SequenceEventWrapper): Future[Unit] = {
    message.event.fold(ToSequenceEvent) match {
      case event: SequenceViewed           => doNothing(event)
      case event: SequenceUpdated          => onCreateOrUpdate(event)
      case event: SequenceCreated          => onCreateOrUpdate(event)
      case event: SequenceProposalsAdded   => onCreateOrUpdate(event)
      case event: SequenceProposalsRemoved => onCreateOrUpdate(event)
      case event: SequencePatched          => onCreateOrUpdate(event)
    }
  }

  def onCreateOrUpdate(event: SequenceEvent): Future[Unit] = {
    retrieveAndShapeSequence(event.id).flatMap(indexOrUpdate)
  }

  def indexOrUpdate(sequence: IndexedSequence): Future[Unit] = {
    log.debug(s"Indexing $sequence")
    elasticsearchSequenceAPI
      .findSequenceById(sequence.id)
      .flatMap {
        case None    => elasticsearchSequenceAPI.indexSequence(sequence)
        case Some(_) => elasticsearchSequenceAPI.updateSequence(sequence)
      }
      .map { _ =>
        }
  }

  private def retrieveAndShapeSequence(id: SequenceId): Future[IndexedSequence] = {

    def retrieveThemes(themeIds: Seq[ThemeId]): Future[Option[Seq[Theme]]] = {
      themeService
        .findAll()
        .map(_.filter(theme => themeIds.contains(theme.themeId)))
        .map(Some(_))
    }

    val maybeResult = for {
      sequence <- OptionT((sequenceCoordinator ? GetSequence(id, RequestContext.empty)).mapTo[Option[Sequence]])
      themes   <- OptionT(retrieveThemes(sequence.themeIds))
    } yield {
      IndexedSequence(
        id = sequence.sequenceId,
        title = sequence.title,
        slug = sequence.slug,
        translation = sequence.sequenceTranslation,
        status = sequence.status,
        createdAt = sequence.createdAt match {
          case Some(date) => date
          case _          => throw new IllegalStateException("created at required")
        },
        updatedAt = sequence.updatedAt match {
          case Some(date) => date
          case _          => throw new IllegalStateException("update at required")
        },
        context = Some(
          Context(
            operation = sequence.creationContext.operationId,
            source = sequence.creationContext.source,
            location = sequence.creationContext.location,
            question = sequence.creationContext.question
          )
        ),
        themes = themes.map(theme => IndexedSequenceTheme(themeId = theme.themeId, translation = theme.translations)),
        operationId = sequence.operationId,
        proposals = sequence.proposalIds.map(IndexedSequenceProposalId.apply),
        searchable = sequence.searchable
      )
    }
    maybeResult.getOrElseF(Future.failed(new IllegalArgumentException(s"Sequence ${id.value} doesn't exist")))
  }

  override val groupId = "sequence-consumer"
}

object SequenceConsumerActor {
  def props(sequenceCoordinator: ActorRef,
            themeService: ThemeService,
            elasticsearchConfiguration: ElasticsearchConfiguration): Props =
    Props(new SequenceConsumerActor(sequenceCoordinator, themeService, elasticsearchConfiguration))
  val name: String = "sequence-consumer"
}
