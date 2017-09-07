package org.make.api.proposal

import akka.kafka.ConsumerMessage.CommittableMessage
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Partition, Zip}
import akka.{Done, NotUsed}
import com.sksamuel.avro4s.RecordFormat
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.GenericRecord
import org.make.api.proposal.DefaultProposalEventStreamingComponent.{ApplyFlow, ElasticFlow}
import org.make.api.technical.AvroSerializers
import org.make.api.technical.mailjet.MailJet.FlowGraph
import org.make.core.proposal.ProposalEvent
import org.make.core.proposal.ProposalEvent._
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.reference.Tag
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait ProposalEventStreamingComponent {
  def proposalEventStreaming: ProposalEventStreaming
}

trait ProposalEventStreaming {
  def indexFlow: FlowGraph
}

trait DefaultProposalEventStreamingComponent extends ProposalEventStreamingComponent {
  self: ProposalSearchEngineComponent =>

  override val proposalEventStreaming =
    new ProposalEventStreaming with AvroSerializers with StrictLogging {

      object HandledMessages extends Poly1 {
        implicit val atProposaEvent: Case.Aux[ProposalProposed, ProposalProposed] = at(identity)
        implicit val atValidateEvent: Case.Aux[ProposalAccepted, ProposalAccepted] = at(identity)
        implicit val atUpdateEvent: Case.Aux[ProposalUpdated, ProposalUpdated] = at(identity)
        implicit val atViewEvent: Case.Aux[ProposalViewed, ProposalViewed] = at(identity)
      }

      val commitOffset: Flow[(CommittableMessage[String, AnyRef], Done), Done, NotUsed] =
        Flow[(CommittableMessage[String, AnyRef], Done)].map {
          case (message, _) => message.committableOffset
        }
        //        WIP:
        //        .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
        //          batch.updated(elem)
        //        }
          .mapAsync(1)(_.commitScaladsl())

      val applyProposalAccepted: ApplyFlow[ProposalAccepted] =
        Flow[(Option[IndexedProposal], ProposalAccepted)].map {
          case (Some(proposal), event) =>
            var result: IndexedProposal =
              proposal.copy(
                updatedAt = Some(event.eventDate),
                labels = event.labels.map(_.value),
                status = Accepted,
                themeId = event.theme,
                tags = event.tags.map(Tag(_, ""))
              )
            result = event.edition.map { edition =>
              result.copy(content = edition.newVersion)
            }.getOrElse(result)
            Some(result)
          case (None, _) => None
        }

      val applyProposalUpdated: ApplyFlow[ProposalUpdated] =
        Flow[(Option[IndexedProposal], ProposalUpdated)].map {
          case (Some(proposal), event) =>
            Some(proposal.copy(updatedAt = Some(event.eventDate), content = event.content))
          case (None, _) => None
        }

      val ignoreEvent: Flow[ProposalEvent, Done, NotUsed] = Flow[ProposalEvent].map(_ => Done)

      def toEvent[T <: ProposalEvent]: Flow[ProposalEvent, T, NotUsed] = Flow[ProposalEvent].map(_.asInstanceOf[T])

      val toProposalEs: Flow[ProposalProposed, IndexedProposal, NotUsed] =
        Flow[ProposalProposed].map(IndexedProposal.apply)

      val unwrapEvent: Flow[CommittableMessage[String, AnyRef], ProposalEvent, NotUsed] =
        Flow[CommittableMessage[String, AnyRef]].map { msg =>
          val event: ProposalEventWrapper =
            RecordFormat[ProposalEventWrapper].from(msg.record.value.asInstanceOf[GenericRecord])
          event.event.fold(HandledMessages)
        }

      def retrieveProposal[T <: ProposalEvent]: Flow[T, (Option[IndexedProposal], T), NotUsed] =
        Flow[T].mapAsync(1) { event =>
          elasticsearchAPI.findProposalById(event.id).map(proposal => (proposal, event))
        }

      def save: ElasticFlow =
        Flow[IndexedProposal].mapAsync(1)(elasticsearchAPI.indexProposal)

      def update: Flow[Option[IndexedProposal], Done, NotUsed] =
        Flow[Option[IndexedProposal]]
          .mapAsync(1)(_.map(elasticsearchAPI.updateProposal).getOrElse(Future.successful(Done)))

      override def indexFlow: FlowGraph = {
        val partitions: ProposalEvent => Int = {
          case _: ProposalProposed => 0
          case _: ProposalAccepted => 1
          case _: ProposalUpdated  => 2
          case _: ProposalViewed   => 3
        }

        Flow.fromGraph(GraphDSL.create() { implicit builder =>
          val baseBroadcast = builder.add(Broadcast[CommittableMessage[String, AnyRef]](2))
          val eventsCount = 4

          val merge = builder.add(Merge[Done](eventsCount))

          val partitioner = builder.add(Partition[ProposalEvent](eventsCount, partitions))
          baseBroadcast ~> unwrapEvent ~> partitioner.in

          partitioner ~> toEvent[ProposalProposed] ~> toProposalEs ~> save ~> merge

          partitioner ~>
            toEvent[ProposalAccepted] ~>
            retrieveProposal[ProposalAccepted] ~>
            applyProposalAccepted ~>
            update ~>
            merge

          partitioner ~>
            toEvent[ProposalUpdated] ~>
            retrieveProposal[ProposalUpdated] ~>
            applyProposalUpdated ~>
            update ~>
            merge

          partitioner ~> toEvent[ProposalViewed] ~> ignoreEvent ~> merge

          val zip = builder.add(Zip[CommittableMessage[String, AnyRef], Done]())
          baseBroadcast ~> zip.in0
          merge         ~> zip.in1

          FlowShape(baseBroadcast.in, (zip.out ~> commitOffset).outlet)
        })
      }

    }
}

object DefaultProposalEventStreamingComponent {
  type FlowGraph =
    Graph[FlowShape[CommittableMessage[String, AnyRef], Done], NotUsed]

  type ElasticFlow = Flow[IndexedProposal, Done, NotUsed]

  type ApplyFlow[T <: ProposalEvent] =
    Flow[(Option[IndexedProposal], T), Option[IndexedProposal], NotUsed]
}
