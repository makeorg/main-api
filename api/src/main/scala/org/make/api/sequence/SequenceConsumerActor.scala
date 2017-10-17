package org.make.api.sequence

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.sequence.SequenceEvent._
import org.make.api.tag.TagService
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationExtension
import org.make.api.theme.ThemeService
import org.make.api.user.UserService
import org.make.core.RequestContext
import org.make.core.reference.{Tag, TagId, Theme, ThemeId}
import org.make.core.sequence._
import org.make.core.sequence.indexed._
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class SequenceConsumerActor(sequenceCoordinator: ActorRef,
                            userService: UserService,
                            tagService: TagService,
                            themeService: ThemeService)
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
      case _: SequenceViewed               => Future.successful {}
      case event: SequenceUpdated          => onCreateOrUpdate(event)
      case event: SequenceCreated          => onCreateOrUpdate(event)
      case event: SequenceProposalsAdded   => onCreateOrUpdate(event)
      case event: SequenceProposalsRemoved => onCreateOrUpdate(event)
    }
  }

  object ToSequenceEvent extends Poly1 {
    implicit val atSequenceViewed: Case.Aux[SequenceViewed, SequenceViewed] = at(identity)
    implicit val atSequenceUpdated: Case.Aux[SequenceUpdated, SequenceUpdated] = at(identity)
    implicit val atSequenceCreated: Case.Aux[SequenceCreated, SequenceCreated] = at(identity)
    implicit val atSequenceProposalsAdded: Case.Aux[SequenceProposalsAdded, SequenceProposalsAdded] = at(identity)
    implicit val atSequenceProposalsRemoved: Case.Aux[SequenceProposalsRemoved, SequenceProposalsRemoved] = at(identity)
  }

  def onCreateOrUpdate(event: SequenceEvent): Future[Unit] = {
    retrieveAndShapeSequence(event.id).flatMap(indexOrUpdate)
  }

  def indexOrUpdate(sequence: IndexedSequence): Future[Unit] = {
    log.debug(s"Indexing $sequence")
    elasticSearchSequenceAPI
      .findSequenceById(sequence.id)
      .flatMap {
        case None    => elasticSearchSequenceAPI.indexSequence(sequence)
        case Some(_) => elasticSearchSequenceAPI.updateSequence(sequence)
      }
      .map { _ =>
        }
  }

  private def retrieveAndShapeSequence(id: SequenceId): Future[IndexedSequence] = {

    def retrieveTags(tags: Seq[TagId]): Future[Option[Seq[Tag]]] = {
      tagService
        .fetchEnabledByTagIds(tags)
        .map(Some(_))
    }

    def retrieveThemes(themeIds: Seq[ThemeId]): Future[Option[Seq[Theme]]] = {
      themeService
        .findAll()
        .map(_.filter(theme => themeIds.contains(theme.themeId)))
        .map(Some(_))
    }

    val maybeResult = for {
      sequence <- OptionT((sequenceCoordinator ? GetSequence(id, RequestContext.empty)).mapTo[Option[Sequence]])
      tags     <- OptionT(retrieveTags(sequence.tagIds))
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
          Context(
            operation = sequence.creationContext.operation,
            source = sequence.creationContext.source,
            location = sequence.creationContext.location,
            question = sequence.creationContext.question
          )
        ),
        tags = tags,
        themes = themes.map(theme => IndexedSequenceTheme(themeId = theme.themeId, translation = theme.translations)),
        proposals = sequence.proposalIds.map(IndexedProposalSequence(_)),
        searchable = sequence.searchable
      )
    }
    maybeResult.getOrElseF(Future.failed(new IllegalArgumentException(s"Sequence ${id.value} doesn't exist")))
  }

  override val groupId = "sequence-consumer"
}

object SequenceConsumerActor {
  def props(sequenceCoordinator: ActorRef,
            userService: UserService,
            tagService: TagService,
            themeService: ThemeService): Props =
    Props(new SequenceConsumerActor(sequenceCoordinator, userService, tagService, themeService))
  val name: String = "sequence-consumer"
}
