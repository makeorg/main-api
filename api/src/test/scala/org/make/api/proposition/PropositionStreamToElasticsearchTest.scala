package org.make.api.proposition

import java.time.ZonedDateTime
import java.util.UUID

import akka.Done
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.avro4s.RecordFormat
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.make.api.ShardingActorTest
import org.make.api.proposition.PropositionStreamToElasticsearchTest.{
  committableOffset,
  msgCreateOk,
  msgKo,
  msgUpdateOk,
  propositionElasticsearch
}
import org.make.api.technical.AvroSerializers
import org.make.api.technical.elasticsearch.{ElasticsearchAPIComponent, PropositionElasticsearch}
import org.make.core.CirceFormatters
import org.make.core.user.UserId
import org.make.core.proposition.PropositionEvent.{PropositionEventWrapper, PropositionProposed, PropositionUpdated}
import org.make.core.proposition.PropositionId
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class PropositionStreamToElasticsearchTest
    extends ShardingActorTest
    with MockitoSugar
    with PropositionStreamToElasticsearchComponent
    with ElasticsearchAPIComponent {

  implicit val materializer: ActorMaterializer = ActorMaterializer()(system)

  val msgsOk: immutable.Iterable[CommittableMessage[String, AnyRef]] =
    immutable.Seq(msgCreateOk, msgUpdateOk, msgCreateOk, msgUpdateOk, msgCreateOk, msgCreateOk)
  val msgsKo: immutable.Iterable[CommittableMessage[String, AnyRef]] =
    immutable.Seq(msgKo)

  feature("Stream to Elasticsearch") {
    scenario("shape the OK data") {
      when(committableOffset.commitScaladsl())
        .thenReturn(Future.successful(Done))
//      when(committableOffsetBatch.commitScaladsl()).thenReturn(Future.successful(Done))
//      when(committableOffsetBatch.updated(any[CommittableOffset])).thenReturn(committableOffsetBatch)
      when(elasticsearchAPI.getPropositionById(any[PropositionId]))
        .thenReturn(Future.successful(Some(propositionElasticsearch)))
      when(elasticsearchAPI.save(any[PropositionElasticsearch]))
        .thenReturn(Future.successful(Done))
      when(elasticsearchAPI.updateProposition(any[PropositionElasticsearch]))
        .thenReturn(Future.successful(Done))

      val future = Source[CommittableMessage[String, AnyRef]](msgsOk)
        .via(propositionStreamToElasticsearch.esPush)
        .runWith(Sink.fold(Seq.empty[Done])(_ :+ _))
      val result: Seq[Done] = Await.result(future, 3.seconds)
      assert(!result.exists(_ != Done))
    }
    scenario("fail the KO data") {
      assertThrows[ClassCastException] {
        Await.result(
          Source[CommittableMessage[String, AnyRef]](msgsKo)
            .via(propositionStreamToElasticsearch.esPush)
            .runWith(Sink.fold(Seq.empty[Done])(_ :+ _)),
          3.seconds
        )
      }
    }
  }

  override val elasticsearchAPI: ElasticsearchAPI = mock[ElasticsearchAPI]
  override val propositionStreamToElasticsearch: PropositionStreamToElasticsearch =
    new PropositionStreamToElasticsearch(system, materializer)
}

object PropositionStreamToElasticsearchTest extends MockitoSugar with AvroSerializers with CirceFormatters {

  val propositionId: PropositionId = PropositionId(UUID.randomUUID.toString)
  val userId: UserId = UserId(UUID.randomUUID.toString)

  private val now = ZonedDateTime.now
  private val before = now.minusSeconds(10)
  private val valueCreate: GenericRecord =
    RecordFormat[PropositionEventWrapper].to(
      PropositionEventWrapper(
        version = 1,
        id = propositionId.value,
        date = before,
        eventType = PropositionProposed.getClass.getName,
        event = PropositionEventWrapper.wrapEvent(PropositionProposed(propositionId, userId, before, "The answer"))
      )
    )
  private val valueUpdate: GenericRecord =
    RecordFormat[PropositionEventWrapper].to(
      PropositionEventWrapper(
        version = 1,
        id = propositionId.value,
        date = now,
        eventType = PropositionUpdated.getClass.getName,
        event = PropositionEventWrapper
          .wrapEvent(PropositionUpdated(propositionId, now, "42 is the answer to Life, the Universe, and Everything"))
      )
    )
  private val consumerRecordCreateOk =
    new ConsumerRecord[String, AnyRef]("topic", 0, 0, "key", valueCreate)
  private val consumerRecordUpdateOk =
    new ConsumerRecord[String, AnyRef]("topic", 0, 0, "key", valueUpdate)
  private val consumerRecordKo =
    new ConsumerRecord[String, AnyRef]("topic", 0, 0, "Hello", "World")

  val committableOffset: CommittableOffset = mock[CommittableOffset]
//  val committableOffsetBatch: CommittableOffsetBatch = mock[CommittableOffsetBatch]

  val propositionElasticsearch: PropositionElasticsearch =
    PropositionElasticsearch(
      UUID.fromString(propositionId.value),
      UUID.fromString(userId.value),
      before,
      now,
      "The answer",
      0,
      0,
      0
    )

  val msgCreateOk: CommittableMessage[String, AnyRef] =
    CommittableMessage(consumerRecordCreateOk, committableOffset)

  val msgUpdateOk: CommittableMessage[String, AnyRef] =
    CommittableMessage(consumerRecordUpdateOk, committableOffset)

  val msgKo: CommittableMessage[String, AnyRef] =
    CommittableMessage(consumerRecordKo, committableOffset)
}
