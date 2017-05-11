package org.make.api.proposition

import java.util.UUID

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

//case class CommittableOffsetWrapper[T](record: T, committableOffset: CommittableOffset)

class PropositionStreamToElasticsearch(val actorSystem: ActorSystem, implicit val materializer: Materializer) extends StrictLogging with AvroSerializers {


  private val client = new CachedSchemaRegistryClient(KafkaConfiguration(actorSystem).schemaRegistry,1000)

  private val propositionConsumerSettings = ConsumerSettings(actorSystem, new StringDeserializer, new KafkaAvroDeserializer(client))
    .withBootstrapServers(KafkaConfiguration(actorSystem).connectionString)
    .withGroupId("stream-proposition-to-es")
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  type FlowGraph = Graph[FlowShape[CommittableMessage[String, AnyRef], Done], NotUsed]

//  type UpdatedPropositionFlowGraph = Graph[FlowShape[PropositionUpdated, PropositionElasticsearch], NotUsed]
//
//  val updatedProposition: UpdatedPropositionFlowGraph = {
//    Flow.fromGraph(
//      GraphDSL.create() { implicit builder =>
//        import GraphDSL.Implicits._
//
//        val bcast = builder.add(Broadcast[PropositionUpdated](2))
//
//        val getPropositionFromES: Flow[PropositionUpdated, Option[PropositionElasticsearch], NotUsed] =
//          Flow[PropositionUpdated].mapAsync(1)(p => ElasticsearchAPI.api.getPropositionById(p.id))
//
//        def filter[T]: Flow[Option[T], T, NotUsed] =
//          Flow[Option[T]].filter(_.isDefined).map(_.get)
//
//        val zip = builder.add(Zip[PropositionUpdated, PropositionElasticsearch]())
//
//        val x: Flow[(PropositionUpdated, PropositionElasticsearch), PropositionElasticsearch, NotUsed] = {
//          Flow[(PropositionUpdated, PropositionElasticsearch)].map { xx =>
//            val update = xx._1
//            val actual = xx._2
//            logger.debug("ZIPPING update: " + update + " actual: " + actual)
//            PropositionElasticsearch(
//              id = actual.id,
//              citizenId = actual.citizenId,
//              createdAt = actual.createdAt.toUTC,
//              updatedAt = update.updatedAt.toUTC,
//              content = update.content,
//              nbVotesAgree = actual.nbVotesAgree,
//              nbVotesDisagree = actual.nbVotesDisagree,
//              nbVotesUnsure = actual.nbVotesUnsure
//            )
//          }
//        }
//
//        bcast                                                             ~> zip.in0
//        bcast ~> getPropositionFromES ~> filter[PropositionElasticsearch] ~> zip.in1
//
//        FlowShape(bcast.in, (zip.out ~> x).outlet)
//      })
//  }

  val esPush: FlowGraph = {
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val bcast = builder.add(Broadcast[CommittableMessage[String, AnyRef]](2))

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
          Flow[PropositionProposed].map { p =>
            PropositionElasticsearch(
              id = UUID.fromString(p.id.value),
              citizenId = UUID.fromString(p.citizenId.value),
              createdAt = p.createdAt.toUTC,
              updatedAt = p.createdAt.toUTC,
              content = p.content,
              nbVotesAgree = 0,
              nbVotesDisagree = 0,
              nbVotesUnsure = 0
            )
          }

        val getPropositionFromES: Flow[PropositionUpdated, Option[PropositionElasticsearch], NotUsed] =
          Flow[PropositionUpdated].mapAsync(1) { update =>
            ElasticsearchAPI.api.getPropositionById(update.id).map {
              _.map {
                p =>
                  logger.debug("ZIPPING update: " + update + " actual: " + p)
                  PropositionElasticsearch(
                    id = p.id,
                    citizenId = p.citizenId,
                    createdAt = p.createdAt.toUTC,
                    updatedAt = update.updatedAt.toUTC,
                    content = update.content,
                    nbVotesAgree = p.nbVotesAgree,
                    nbVotesDisagree = p.nbVotesDisagree,
                    nbVotesUnsure = p.nbVotesUnsure
                  )
              }
            }
          }

        def filter[T]: Flow[Option[T], T, NotUsed] =
          Flow[Option[T]].filter(_.isDefined).map(_.get)

        val save: Flow[PropositionElasticsearch, Done, NotUsed] =
          Flow[PropositionElasticsearch].mapAsync(1)(ElasticsearchAPI.api.save)
        val update: Flow[PropositionElasticsearch, Done, NotUsed] =
          Flow[PropositionElasticsearch].mapAsync(1)(ElasticsearchAPI.api.updateProposition)

        val merge = builder.add(Merge[Done](2))

        bcast ~> proposeEvent ~> toPropositionEs                                          ~> save   ~> merge
        bcast ~> updateEvent  ~> getPropositionFromES ~> filter[PropositionElasticsearch] ~> update ~> merge

        FlowShape(bcast.in, merge.out)
      })
  }

  val commitOffset: FlowGraph = {
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val bcast = builder.add(Broadcast[CommittableMessage[String, AnyRef]](2))
        val zip = builder.add(Zip[CommittableMessage[String, AnyRef], Done]())

        val commitOffset: Flow[(CommittableMessage[String, AnyRef], Done), Done, NotUsed] =
          Flow[(CommittableMessage[String, AnyRef], Done)].map(_._1.committableOffset)
            .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
              batch.updated(elem)
            }
            .mapAsync(1)(_.commitScaladsl())

        bcast           ~> zip.in0
        bcast ~> esPush ~> zip.in1

        FlowShape(bcast.in, (zip.out ~> commitOffset).outlet)
      })
  }

  def run: Future[Done] =
    Consumer.committableSource(propositionConsumerSettings, Subscriptions.topics(PropositionProducerActor.kafkaTopic(actorSystem)))
      .via(esPush)
      .runWith(Sink.foreach(msg => logger.info("Stream done: " + msg.toString)))
}

object PropositionStreamToElasticsearch {
  def stream(actorSystem: ActorSystem, materializer: Materializer) = new PropositionStreamToElasticsearch(actorSystem, materializer)
}