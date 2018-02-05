package org.make.api.sequence

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.sequence.PublishedSequenceEvent._
import org.make.api.tag.TagService
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationExtension
import org.make.api.theme.ThemeService
import org.make.core.RequestContext
import org.make.core.reference.{Tag, TagId, Theme, ThemeId}
import org.make.core.sequence._
import org.make.core.sequence.indexed._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SequenceConsumerActor(sequenceCoordinator: ActorRef, tagService: TagService, themeService: ThemeService)
    extends KafkaConsumerActor[SequenceEventWrapper]
    with KafkaConfigurationExtension
    with DefaultSequenceSearchEngineComponent
    with ElasticsearchConfigurationExtension
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

    def retrieveTags(tags: Seq[TagId]): Future[Option[Seq[Tag]]] = {
      tagService
        .findEnabledByTagIds(tags)
        .map(Some(_))
    }

    def retrieveThemes(themeIds: Seq[ThemeId]): Future[Option[Seq[Theme]]] = {
      themeService
        .findAll()
        .map(_.filter(theme => themeIds.contains(theme.themeId)))
        .map(Some(_))
    }

    val maybeResult = for {
      sequence @ _ <- OptionT((sequenceCoordinator ? GetSequence(id, RequestContext.empty)).mapTo[Option[Sequence]])
      tags @ _     <- OptionT(retrieveTags(sequence.tagIds))
      themes @ _   <- OptionT(retrieveThemes(sequence.themeIds))
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
          Context(
            operation = sequence.creationContext.operationId,
            source = sequence.creationContext.source,
            location = sequence.creationContext.location,
            question = sequence.creationContext.question
          )
        ),
        tags = tags,
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
  def props(sequenceCoordinator: ActorRef, tagService: TagService, themeService: ThemeService): Props =
    Props(new SequenceConsumerActor(sequenceCoordinator, tagService, themeService))
  val name: String = "sequence-consumer"
}
