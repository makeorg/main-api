package org.make.api.technical.elasticsearch

import cats.implicits._
import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import cats.data.OptionT
import com.sksamuel.elastic4s.IndexAndType
import com.typesafe.scalalogging.StrictLogging
import org.make.api.sequence.{SequenceCoordinatorServiceComponent, SequenceSearchEngine, SequenceSearchEngineComponent}
import org.make.api.theme.ThemeServiceComponent
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
    with ThemeServiceComponent
    with SequenceSearchEngineComponent
    with StrictLogging {

  object SequenceStream {
    val maybeIndexedSequence: Flow[String, Option[IndexedSequence], NotUsed] =
      Flow[String].mapAsync(parallelism)(persistenceId => getIndexedSequence(SequenceId(persistenceId)))

    def runIndexSequences(sequenceIndexName: String): Flow[Seq[IndexedSequence], Done, NotUsed] =
      Flow[Seq[IndexedSequence]].mapAsync(parallelism)(sequences => executeIndexSequences(sequences, sequenceIndexName))

    def flowIndexSequences(sequenceIndexName: String): Flow[String, Done, NotUsed] =
      maybeIndexedSequence
        .via(filterIsDefined[IndexedSequence])
        .via(grouped[IndexedSequence])
        .via(runIndexSequences(sequenceIndexName))
  }

  private def retrieveThemes(themeIds: Seq[ThemeId]): Future[Option[Seq[Theme]]] = {
    themeService
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
        createdAt = sequence.createdAt.get,
        updatedAt = sequence.updatedAt.get,
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

  private def executeIndexSequences(sequences: Seq[IndexedSequence], indexName: String): Future[Done] =
    elasticsearchSequenceAPI
      .indexSequences(sequences, Some(IndexAndType(indexName, SequenceSearchEngine.sequenceIndexName)))
      .recoverWith {
        case e =>
          logger.error("Indexing sequences failed", e)
          Future.successful(Done)
      }

}
