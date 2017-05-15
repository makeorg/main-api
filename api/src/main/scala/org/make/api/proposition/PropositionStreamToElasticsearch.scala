package org.make.api.proposition

import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Zip}
import akka.stream.{FlowShape, Graph, Materializer}
import akka.{Done, NotUsed}
import com.sksamuel.avro4s.RecordFormat
import com.typesafe.scalalogging.StrictLogging
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.make.api.Predef._
import org.make.api.elasticsearch.{ElasticsearchAPI, PropositionElasticsearch}
import org.make.api.kafka.{AvroSerializers, KafkaConfiguration, PropositionProducerActor}
import org.make.core.proposition.PropositionEvent._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PropositionStreamToElasticsearch(val actorSystem: ActorSystem, implicit val materializer: Materializer) extends StrictLogging with AvroSerializers {

  private val client = new CachedSchemaRegistryClient(KafkaConfiguration(actorSystem).schemaRegistry,1000)

  private val propositionConsumerSettings = ConsumerSettings(actorSystem, new StringDeserializer, new KafkaAvroDeserializer(client))
    .withBootstrapServers(KafkaConfiguration(actorSystem).connectionString)
    .withGroupId("stream-proposition-to-es")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  type FlowGraph = Graph[FlowShape[CommittableMessage[String, AnyRef], Done], NotUsed]

  def esPush(api: ElasticsearchAPI): FlowGraph = {
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val bcast = builder.add(Broadcast[CommittableMessage[String, AnyRef]](3))

        val proposeEvent: Flow[CommittableMessage[String, AnyRef], PropositionProposed, NotUsed] =
          Flow[CommittableMessage[String, AnyRef]]
            .map { msg =>
              val event: PropositionEventWrapper =
                RecordFormat[PropositionEventWrapper].from(msg.record.value.asInstanceOf[GenericRecord])
              event.event.select[PropositionProposed]
            }
            .filter(_.isDefined)
            .map(_.get)
        val updateEvent: Flow[CommittableMessage[String, AnyRef], PropositionUpdated, NotUsed] =
          Flow[CommittableMessage[String, AnyRef]]
            .map { msg =>
              val event: PropositionEventWrapper =
                RecordFormat[PropositionEventWrapper].from(msg.record.value.asInstanceOf[GenericRecord])
              event.event.select[PropositionUpdated]
            }
            .filter(_.isDefined)
            .map(_.get)

        val toPropositionEs: Flow[PropositionProposed, PropositionElasticsearch, NotUsed] =
          Flow[PropositionProposed].map(PropositionElasticsearch.shape(_).get)
        val getPropositionFromES: Flow[PropositionUpdated, Option[PropositionElasticsearch], NotUsed] =
          Flow[PropositionUpdated].mapAsync(1) { update =>
            api.getPropositionById(update.id).map {
              _.map(_.copy(updatedAt = update.updatedAt.toUTC, content = update.content))
            }
          }

        def filter[T]: Flow[Option[T], T, NotUsed] =
          Flow[Option[T]].filter(_.isDefined).map(_.get)

        val save: Flow[PropositionElasticsearch, Done, NotUsed] =
          Flow[PropositionElasticsearch].mapAsync(1)(api.save)
        val update: Flow[PropositionElasticsearch, Done, NotUsed] =
          Flow[PropositionElasticsearch].mapAsync(1)(api.updateProposition)

        val commitOffset: Flow[(CommittableMessage[String, AnyRef], Done), Done, NotUsed] =
          Flow[(CommittableMessage[String, AnyRef], Done)].map(_._1.committableOffset)
            .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
              batch.updated(elem)
            }
            .mapAsync(1)(_.commitScaladsl())

        val merge = builder.add(Merge[Done](2))
        val zip = builder.add(Zip[CommittableMessage[String, AnyRef], Done]())

        bcast                                                                                                ~> zip.in0
        bcast ~> proposeEvent ~> toPropositionEs                                          ~> save   ~> merge
        bcast ~> updateEvent  ~> getPropositionFromES ~> filter[PropositionElasticsearch] ~> update ~> merge
                                                                                                       merge ~> zip.in1

        FlowShape(bcast.in, (zip.out ~> commitOffset).outlet)
      })
  }

  def run(api: ElasticsearchAPI): Future[Done] =
    Consumer.committableSource(propositionConsumerSettings, Subscriptions.topics(PropositionProducerActor.kafkaTopic(actorSystem)))
      .via(esPush(api))
      .runWith(Sink.foreach(msg => logger.info("Streaming of one message: " + msg.toString)))
}

object PropositionStreamToElasticsearch {
  def stream(actorSystem: ActorSystem, materializer: Materializer) = new PropositionStreamToElasticsearch(actorSystem, materializer)
}