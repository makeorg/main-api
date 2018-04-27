package org.make.api.proposal

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.operation.OperationService
import org.make.api.proposal.ProposalIndexerActor.IndexProposal
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.semantic.SemanticService
import org.make.api.sequence.SequenceService
import org.make.api.tag.TagService
import org.make.api.tagtype.PersistentTagTypeService
import org.make.api.technical.KafkaConsumerActor
import org.make.api.user.UserService
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.sequence.SequenceId
import org.make.core.tag.{TagDisplay, TagId, TagType}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProposalConsumerActor(proposalCoordinatorService: ProposalCoordinatorService,
                            userService: UserService,
                            tagService: TagService,
                            persistentTagTypeService: PersistentTagTypeService,
                            sequenceService: SequenceService,
                            operationService: OperationService,
                            semanticService: SemanticService)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with KafkaConfigurationExtension
    with ActorLogging {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(ProposalProducerActor.topicKey)
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  val indexerActor: ActorRef = context.actorOf(ProposalIndexerActor.props, ProposalIndexerActor.name)

  implicit val timeout: Timeout = Timeout(5.seconds)

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case _: ProposalViewed => Future.successful {}
      case event: ProposalUpdated =>
        onSimilarProposalsUpdated(event.id, event.similarProposals)
        onCreateOrUpdate(event)
      case event: ProposalProposed => onCreateOrUpdate(event)
      case event: ProposalAccepted =>
        onSimilarProposalsUpdated(event.id, event.similarProposals)
        onCreateOrUpdate(event)
      case event: ProposalRefused     => onCreateOrUpdate(event)
      case event: ProposalPostponed   => onCreateOrUpdate(event)
      case event: ProposalVoted       => onCreateOrUpdate(event)
      case event: ProposalUnvoted     => onCreateOrUpdate(event)
      case event: ProposalQualified   => onCreateOrUpdate(event)
      case event: ProposalUnqualified => onCreateOrUpdate(event)
      case event: ProposalPatched     => onCreateOrUpdate(event)
      case event: ProposalAddedToOperation =>
        addToOperation(event)
        onCreateOrUpdate(event)
      case event: ProposalRemovedFromOperation =>
        removeFromOperation(event)
        onCreateOrUpdate(event)
      case _: ProposalLocked => Future.successful {}
      case event: SimilarProposalsAdded =>
        onSimilarProposalsUpdated(event.id, event.similarProposals.toSeq)
        Future.successful {}
    }

  }

  def onCreateOrUpdate(event: ProposalEvent): Future[Unit] = {
    retrieveAndShapeProposal(event.id).flatMap(indexOrUpdate)
  }

  def addToOperation(event: ProposalAddedToOperation): Future[Unit] = {
    proposalCoordinatorService.getProposal(event.id).flatMap {
      case Some(proposal) =>
        operationService.findOne(event.operationId).map { maybeOperation =>
          maybeOperation.foreach { operation =>
            val sequenceId: SequenceId = operation.countriesConfiguration
              .find(countryConfiguration => proposal.country.contains(countryConfiguration.countryCode))
              .getOrElse(operation.countriesConfiguration.head)
              .landingSequenceId
            sequenceService
              .addProposals(sequenceId, event.moderatorId, event.requestContext, Seq(event.id))
          }
        }
      case None => Future.successful[Unit] {}
    }
  }

  def removeFromOperation(event: ProposalRemovedFromOperation): Future[Unit] = {
    proposalCoordinatorService.getProposal(event.id).flatMap {
      case Some(proposal) =>
        operationService.findOne(event.operationId).map { maybeOperation =>
          maybeOperation.foreach { operation =>
            val sequenceId: SequenceId = operation.countriesConfiguration
              .find(countryConfiguration => proposal.country.contains(countryConfiguration.countryCode))
              .getOrElse(operation.countriesConfiguration.head)
              .landingSequenceId
            sequenceService
              .removeProposals(sequenceId, event.moderatorId, event.requestContext, Seq(event.id))
          }
        }
      case None => Future.successful[Unit] {}
    }
  }

  def onSimilarProposalsUpdated(proposalId: ProposalId, newSimilarProposals: Seq[ProposalId]): Unit = {
    val allIds = Seq(proposalId) ++ newSimilarProposals
    newSimilarProposals.foreach { id =>
      proposalCoordinatorService.updateDuplicates(UpdateDuplicatedProposalsCommand(id, allIds.filter(_ != id)))
    }
  }

  def indexOrUpdate(proposal: IndexedProposal): Future[Unit] = {
    indexerActor ! IndexProposal(proposal)
    semanticService.indexProposal(proposal)
  }

  private def retrieveAndShapeProposal(id: ProposalId): Future[IndexedProposal] = {

    def retrieveIndexedTags(tags: Seq[TagId]): Future[Option[Seq[IndexedTag]]] = {
      val tagTypes: Future[Seq[TagType]] = persistentTagTypeService.findAll()

      tagTypes.flatMap { tagTypes =>
        tagService
          .findByTagIds(tags)
          .map { tags =>
            Some(tags.map { tag =>
              if (tag.display == TagDisplay.Inherit) {
                val tagType: Seq[TagType] = tagTypes.filter(tagType => tagType.tagTypeId == tag.tagTypeId)
                IndexedTag(
                  tagId = tag.tagId,
                  label = tag.label,
                  display = tagType.nonEmpty && tagType.head.display.shortName == TagDisplay.Displayed.shortName
                )
              } else {
                IndexedTag(tagId = tag.tagId, label = tag.label, display = tag.display == TagDisplay.Displayed)
              }
            })
          }
      }
    }

    val maybeResult = for {
      proposal <- OptionT(proposalCoordinatorService.getProposal(id))
      user     <- OptionT(userService.getUser(proposal.author))
      tags     <- OptionT(retrieveIndexedTags(proposal.tags))
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
            operation = proposal.creationContext.operationId,
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
        country = proposal.country.getOrElse("FR"),
        language = proposal.language.getOrElse("fr"),
        themeId = proposal.theme,
        tags = tags,
        ideaId = proposal.idea,
        operationId = proposal.operation
      )
    }
    maybeResult.getOrElseF(Future.failed(new IllegalArgumentException(s"Proposal ${id.value} doesn't exist")))
  }

  override val groupId = "proposal-consumer"
}

object ProposalConsumerActor {
  def props(proposalCoordinatorService: ProposalCoordinatorService,
            userService: UserService,
            tagService: TagService,
            persistentTagTypeService: PersistentTagTypeService,
            sequenceService: SequenceService,
            operationService: OperationService,
            semanticService: SemanticService): Props =
    Props(
      new ProposalConsumerActor(
        proposalCoordinatorService,
        userService,
        tagService,
        persistentTagTypeService,
        sequenceService,
        operationService,
        semanticService
      )
    )
  val name: String = "proposal-consumer"
}
