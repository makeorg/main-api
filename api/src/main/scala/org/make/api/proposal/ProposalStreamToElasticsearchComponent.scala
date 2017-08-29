package org.make.api.proposal

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
import org.make.core.DateHelper._
import org.make.api.extensions.KafkaConfiguration
import org.make.api.technical.AvroSerializers
import org.make.core.proposal.ProposalEvent._
import org.make.core.proposal.indexed.IndexedProposal

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalStreamToElasticsearchComponent { self: ProposalSearchEngineComponent =>

  def proposalStreamToElasticsearch: ProposalStreamToElasticsearch

  class ProposalStreamToElasticsearch(val actorSystem: ActorSystem, implicit val materializer: Materializer)
      extends StrictLogging
      with AvroSerializers {

    type FlowGraph =
      Graph[FlowShape[CommittableMessage[String, AnyRef], Done], NotUsed]
    type ElasticFlow = Flow[IndexedProposal, Done, NotUsed]

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

    val proposeEvent: Flow[CommittableMessage[String, AnyRef], ProposalProposed, NotUsed] =
      Flow[CommittableMessage[String, AnyRef]].map { msg =>
        val event: ProposalEventWrapper =
          RecordFormat[ProposalEventWrapper].from(msg.record.value.asInstanceOf[GenericRecord])
        event.event.select[ProposalProposed]
      }.filter(_.isDefined)
        .map(_.get)

    val updateEvent: Flow[CommittableMessage[String, AnyRef], ProposalUpdated, NotUsed] =
      Flow[CommittableMessage[String, AnyRef]].map { msg =>
        val event: ProposalEventWrapper =
          RecordFormat[ProposalEventWrapper].from(msg.record.value.asInstanceOf[GenericRecord])
        event.event.select[ProposalUpdated]
      }.filter(_.isDefined)
        .map(_.get)

    val toProposalEs: Flow[ProposalProposed, IndexedProposal, NotUsed] =
      Flow[ProposalProposed].map(IndexedProposal.apply)

    val getProposalFromES: Flow[ProposalUpdated, Option[IndexedProposal], NotUsed] =
      Flow[ProposalUpdated].mapAsync(1) { update =>
        elasticsearchAPI.findProposalById(update.id).map {
          _.map(_.copy(updatedAt = Some(update.updatedAt.toUTC), content = update.content))
        }
      }

    def filter[T]: Flow[Option[T], T, NotUsed] =
      Flow[Option[T]].filter(_.isDefined).map(_.get)

    val save: ElasticFlow =
      Flow[IndexedProposal].mapAsync(1)(elasticsearchAPI.indexProposal)

    val update: ElasticFlow = Flow[IndexedProposal].mapAsync(1)(elasticsearchAPI.updateProposal)

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
        bcast ~> proposeEvent ~> toProposalEs      ~> save                    ~> merge
        bcast ~> updateEvent  ~> getProposalFromES ~> filter[IndexedProposal] ~> update ~> merge

        val zip = builder.add(Zip[CommittableMessage[String, AnyRef], Done]())
        bcast ~> zip.in0
        merge ~> zip.in1

        FlowShape(bcast.in, (zip.out ~> commitOffset).outlet)
      })
    }

    def run(): Future[Done] =
      Consumer
        .committableSource(
          proposalConsumerSettings,
          Subscriptions.topics(ProposalProducerActor.kafkaTopic(actorSystem))
        )
        .via(esPush)
        .runWith(Sink.foreach(msg => logger.info("Streaming of one message: " + msg.toString)))
  }

}
