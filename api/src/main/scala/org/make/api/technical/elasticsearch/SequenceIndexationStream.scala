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
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.elastic4s.IndexAndType
import com.typesafe.scalalogging.StrictLogging
import org.make.api.sequence.{SequenceCoordinatorServiceComponent, SequenceSearchEngine, SequenceSearchEngineComponent}
import org.make.api.theme.PersistentThemeServiceComponent
import org.make.core.reference.{Theme, ThemeId}
import org.make.core.sequence.SequenceId
import org.make.core.sequence.indexed.{
  IndexedSequence,
  IndexedSequenceProposalId,
  IndexedSequenceTheme,
  Context => SequenceContext
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SequenceIndexationStream
    extends IndexationStream
    with SequenceCoordinatorServiceComponent
    with PersistentThemeServiceComponent
    with SequenceSearchEngineComponent
    with StrictLogging {

  object SequenceStream {
    val maybeIndexedSequence: Flow[SequenceId, Option[IndexedSequence], NotUsed] =
      Flow[SequenceId].mapAsync(parallelism)(sequenceId => getIndexedSequence(sequenceId))

    def runIndexSequences(sequenceIndexName: String): Flow[Seq[IndexedSequence], Done, NotUsed] =
      Flow[Seq[IndexedSequence]].mapAsync(parallelism)(sequences => executeIndexSequences(sequences, sequenceIndexName))

    def flowIndexSequences(sequenceIndexName: String): Flow[SequenceId, Done, NotUsed] =
      maybeIndexedSequence
        .via(filterIsDefined[IndexedSequence])
        .via(grouped[IndexedSequence])
        .via(runIndexSequences(sequenceIndexName))
  }

  private def retrieveThemes(themeIds: Seq[ThemeId]): Future[Option[Seq[Theme]]] = {
    persistentThemeService
      .findAll()
      .map(_.filter(theme => themeIds.contains(theme.themeId)))
      .map(Some(_))
  }

  private def getIndexedSequence(sequenceId: SequenceId): Future[Option[IndexedSequence]] = {
    val maybeResult: OptionT[Future, IndexedSequence] = for {
      sequence <- OptionT(sequenceCoordinatorService.getSequence(sequenceId))
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
          case _          => throw new IllegalStateException("updated at required")
        },
        context = Some(
          SequenceContext(
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

    maybeResult.value
  }

  private def executeIndexSequences(sequences: Seq[IndexedSequence], indexName: String): Future[Done] = {
    elasticsearchSequenceAPI
      .indexSequences(sequences, Some(IndexAndType(indexName, SequenceSearchEngine.sequenceIndexName)))
      .recoverWith {
        case e =>
          logger.error("Indexing sequences failed", e)
          Future.successful(Done)
      }
  }

}
