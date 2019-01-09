/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.proposal

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.{MailJetTemplateConfigurationExtension, MakeSettingsExtension}
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.question.QuestionService
import org.make.api.technical.crm.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor, TimeSettings}
import org.make.api.user.UserService
import org.make.core.reference.{Country, Language}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProposalEmailConsumer(userService: UserService,
                            proposalCoordinatorService: ProposalCoordinatorService,
                            questionService: QuestionService)
    extends KafkaConsumerActor[ProposalEventWrapper]
    with MakeSettingsExtension
    with ActorEventBusServiceComponent
    with MailJetTemplateConfigurationExtension
    with ActorLogging {

  override protected lazy val kafkaTopic: String = ProposalProducerActor.topicKey
  override protected val format: RecordFormat[ProposalEventWrapper] = RecordFormat[ProposalEventWrapper]
  override val groupId = "proposal-email"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: ProposalEventWrapper): Future[Unit] = {
    message.event.fold(ToProposalEvent) match {
      case event: ProposalAccepted             => handleProposalAccepted(event)
      case event: ProposalRefused              => handleProposalRefused(event)
      case event: ProposalPostponed            => handleProposalPostponed(event)
      case event: ProposalViewed               => doNothing(event)
      case event: ProposalUpdated              => doNothing(event)
      case event: ReindexProposal              => doNothing(event)
      case event: ProposalProposed             => doNothing(event)
      case event: ProposalVoted                => doNothing(event)
      case event: ProposalUnvoted              => doNothing(event)
      case event: ProposalQualified            => doNothing(event)
      case event: ProposalUnqualified          => doNothing(event)
      case event: SimilarProposalsAdded        => doNothing(event)
      case event: ProposalLocked               => doNothing(event)
      case event: ProposalPatched              => doNothing(event)
      case event: ProposalAddedToOperation     => doNothing(event)
      case event: ProposalRemovedFromOperation => doNothing(event)
      case event: ProposalAnonymized           => doNothing(event)
    }

  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    if (event.sendValidationEmail) {
      // OptionT[Future, Unit] is some kind of monad wrapper to be able to unwrap options with no boilerplate
      // it allows here to have a for-comprehension on methods returning Future[Option[_]]
      // Do not use unless it really simplifies the code readability
      val country: String = event.requestContext.country.map(_.value).getOrElse("")

      val futureQuestionSlug: Future[String] = event.question match {
        case Some(questionId) =>
          questionService.getQuestion(questionId).map(_.map(_.slug).getOrElse(s"core.$country"))
        case None => Future.successful(s"core.$country")
      }

      futureQuestionSlug.map { questionSlug =>
        val maybePublish: OptionT[Future, Unit] = for {
          proposal <- OptionT(proposalCoordinatorService.getProposal(event.id))
          user     <- OptionT(userService.getUser(proposal.author))
        } yield {
          val country: Country = proposal.country.getOrElse(user.country)
          val language: Language = proposal.language.getOrElse(user.language)
          val templateConfiguration =
            mailJetTemplateConfiguration.proposalAccepted(questionSlug, country, language, user.isOrganisation)
          if (user.emailVerified && templateConfiguration.enabled) {
            eventBusService.publish(
              SendEmail.create(
                templateId = Some(templateConfiguration.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(
                    name = Some(mailJetTemplateConfiguration.fromName),
                    email = mailJetTemplateConfiguration.from
                  )
                ),
                variables = Some(
                  Map(
                    "proposal_url" -> s"${mailJetTemplateConfiguration.getFrontUrl()}?utm_source=crm&utm_medium=email&utm_campaign=core&utm_term=publication&utm_content=cta_share#/${proposal.country
                      .getOrElse("FR")}/proposal/${proposal.proposalId.value}/${proposal.slug}",
                    "proposal_text" -> proposal.content,
                    "firstname" -> user.fullName.getOrElse(""),
                    "operation" -> event.operation.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse(""),
                    "organisation_name" -> user.organisationName.getOrElse("")
                  )
                ),
                customCampaign = templateConfiguration.customCampaign,
                monitoringCategory = templateConfiguration.monitoringCategory
              )
            )
          }
        }
        maybePublish.getOrElseF(
          Future.failed(
            new IllegalStateException(s"proposal or user not found or user not verified for proposal ${event.id.value}")
          )
        )
      }
    } else {
      Future.successful[Unit] {}
    }

  }

  def handleProposalRefused(event: ProposalRefused): Future[Unit] = {
    if (event.sendRefuseEmail) {
      // OptionT[Future, Unit] is some kind of monad wrapper to be able to unwrap options with no boilerplate
      // it allows here to have a for-comprehension on methods returning Future[Option[_]]
      // Do not use unless it really simplifies the code readability
      val country: String = event.requestContext.country.map(_.value).getOrElse("")

      val futureQuestionSlug: Future[String] = (for {
        questionId <- OptionT(proposalCoordinatorService.getProposal(event.id).map(_.flatMap(_.questionId)))
        question   <- OptionT(questionService.getQuestion(questionId).map(_.map(_.slug)))
      } yield question).value.map(_.getOrElse(s"core.$country"))

      futureQuestionSlug.map { questionSlug =>
        val maybePublish: OptionT[Future, Unit] = for {
          proposal <- OptionT(proposalCoordinatorService.getProposal(event.id))
          user     <- OptionT(userService.getUser(proposal.author))
        } yield {
          val country: Country = proposal.country.getOrElse(user.country)
          val language: Language = proposal.language.getOrElse(user.language)
          val templateConfiguration =
            mailJetTemplateConfiguration.proposalRefused(questionSlug, country, language, user.isOrganisation)
          if (user.emailVerified && templateConfiguration.enabled) {
            eventBusService.publish(
              SendEmail.create(
                templateId = Some(templateConfiguration.templateId),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(
                    name = Some(mailJetTemplateConfiguration.fromName),
                    email = mailJetTemplateConfiguration.from
                  )
                ),
                variables = Some(
                  Map(
                    "proposal_text" -> proposal.content,
                    "firstname" -> user.fullName.getOrElse(""),
                    "refusal_reason" -> proposal.refusalReason.getOrElse(""),
                    "registration_context" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "operation" -> event.operation.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse(""),
                    "organisation_name" -> user.organisationName.getOrElse("")
                  )
                ),
                customCampaign = templateConfiguration.customCampaign,
                monitoringCategory = templateConfiguration.monitoringCategory
              )
            )
          }
        }
        maybePublish.getOrElseF(
          Future.failed(
            new IllegalStateException(s"proposal or user not found or user not verified for proposal ${event.id.value}")
          )
        )
      }
    } else {
      Future.successful[Unit] {}
    }

  }

  def handleProposalPostponed(event: ProposalPostponed): Future[Unit] = {
    Future.successful[Unit] {
      log.debug(s"received $event")
    }
  }
}

object ProposalEmailConsumerActor {
  val name: String = "proposal-events-emails-consumer"
  def props(userService: UserService,
            proposalCoordinatorService: ProposalCoordinatorService,
            questionService: QuestionService): Props =
    Props(new ProposalEmailConsumer(userService, proposalCoordinatorService, questionService))
}
