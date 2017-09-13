package org.make.api.proposal

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import akka.actor.{ActorRef, Props}
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.technical.KafkaConsumerActor
import org.make.core.proposal._
import org.make.core.proposal.ProposalEvent._
import shapeless.Poly1
import akka.pattern.ask
import org.joda.time.Years
import org.make.api.user.UserService
import org.make.core.RequestContext
import org.make.core.proposal.indexed._

import scala.concurrent.Future

class ProposalConsumerActor(proposalCoordinator: ActorRef, userService: UserService)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with KafkaConfigurationExtension {
  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(ProposalProducerActor.topicKey)
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case _: ProposalViewed       => Future.successful {}
      case event: ProposalUpdated  => onUpdate(event)
      case event: ProposalProposed => onCreate(event)
      case event: ProposalAccepted => onUpdate(event)
    }

  }

  object ToProposalEvent extends Poly1 {
    implicit val atProposalViewed: Case.Aux[ProposalViewed, ProposalViewed] = at(identity)
    implicit val atProposalUpdated: Case.Aux[ProposalUpdated, ProposalUpdated] = at(identity)
    implicit val atProposalProposed: Case.Aux[ProposalProposed, ProposalProposed] = at(identity)
    implicit val atProposalAccepted: Case.Aux[ProposalAccepted, ProposalAccepted] = at(identity)
  }

  def onUpdate(event: ProposalEvent): Future[Unit] = {
    val proposal = retrieveAndShapeProposal(event.id)
  }

  def onCreate(event: ProposalEvent): Future[Unit] = {
    val proposal = retrieveAndShapeProposal(event.id)
  }

  private def retrieveAndShapeProposal(id: ProposalId): Future[IndexedProposal] = {
    for {
      proposal <- (proposalCoordinator ? GetProposal(id, RequestContext.empty)).mapTo[Proposal]
      user     <- userService.getUser(proposal.author)
    } yield {
      // TODO: missing tags and qualifs
      IndexedProposal(
        id = proposal.proposalId,
        userId = proposal.author,
        content = proposal.content,
        slug = proposal.slug,
        status = proposal.status,
        createdAt = proposal.createdAt.get,
        updatedAt = proposal.updatedAt,
        votesAgree = Vote(key = VoteKey.Agree, qualifications = Seq()),
        votesDisagree = Vote(key = VoteKey.Disagree, qualifications = Seq()),
        votesNeutral = Vote(key = VoteKey.Neutral, qualifications = Seq()),
        proposalContext = ProposalContext(
          operation = proposal.creationContext.operation,
          source = proposal.creationContext.source,
          location = proposal.creationContext.location,
          question = proposal.creationContext.question
        ),
        trending = None,
        labels = proposal.labels.map(_.value),
        author = Author(
          firstName = user.flatMap(_.firstName),
          postalCode = user.flatMap(_.profile).flatMap(_.postalCode),
          age = user
            .flatMap(_.profile)
            .flatMap(_.dateOfBirth)
            .map(date => ChronoUnit.YEARS.between(LocalDate.now(), date).toInt)
        ),
        country = proposal.creationContext.country.getOrElse("FR"),
        language = proposal.creationContext.language.getOrElse("fr"),
        themeId = proposal.theme,
        tags = Seq()
      )
    }
  }

  override val groupId = "proposal-consumer"
}

object ProposalConsumerActor {
  val props: Props = Props[ProposalConsumerActor]
  val name: String = "proposal-consumer"
}
