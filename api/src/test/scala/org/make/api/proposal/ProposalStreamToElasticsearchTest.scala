package org.make.api.proposal

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
import org.make.api.proposal.ProposalStreamToElasticsearchTest.{
  committableOffset,
  msgCreateOk,
  msgKo,
  msgUpdateOk,
  proposalElasticsearch
}
import org.make.api.technical.AvroSerializers
import org.make.core.{CirceFormatters, DateHelper, RequestContext}
import org.make.core.proposal.ProposalEvent.{
  ProposalAuthorInfo,
  ProposalEventWrapper,
  ProposalProposed,
  ProposalUpdated
}
import org.make.core.proposal._
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ProposalStreamToElasticsearchTest
    extends ShardingActorTest
    with MockitoSugar
    with ProposalStreamToElasticsearchComponent
    with ProposalSearchEngineComponent {

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
      when(elasticsearchAPI.findProposalById(any[ProposalId]))
        .thenReturn(Future.successful(Some(proposalElasticsearch)))
      when(elasticsearchAPI.indexProposal(any[IndexedProposal]))
        .thenReturn(Future.successful(Done))
      when(elasticsearchAPI.updateProposal(any[IndexedProposal]))
        .thenReturn(Future.successful(Done))

      val future = Source[CommittableMessage[String, AnyRef]](msgsOk)
        .via(proposalStreamToElasticsearch.esPush)
        .runWith(Sink.fold(Seq.empty[Done])(_ :+ _))
      val result: Seq[Done] = Await.result(future, 3.seconds)
      assert(!result.exists(_ != Done))
    }
    scenario("fail the KO data") {
      assertThrows[ClassCastException] {
        Await.result(
          Source[CommittableMessage[String, AnyRef]](msgsKo)
            .via(proposalStreamToElasticsearch.esPush)
            .runWith(Sink.fold(Seq.empty[Done])(_ :+ _)),
          3.seconds
        )
      }
    }
  }

  override val elasticsearchAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val proposalStreamToElasticsearch: ProposalStreamToElasticsearch =
    new ProposalStreamToElasticsearch(system, materializer)
}

object ProposalStreamToElasticsearchTest extends MockitoSugar with AvroSerializers with CirceFormatters {

  val proposalId: ProposalId = ProposalId(UUID.randomUUID.toString)
  val userId: UserId = UserId(UUID.randomUUID.toString)

  private val now = ZonedDateTime.now
  private val before = now.minusSeconds(10)
  private val valueCreate: GenericRecord =
    RecordFormat[ProposalEventWrapper].to(
      ProposalEventWrapper(
        version = 1,
        id = proposalId.value,
        date = before,
        eventType = ProposalProposed.getClass.getName,
        event = ProposalEventWrapper
          .wrapEvent(
            ProposalProposed(
              proposalId,
              "the-answer",
              RequestContext.empty,
              ProposalAuthorInfo(userId = userId, firstName = Some("John"), postalCode = None, age = Some(21)),
              userId,
              before,
              "The answer"
            )
          )
      )
    )
  private val valueUpdate: GenericRecord =
    RecordFormat[ProposalEventWrapper].to(
      ProposalEventWrapper(
        version = 1,
        id = proposalId.value,
        date = now,
        eventType = ProposalUpdated.getClass.getName,
        event = ProposalEventWrapper
          .wrapEvent(
            ProposalUpdated(
              proposalId,
              DateHelper.now(),
              RequestContext.empty,
              now,
              "42 is the answer to Life, the Universe, and Everything"
            )
          )
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

  val proposalElasticsearch: IndexedProposal =
    IndexedProposal(
      id = proposalId,
      userId = userId,
      content = "DummyContent",
      slug = "DummyContent",
      createdAt = before,
      updatedAt = None,
      votesAgree = Vote(key = "agree", qualifications = Seq()),
      votesDisagree = Vote(key = "disagree", qualifications = Seq()),
      votesNeutral = Vote(key = "neutral", qualifications = Seq()),
      proposalContext = ProposalContext(operation = None, location = None, source = None, question = None),
      author = Author(firstName = None, postalCode = None, age = None),
      themeId = None,
      tags = Seq(),
      trending = None,
      labels = Seq(),
      country = "FR",
      language = "fr",
      status = Pending.shortName
    )

  val msgCreateOk: CommittableMessage[String, AnyRef] =
    CommittableMessage(consumerRecordCreateOk, committableOffset)

  val msgUpdateOk: CommittableMessage[String, AnyRef] =
    CommittableMessage(consumerRecordUpdateOk, committableOffset)

  val msgKo: CommittableMessage[String, AnyRef] =
    CommittableMessage(consumerRecordKo, committableOffset)
}
