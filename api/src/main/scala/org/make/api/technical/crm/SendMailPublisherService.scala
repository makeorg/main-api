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

package org.make.api.technical.crm

import cats.data.OptionT
import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.crmTemplates.CrmTemplatesServiceComponent
import org.make.api.extensions.MailJetTemplateConfigurationComponent
import org.make.api.operation.OperationOfQuestionServiceComponent
import org.make.api.proposal.ProposalCoordinatorServiceComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.EventBusServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.RequestContext
import org.make.core.crmTemplate.{CrmTemplates, TemplateId}
import org.make.core.operation.OperationId
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.User

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait SendMailPublisherServiceComponent {
  def sendMailPublisherService: SendMailPublisherService
}

trait SendMailPublisherService {
  def publishWelcome(user: User, country: Country, language: Language, requestContext: RequestContext): Future[Unit]
  def publishRegistration(user: User,
                          country: Country,
                          language: Language,
                          requestContext: RequestContext): Future[Unit]
  def publishForgottenPassword(user: User,
                               country: Country,
                               language: Language,
                               requestContext: RequestContext): Future[Unit]
  def publishForgottenPasswordOrganisation(organisation: User,
                                           country: Country,
                                           language: Language,
                                           requestContext: RequestContext): Future[Unit]
  def publishAcceptProposal(proposalId: ProposalId,
                            maybeQuestionId: Option[QuestionId],
                            maybeOperationId: Option[OperationId],
                            requestContext: RequestContext): Future[Unit]
  def publishRefuseProposal(proposalId: ProposalId,
                            maybeOperationId: Option[OperationId],
                            requestContext: RequestContext): Future[Unit]
}

trait DefaultSendMailPublisherServiceComponent
    extends SendMailPublisherServiceComponent
    with MailJetTemplateConfigurationComponent
    with EventBusServiceComponent
    with StrictLogging {
  this: UserServiceComponent
    with ProposalCoordinatorServiceComponent
    with QuestionServiceComponent
    with CrmTemplatesServiceComponent
    with OperationOfQuestionServiceComponent =>

  private def getProposalUrl(proposal: Proposal, questionSlug: String): String = {
    val utmParams = "utm_source=crm&utm_medium=email&utm_campaign=core&utm_term=publication&utm_content=cta_share"
    val country: String = proposal.country.map(_.value).getOrElse("FR")
    val language: String = proposal.language.map(_.value).getOrElse("fr")

    val appPath =
      s"$country-$language/consultation/$questionSlug/proposal/${proposal.proposalId.value}/${proposal.slug}"
    s"${mailJetTemplateConfiguration.getMainFrontendUrl()}/$appPath?$utmParams"
  }

  private def getAccountValidationUrl(user: User, verificationToken: String, requestContext: RequestContext): String = {
    val operationIdValue: String = requestContext.operationId.map(_.value).getOrElse("core")
    val language: String = requestContext.language.map(_.value).getOrElse("fr")
    val country: String = requestContext.country.map(_.value).getOrElse("FR")
    val questionIdValue: String = requestContext.questionId.map(_.value).getOrElse("")

    val utmParams = "utm_source=crm&utm_medium=email&utm_campaign=core&utm_term=validation&utm_content=cta"
    val appParams = s"operation=$operationIdValue&language=$language&country=$country&question=$questionIdValue"

    val appPath =
      s"${user.country.value}-${user.language.value}/account-activation/${user.userId.value}/$verificationToken"
    s"${mailJetTemplateConfiguration.getMainFrontendUrl()}/$appPath?$appParams&$utmParams"
  }

  private def getForgottenPasswordUrl(user: User, resetToken: String, requestContext: RequestContext): String = {
    val language: String = requestContext.language.map(_.value).getOrElse("fr")
    val country: String = requestContext.country.map(_.value).getOrElse("FR")
    val operationIdValue: String = requestContext.operationId.map(_.value).getOrElse("core")
    val questionIdValue: String = requestContext.questionId.map(_.value).getOrElse("")
    val appParams = s"operation=$operationIdValue&language=$language&country=$country&question=$questionIdValue"
    val appPath = s"$country-$language/password-recovery/${user.userId.value}/$resetToken"

    s"${mailJetTemplateConfiguration.getMainFrontendUrl()}/$appPath?$appParams"
  }

  def resolveQuestionSlug(country: Country, language: Language, requestContext: RequestContext): Future[String] = {
    requestContext.questionId
      .map(questionService.getQuestion)
      .orElse {
        requestContext.operationId.map(
          operationId =>
            questionService
              .findQuestion(
                maybeOperationId = Some(operationId),
                country = country,
                language = language,
                maybeThemeId = None
            )
        )
      } match {
      case Some(futureMaybeQuestion) => futureMaybeQuestion.map(_.map(_.slug).getOrElse("unknown"))
      case None                      => Future.successful("unknown")
    }
  }

  private def sendModerationMail(proposalId: ProposalId,
                                 questionId: Option[QuestionId],
                                 templateId: (CrmTemplates, Boolean) => TemplateId,
                                 variables: (User, Proposal)         => Map[String, String]): Future[Unit] = {
    val maybePublish: OptionT[Future, Unit] = for {
      proposal <- OptionT(proposalCoordinatorService.getProposal(proposalId))
      user     <- OptionT(userService.getUser(proposal.author))
      locale = s"${user.language.value}_${user.country.value}"
      crmTemplates <- OptionT(findCrmTemplates(questionId, locale))
    } yield {
      if (user.emailVerified) {
        eventBusService.publish(
          SendEmail.create(
            templateId = Some(templateId(crmTemplates, user.isOrganisation).value.toInt),
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

  private def findCrmTemplates(questionId: Option[QuestionId], locale: String): Future[Option[CrmTemplates]] = {
    crmTemplatesService.find(start = 0, end = None, questionId = questionId, locale = Some(locale)).map {
      case Seq(crmTemplates) => Some(crmTemplates)
      case seq if seq.length > 1 =>
        logger
          .warn(s"Concurrent templates for question: $questionId and locale $locale. Using ${seq.head.crmTemplatesId}.")
        Some(seq.head)
      case _ =>
        logger.error(s"No templates found for question: $questionId and locale $locale. Mail not sent.")
        None
    }
  }

  override def sendMailPublisherService: SendMailPublisherService = new DefaultSendMailPublisherService

  class DefaultSendMailPublisherService extends SendMailPublisherService {
    override def publishWelcome(user: User,
                                country: Country,
                                language: Language,
                                requestContext: RequestContext): Future[Unit] = {
      val locale = s"${language.value}_${country.value}"
      val questionId = requestContext.questionId
      val futureQuestionSlug: Future[String] = resolveQuestionSlug(country, language, requestContext)
      def publishSendEmail(questionSlug: String, crmTemplates: CrmTemplates): Unit =
        eventBusService.publish(
          SendEmail.create(
            templateId = Some(crmTemplates.welcome.value.toInt),
            recipients = Seq(Recipient(email = user.email, name = user.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(
              Map(
                "firstname" -> user.firstName.getOrElse(""),
                "registration_context" -> questionSlug,
                "operation" -> requestContext.operationId.map(_.value).getOrElse(""),
                "question" -> requestContext.question.getOrElse(""),
                "location" -> requestContext.location.getOrElse(""),
                "source" -> requestContext.source.getOrElse("")
              )
            ),
            customCampaign = None,
            monitoringCategory = Some(CrmTemplates.MonitoringCategory.welcome)
          )
        )

      futureQuestionSlug.flatMap { questionSlug =>
        findCrmTemplates(questionId, locale).map(_.foreach { crmTemplates =>
          publishSendEmail(questionSlug, crmTemplates)
        })
      }
    }

    override def publishRegistration(user: User,
                                     country: Country,
                                     language: Language,
                                     requestContext: RequestContext): Future[Unit] = {
      val locale = s"${language.value}_${country.value}"
      val questionId = requestContext.questionId
      val verificationToken: String = user.verificationToken match {
        case Some(token) => token
        case _           => throw new IllegalStateException("verification token required but not provided")
      }

      def publishSendEmail(crmTemplates: CrmTemplates): Unit =
        eventBusService.publish(
          SendEmail.create(
            templateId = Some(crmTemplates.registration.value.toInt),
            recipients = Seq(Recipient(email = user.email, name = user.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(
              Map(
                "firstname" -> user.firstName.getOrElse(""),
                "email_validation_url" -> getAccountValidationUrl(user, verificationToken, requestContext),
                "operation" -> requestContext.operationId.map(_.value).getOrElse(""),
                "question" -> requestContext.question.getOrElse(""),
                "location" -> requestContext.location.getOrElse(""),
                "source" -> requestContext.source.getOrElse("")
              )
            ),
            customCampaign = None,
            monitoringCategory = Some(CrmTemplates.MonitoringCategory.account)
          )
        )

      findCrmTemplates(questionId, locale).map(_.foreach { crmTemplates =>
        publishSendEmail(crmTemplates)
      })
    }

    override def publishForgottenPassword(user: User,
                                          country: Country,
                                          language: Language,
                                          requestContext: RequestContext): Future[Unit] = {
      val locale = s"${language.value}_${country.value}"
      val questionId = requestContext.questionId
      val resetToken: String = user.resetToken match {
        case Some(token) => token
        case _           => throw new IllegalStateException("reset token required but not provided")
      }
      def publishSendEmail(crmTemplates: CrmTemplates): Unit =
        eventBusService.publish(
          SendEmail.create(
            templateId = Some(crmTemplates.forgottenPassword.value.toInt),
            recipients = Seq(Recipient(email = user.email, name = user.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(
              Map(
                "firstname" -> user.firstName.getOrElse(""),
                "forgotten_password_url" -> getForgottenPasswordUrl(user, resetToken, requestContext),
                "operation" -> requestContext.operationId.map(_.value).getOrElse(""),
                "question" -> requestContext.question.getOrElse(""),
                "location" -> requestContext.location.getOrElse(""),
                "source" -> requestContext.source.getOrElse("")
              )
            ),
            customCampaign = None,
            monitoringCategory = Some(CrmTemplates.MonitoringCategory.account)
          )
        )

      findCrmTemplates(questionId, locale).map(_.foreach { crmTemplates =>
        publishSendEmail(crmTemplates)
      })
    }

    override def publishForgottenPasswordOrganisation(organisation: User,
                                                      country: Country,
                                                      language: Language,
                                                      requestContext: RequestContext): Future[Unit] = {
      val locale = s"${language.value}_${country.value}"
      val questionId = requestContext.questionId
      val resetToken: String = organisation.resetToken match {
        case Some(token) => token
        case _           => throw new IllegalStateException("reset token required")
      }
      def publishSendEmail(crmTemplates: CrmTemplates): Unit =
        eventBusService.publish(
          SendEmail.create(
            templateId = Some(crmTemplates.forgottenPasswordOrganisation.value.toInt),
            recipients = Seq(Recipient(email = organisation.email, name = organisation.fullName)),
            from = Some(
              Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
            ),
            variables = Some(
              Map(
                "firstname" -> organisation.firstName.getOrElse(""),
                "forgotten_password_url" -> getForgottenPasswordUrl(organisation, resetToken, requestContext),
                "operation" -> requestContext.operationId.map(_.value).getOrElse(""),
                "question" -> requestContext.question.getOrElse(""),
                "location" -> requestContext.location.getOrElse(""),
                "source" -> requestContext.source.getOrElse("")
              )
            ),
            customCampaign = None,
            monitoringCategory = Some(CrmTemplates.MonitoringCategory.account)
          )
        )

      findCrmTemplates(questionId, locale).map(_.foreach { crmTemplates =>
        publishSendEmail(crmTemplates)
      })
    }

    override def publishAcceptProposal(proposalId: ProposalId,
                                       maybeQuestionId: Option[QuestionId],
                                       maybeOperationId: Option[OperationId],
                                       requestContext: RequestContext): Future[Unit] = {
      val locale: Option[String] = (requestContext.language, requestContext.country) match {
        case (Some(language), Some(country)) => Some(s"${language}_$country")
        case _                               => None
      }

      val futureMaybeQuestion: Future[Option[Question]] = maybeQuestionId match {
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
            "operation" -> maybeOperationId.map(_.value).getOrElse(""),
            "question" -> maybeQuestionId.map(_.value).getOrElse(""),
            "location" -> requestContext.location.getOrElse(""),
            "source" -> requestContext.source.getOrElse("")
          )
        def template(crmTemplates: CrmTemplates, isOrga: Boolean): TemplateId =
          if (isOrga)
            crmTemplates.proposalAcceptedOrganisation
          else
            crmTemplates.proposalAccepted
        sendModerationMail(proposalId, maybeQuestionId, template, variables)
      }
    }

    override def publishRefuseProposal(proposalId: ProposalId,
                                       maybeOperationId: Option[OperationId],
                                       requestContext: RequestContext): Future[Unit] = {
      val locale: Option[String] = (requestContext.language, requestContext.country) match {
        case (Some(language), Some(country)) => Some(s"${language}_$country")
        case _                               => None
      }

      val futureMaybeQuestion: Future[Option[Question]] = (for {
        questionId <- OptionT(proposalCoordinatorService.getProposal(proposalId).map(_.flatMap(_.questionId)))
        question   <- OptionT(questionService.getQuestion(questionId))
      } yield question).value
      futureMaybeQuestion.map { maybeQuestion =>
        val maybeQuestionId = maybeQuestion.map(_.questionId)
        val slug = maybeQuestion.map(_.slug).orElse(locale).getOrElse("")
        def variables(user: User, proposal: Proposal): Map[String, String] =
          Map(
            "proposal_url" -> getProposalUrl(proposal, slug),
            "proposal_text" -> proposal.content,
            "refusal_reason" -> proposal.refusalReason.getOrElse(""),
            "firstname" -> user.firstName.getOrElse(""),
            "organisation_name" -> user.organisationName.getOrElse(""),
            "operation" -> maybeOperationId.map(_.value).getOrElse(""),
            "question" -> maybeQuestionId.map(_.value).getOrElse(""),
            "location" -> requestContext.location.getOrElse(""),
            "source" -> requestContext.source.getOrElse("")
          )
        def template(crmTemplates: CrmTemplates, isOrga: Boolean): TemplateId =
          if (isOrga)
            crmTemplates.proposalRefusedOrganisation
          else
            crmTemplates.proposalRefused
        sendModerationMail(proposalId, maybeQuestionId, template, variables)
      }

    }
  }
}
