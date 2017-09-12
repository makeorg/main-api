package org.make.api.proposal

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink}
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.make.api.ActorSystemComponent
import org.make.api.extensions.KafkaConfiguration
import org.make.api.technical.ShortenedNames
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationExtension

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}

class ProposalEventStreamingActor
    extends Actor
    with ActorLogging
    with ActorSystemComponent
    with DefaultProposalSearchEngineComponent
    with ElasticsearchConfigurationExtension
    with DefaultProposalEventStreamingComponent
    with ShortenedNames {

  override def actorSystem: ActorSystem = context.system

  implicit private val executionContext: EC = ECGlobal
  implicit private val materializer: ActorMaterializer = ActorMaterializer()

  private val identityMapCapacity = 1000
  private val proposalConsumerSettings =
    ConsumerSettings(
      actorSystem,
      new StringDeserializer,
      new KafkaAvroDeserializer(
        new CachedSchemaRegistryClient(KafkaConfiguration(actorSystem).schemaRegistry, identityMapCapacity)
      )
    ).withBootstrapServers(KafkaConfiguration(actorSystem).connectionString)
      .withGroupId("stream-proposal-to-es")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  override def preStart(): Unit = {

    val futureFlow: Future[Done] = Consumer
      .committablePartitionedSource(
        proposalConsumerSettings,
        Subscriptions.topics(ProposalProducerActor.kafkaTopic(actorSystem))
      )
      .map {
        case (_, source) =>
          source
            .via(proposalEventStreaming.indexFlow)
            .toMat(Sink.ignore)(Keep.both)
            .run()
      }
      .mapAsyncUnordered(3) {
        case (_, done) => done
      }
      .runWith(Sink.foreach(msg => log.info("Streaming of one message: " + msg.toString)))

    futureFlow.onComplete {
      case Success(result) =>
        log.debug("Stream processed: {}", result)
      case Failure(e) =>
        log.error("Error in proposal streaming: {}", e)
        self ! PoisonPill
    }
  }

  override def receive: Receive = {
    case message => log.info(s"proposal streamer received $message")
  }

}

object ProposalEventStreamingActor {
  val props: Props = Props[ProposalEventStreamingActor]
  val name: String = "proposal-event-streaming-actor"

  val backoffPros: Props = BackoffSupervisor.props(
    Backoff
      .onStop(childProps = props, childName = name, minBackoff = 3.seconds, maxBackoff = 20.minutes, randomFactor = 0.2)
  )

  val backoffName = "proposal-event-streaming-actor-backoff"

}
