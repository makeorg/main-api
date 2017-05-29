package org.make.api.proposition

import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.scaladsl.GraphDSL.Implicits._
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
import org.make.api.extensions.KafkaConfiguration
import org.make.api.technical.AvroSerializers
import org.make.api.technical.elasticsearch.{ElasticsearchAPIComponent, PropositionElasticsearch}
import org.make.core.proposition.PropositionEvent._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PropositionStreamToElasticsearchComponent { self: ElasticsearchAPIComponent =>

  def propositionStreamToElasticsearch: PropositionStreamToElasticsearch

  class PropositionStreamToElasticsearch(val actorSystem: ActorSystem, implicit val materializer: Materializer)
      extends StrictLogging
      with AvroSerializers {

    type FlowGraph =
      Graph[FlowShape[CommittableMessage[String, AnyRef], Done], NotUsed]
    type ElasticFlow = Flow[PropositionElasticsearch, Done, NotUsed]

    private val propositionConsumerSettings =
      ConsumerSettings(
        actorSystem,
        new StringDeserializer,
        new KafkaAvroDeserializer(new CachedSchemaRegistryClient(KafkaConfiguration(actorSystem).schemaRegistry, 1000))
      ).withBootstrapServers(KafkaConfiguration(actorSystem).connectionString)
        .withGroupId("stream-proposition-to-es")
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

    val proposeEvent: Flow[CommittableMessage[String, AnyRef], PropositionProposed, NotUsed] =
      Flow[CommittableMessage[String, AnyRef]].map { msg =>
        val event: PropositionEventWrapper =
          RecordFormat[PropositionEventWrapper].from(msg.record.value.asInstanceOf[GenericRecord])
        event.event.select[PropositionProposed]
      }.filter(_.isDefined)
        .map(_.get)

    val updateEvent: Flow[CommittableMessage[String, AnyRef], PropositionUpdated, NotUsed] =
      Flow[CommittableMessage[String, AnyRef]].map { msg =>
        val event: PropositionEventWrapper =
          RecordFormat[PropositionEventWrapper].from(msg.record.value.asInstanceOf[GenericRecord])
        event.event.select[PropositionUpdated]
      }.filter(_.isDefined)
        .map(_.get)

    val toPropositionEs: Flow[PropositionProposed, PropositionElasticsearch, NotUsed] =
      Flow[PropositionProposed].map(PropositionElasticsearch.shape(_).get)

    val getPropositionFromES: Flow[PropositionUpdated, Option[PropositionElasticsearch], NotUsed] =
      Flow[PropositionUpdated].mapAsync(1) { update =>
        elasticsearchAPI.getPropositionById(update.id).map {
          _.map(_.copy(updatedAt = update.updatedAt.toUTC, content = update.content))
        }
      }

    def filter[T]: Flow[Option[T], T, NotUsed] =
      Flow[Option[T]].filter(_.isDefined).map(_.get)

    val save: ElasticFlow =
      Flow[PropositionElasticsearch].mapAsync(1)(elasticsearchAPI.save)

    val update: ElasticFlow = Flow[PropositionElasticsearch].mapAsync(1)(elasticsearchAPI.updateProposition)

    val commitOffset: Flow[(CommittableMessage[String, AnyRef], Done), Done, NotUsed] =
      Flow[(CommittableMessage[String, AnyRef], Done)]
        .map(_._1.committableOffset)
        //        WIP:
        //        .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
        //          batch.updated(elem)
        //        }
        .mapAsync(1)(_.commitScaladsl())

    def debug[T]: Flow[T, T, NotUsed] = Flow[T].map { x =>
      logger.debug(s"[DEBUG][${x.getClass.getName}] " + x.toString)
      x
    }

    def esPush: FlowGraph = {
      Flow.fromGraph(GraphDSL.create() { implicit builder =>
        val bcast =
          builder.add(Broadcast[CommittableMessage[String, AnyRef]](3))
        val merge = builder.add(Merge[Done](2))
        bcast ~> proposeEvent ~> toPropositionEs ~> save ~> merge
        bcast ~> updateEvent ~> getPropositionFromES ~> filter[PropositionElasticsearch] ~> update ~> merge

        val zip = builder.add(Zip[CommittableMessage[String, AnyRef], Done]())
        bcast ~> zip.in0
        merge ~> zip.in1

        FlowShape(bcast.in, (zip.out ~> commitOffset).outlet)
      })
    }

    def run(): Future[Done] =
      Consumer
        .committableSource(
          propositionConsumerSettings,
          Subscriptions.topics(PropositionProducerActor.kafkaTopic(actorSystem))
        )
        .via(esPush)
        .runWith(Sink.foreach(msg => logger.info("Streaming of one message: " + msg.toString)))
  }

}
