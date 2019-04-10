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
import org.make.api.crmTemplates.CrmTemplatesService
import org.make.api.extensions.{MailJetTemplateConfigurationExtension, MakeSettingsExtension}
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.question.QuestionService
import org.make.api.technical.crm.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, KafkaConsumerActor, TimeSettings}
import org.make.api.user.UserService
import org.make.core.ApplicationName.{MainFrontend, Widget}
import org.make.core.crmTemplate.{CrmTemplates, TemplateId}
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.question.Question
import org.make.core.user.User

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ProposalEmailConsumer(userService: UserService,
                            proposalCoordinatorService: ProposalCoordinatorService,
                            questionService: QuestionService,
                            crmTemplatesService: CrmTemplatesService)
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
      case event: ProposalVotesVerifiedUpdated => doNothing(event)
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

  private def getProposalUrl(proposal: Proposal, questionSlug: String): String = {
    val utmParams = "utm_source=crm&utm_medium=email&utm_campaign=core&utm_term=publication&utm_content=cta_share"
    val country: String = proposal.country.map(_.value).getOrElse("FR")
    val language: String = proposal.language.map(_.value).getOrElse("fr")

    if (proposal.creationContext.applicationName.contains(MainFrontend) || proposal.creationContext.applicationName
          .contains(Widget)) {
      val appPath =
        s"$country-$language/consultation/$questionSlug/proposal/${proposal.proposalId.value}/${proposal.slug}"
      s"${mailJetTemplateConfiguration.getMainFrontendUrl()}/$appPath?$utmParams"
    } else {
      val appPath = s"/$country/proposal/${proposal.proposalId.value}/${proposal.slug}"
      s"${mailJetTemplateConfiguration.getLegacyFrontendUrl()}?$utmParams#$appPath"
    }
  }

  private def sendMail(proposalId: ProposalId,
                       templateId: TemplateId,
                       variables: (User, Proposal) => Map[String, String]): Future[Unit] = {
    val maybePublish: OptionT[Future, Unit] = for {
      proposal <- OptionT(proposalCoordinatorService.getProposal(proposalId))
      user     <- OptionT(userService.getUser(proposal.author))
    } yield {
      if (user.emailVerified) {
        eventBusService.publish(
          SendEmail.create(
            templateId = Some(templateId.value.toInt),
            recipients = Seq(Recipient(email = user.email, name = user.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(variables(user, proposal)),
            customCampaign = None,
            monitoringCategory = Some(CrmTemplates.MonitoringCategory.moderation)
          )
        )
      }
    }
    maybePublish.getOrElseF(
      Future.failed(
        new IllegalStateException(s"proposal or user not found or user not verified for proposal ${proposalId.value}")
      )
    )

  }

  def handleProposalAccepted(event: ProposalAccepted): Future[Unit] = {
    if (event.sendValidationEmail) {
      val locale: Option[String] = (event.requestContext.language, event.requestContext.country) match {
        case (Some(language), Some(country)) => Some(s"${language}_$country")
        case _                               => None
      }

      val futureMaybeQuestion: Future[Option[Question]] = event.question match {
        case Some(questionId) => questionService.getQuestion(questionId)
        case None             => Future.successful(None)
      }

      futureMaybeQuestion.map { maybeQuestion =>
        val maybeQuestionId = maybeQuestion.map(_.questionId)
        val slug = maybeQuestion.map(_.slug).orElse(locale).getOrElse("")
        def variables(user: User, proposal: Proposal): Map[String, String] =
          Map(
            "proposal_url" -> getProposalUrl(proposal, slug),
            "proposal_text" -> proposal.content,
            "firstname" -> user.firstName.getOrElse(""),
            "organisation_name" -> user.organisationName.getOrElse(""),
            "operation" -> event.operation.map(_.value).getOrElse(""),
            "question" -> event.requestContext.question.getOrElse(""),
            "location" -> event.requestContext.location.getOrElse(""),
            "source" -> event.requestContext.source.getOrElse("")
          )
        crmTemplatesService.find(0, None, maybeQuestionId, locale).map {
          case Seq(crmTemplates) =>
            sendMail(event.id, crmTemplates.proposalAccepted, variables)
          case seq if seq.length > 1 =>
            log.warning(
              s"Concurrent templates for question: $maybeQuestionId and locale $locale. Using ${seq.head.crmTemplatesId}"
            )
            sendMail(event.id, seq.head.proposalAccepted, variables)
          case _ =>
            log.error(s"No templates found for question: $maybeQuestionId and locale $locale. Mail not sent.")
            ()
        }
      }
    } else {
      Future.successful(Unit)
    }
  }

  def handleProposalRefused(event: ProposalRefused): Future[Unit] = {
    if (event.sendRefuseEmail) {
      val locale: Option[String] = (event.requestContext.language, event.requestContext.country) match {
        case (Some(language), Some(country)) => Some(s"${language}_$country")
        case _                               => None
      }

      val futureMaybeQuestion: Future[Option[Question]] = (for {
        questionId <- OptionT(proposalCoordinatorService.getProposal(event.id).map(_.flatMap(_.questionId)))
        question   <- OptionT(questionService.getQuestion(questionId))
      } yield question).value
      futureMaybeQuestion.map { maybeQuestion =>
        val maybeQuestionId = maybeQuestion.map(_.questionId)
        val slug = maybeQuestion.map(_.slug).orElse(locale).getOrElse("")
        def variables(user: User, proposal: Proposal): Map[String, String] =
          Map(
            "proposal_url" -> getProposalUrl(proposal, slug),
            "refusal_reason" -> proposal.refusalReason.getOrElse(""),
            "firstname" -> user.firstName.getOrElse(""),
            "organisation_name" -> user.organisationName.getOrElse(""),
            "operation" -> event.operation.map(_.value).getOrElse(""),
            "question" -> event.requestContext.question.getOrElse(""),
            "location" -> event.requestContext.location.getOrElse(""),
            "source" -> event.requestContext.source.getOrElse("")
          )
        crmTemplatesService.find(0, None, maybeQuestionId, locale).map {
          case Seq(crmTemplates) =>
            sendMail(event.id, crmTemplates.proposalAccepted, variables)
          case seq if seq.length > 1 =>
            log.warning(
              s"Concurrent templates for question: $maybeQuestionId and locale $locale. Using ${seq.head.crmTemplatesId}"
            )
            sendMail(event.id, seq.head.proposalAccepted, variables)
          case _ =>
            log.error(s"No templates found for question: $maybeQuestionId and locale $locale. Mail not sent.")
            ()
        }
      }
    } else {
      Future.successful(Unit)
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
            questionService: QuestionService,
            crmTemplatesService: CrmTemplatesService): Props =
    Props(new ProposalEmailConsumer(userService, proposalCoordinatorService, questionService, crmTemplatesService))
}
