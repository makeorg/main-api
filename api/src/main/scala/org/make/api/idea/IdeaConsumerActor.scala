package org.make.api.idea

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.idea.IdeaEvent.{IdeaCreatedEvent, IdeaEventWrapper, IdeaUpdatedEvent}
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationExtension
import org.make.core.idea.IdeaId
import org.make.core.idea.indexed.IndexedIdea
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class IdeaConsumerActor(ideaService: IdeaService)
  extends KafkaConsumerActor[IdeaEventWrapper]
    with KafkaConfigurationExtension
    with DefaultIdeaSearchEngineComponent
    with ElasticsearchConfigurationExtension
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
      .findIdeaById(idea.id)
      .flatMap {
        case None    => elasticsearchIdeaAPI.indexIdea(idea)
        case Some(_) => elasticsearchIdeaAPI.updateIdea(idea)
      }
      .map { _ =>
      }
  }

  private def retrieveAndShapeIdea(id: IdeaId): Future[IndexedIdea] = {


    val maybeResult = for {
      idea <- OptionT(ideaService.fetchOne(id))
    } yield {
      IndexedIdea.createFromIdea(idea)
    }

    maybeResult.getOrElseF(Future.failed(new IllegalArgumentException(s"Idea ${id.value} doesn't exist")))
  }

  override val groupId = "idea-consumer"
}

object IdeaConsumerActor {
  def props(ideaService: IdeaService): Props =
    Props(new IdeaConsumerActor(ideaService))
  val name: String = "idea-consumer"
}
