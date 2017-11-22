package org.make.api.proposal

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.proposal.ProposalEvent._
import org.make.api.sequence
import org.make.api.sequence.SequenceService
import org.make.api.tag.TagService
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationExtension
import org.make.api.user.UserService
import org.make.core.RequestContext
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.{Tag, TagId}
import shapeless.Poly1

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class ProposalConsumerActor(proposalCoordinator: ActorRef,
                            userService: UserService,
                            tagService: TagService,
                            sequenceService: SequenceService)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with KafkaConfigurationExtension
    with DefaultProposalSearchEngineComponent
    with ElasticsearchConfigurationExtension
    with ActorLogging {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(ProposalProducerActor.topicKey)
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case _: ProposalViewed => Future.successful {}
      case event: ProposalUpdated =>
        onSimilarProposalsUpdated(event.id, event.similarProposals)
        onCreateOrUpdate(event)
      case event: ProposalProposed => onCreateOrUpdate(event)
      case event: ProposalAccepted =>
        onAddToSequence(event)
        onSimilarProposalsUpdated(event.id, event.similarProposals)
        onCreateOrUpdate(event)
      case event: ProposalRefused     => onCreateOrUpdate(event)
      case event: ProposalVoted       => onCreateOrUpdate(event)
      case event: ProposalUnvoted     => onCreateOrUpdate(event)
      case event: ProposalQualified   => onCreateOrUpdate(event)
      case event: ProposalUnqualified => onCreateOrUpdate(event)
      case _: ProposalLocked          => Future.successful {}
      case event: SimilarProposalsAdded =>
        onSimilarProposalsUpdated(event.id, event.similarProposals.toSeq)
        Future.successful {}
    }

  }

  object ToProposalEvent extends Poly1 {
    implicit val atProposalViewed: Case.Aux[ProposalViewed, ProposalViewed] = at(identity)
    implicit val atProposalUpdated: Case.Aux[ProposalUpdated, ProposalUpdated] = at(identity)
    implicit val atProposalProposed: Case.Aux[ProposalProposed, ProposalProposed] = at(identity)
    implicit val atProposalAccepted: Case.Aux[ProposalAccepted, ProposalAccepted] = at(identity)
    implicit val atProposalRefused: Case.Aux[ProposalRefused, ProposalRefused] = at(identity)
    implicit val atProposalVoted: Case.Aux[ProposalVoted, ProposalVoted] = at(identity)
    implicit val atProposalUnvoted: Case.Aux[ProposalUnvoted, ProposalUnvoted] = at(identity)
    implicit val atProposalQualified: Case.Aux[ProposalQualified, ProposalQualified] = at(identity)
    implicit val atProposalUnqualified: Case.Aux[ProposalUnqualified, ProposalUnqualified] = at(identity)
    implicit val atSimilarProposalsAdded: Case.Aux[SimilarProposalsAdded, SimilarProposalsAdded] = at(identity)
    implicit val atProposalLocked: Case.Aux[ProposalLocked, ProposalLocked] = at(identity)
  }

  def onCreateOrUpdate(event: ProposalEvent): Future[Unit] = {
    retrieveAndShapeProposal(event.id).flatMap(indexOrUpdate)
  }

  def onAddToSequence(event: ProposalAccepted): Future[Unit] = {
    (proposalCoordinator ? GetProposal(event.id, RequestContext.empty)).mapTo[Option[Proposal]].flatMap {
      case Some(proposal) =>
        if (proposal.creationContext.operation.nonEmpty) {
          sequenceService
            .search(
              Some(event.moderator),
              sequence
                .ExhaustiveSearchRequest(
                  context = Some(sequence.ContextFilterRequest(operation = proposal.creationContext.operation)),
                  limit = Some(1)
                )
                .toSearchQuery,
              event.requestContext
            )
            .map { sequenceResult =>
              sequenceResult.results.map(sequence => {
                sequenceService.addProposals(sequence.id, event.moderator, event.requestContext, Seq(event.id))
              })
            }
        } else {
          Future.successful[Unit] {}
        }
      case None => Future.successful[Unit] {}
    }
  }

  def onSimilarProposalsUpdated(proposalId: ProposalId, newSimilarProposals: Seq[ProposalId]): Unit = {
    val allIds = Seq(proposalId) ++ newSimilarProposals
    newSimilarProposals.foreach { id =>
      proposalCoordinator ! UpdateDuplicatedProposalsCommand(id, allIds.filter(_ != id))
    }
  }

  def indexOrUpdate(proposal: IndexedProposal): Future[Unit] = {
    log.debug(s"Indexing $proposal")
    elasticsearchProposalAPI
      .findProposalById(proposal.id)
      .flatMap {
        case None    => elasticsearchProposalAPI.indexProposal(proposal)
        case Some(_) => elasticsearchProposalAPI.updateProposal(proposal)
      }
      .map { _ =>
        }
  }

  private def retrieveAndShapeProposal(id: ProposalId): Future[IndexedProposal] = {

    def retrieveTags(tags: Seq[TagId]): Future[Option[Seq[Tag]]] = {
      tagService
        .findEnabledByTagIds(tags)
        .map(Some(_))
    }

    val maybeResult = for {
      proposal <- OptionT((proposalCoordinator ? GetProposal(id, RequestContext.empty)).mapTo[Option[Proposal]])
      user     <- OptionT(userService.getUser(proposal.author))
      tags     <- OptionT(retrieveTags(proposal.tags))
    } yield {
      IndexedProposal(
        id = proposal.proposalId,
        userId = proposal.author,
        content = proposal.content,
        slug = proposal.slug,
        status = proposal.status,
        createdAt = proposal.createdAt.get,
        updatedAt = proposal.updatedAt,
        votes = proposal.votes.map(IndexedVote.apply),
        context = Some(
          Context(
            operation = proposal.creationContext.operation,
            source = proposal.creationContext.source,
            location = proposal.creationContext.location,
            question = proposal.creationContext.question
          )
        ),
        trending = None,
        labels = proposal.labels.map(_.value),
        author = Author(
          firstName = user.firstName,
          postalCode = user.profile.flatMap(_.postalCode),
          age = user.profile
            .flatMap(_.dateOfBirth)
            .map(date => ChronoUnit.YEARS.between(date, LocalDate.now()).toInt)
        ),
        country = proposal.creationContext.country.getOrElse("FR"),
        language = proposal.creationContext.language.getOrElse("fr"),
        themeId = proposal.theme,
        tags = tags
      )
    }
    maybeResult.getOrElseF(Future.failed(new IllegalArgumentException(s"Proposal ${id.value} doesn't exist")))
  }

  override val groupId = "proposal-consumer"
}

object ProposalConsumerActor {
  def props(proposalCoordinator: ActorRef,
            userService: UserService,
            tagService: TagService,
            sequenceService: SequenceService): Props =
    Props(new ProposalConsumerActor(proposalCoordinator, userService, tagService, sequenceService))
  val name: String = "proposal-consumer"
}
