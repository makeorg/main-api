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
import io.netty.handler.codec.http.QueryStringEncoder
import org.make.api.crmTemplates.CrmTemplatesServiceComponent
import org.make.api.extensions.MailJetTemplateConfigurationComponent
import org.make.api.operation.OperationOfQuestionServiceComponent
import org.make.api.proposal.ProposalCoordinatorServiceComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.EventBusServiceComponent
import org.make.api.technical.crm.DefaultSendMailPublisherServiceComponent.Utm
import org.make.api.user.UserServiceComponent
import org.make.core.{ApplicationName, RequestContext}
import org.make.core.BusinessConfig._
import org.make.core.crmTemplate.{CrmTemplates, TemplateId}
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.Country
import org.make.core.user.{User, UserType}
import org.make.api.technical.RichOptionT._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SendMailPublisherServiceComponent {
  def sendMailPublisherService: SendMailPublisherService
}

trait SendMailPublisherService {
  def publishWelcome(user: User, country: Country, requestContext: RequestContext): Future[Unit]
  def publishRegistration(user: User, country: Country, requestContext: RequestContext): Future[Unit]
  def publishRegistrationB2B(user: User, country: Country, requestContext: RequestContext): Future[Unit]
  def publishForgottenPassword(user: User, country: Country, requestContext: RequestContext): Future[Unit]
  def publishForgottenPasswordOrganisation(
    organisation: User,
    country: Country,
    requestContext: RequestContext
  ): Future[Unit]
  def publishEmailChanged(user: User, country: Country, requestContext: RequestContext, newEmail: String): Future[Unit]
  def publishAcceptProposal(proposalId: ProposalId): Future[Unit]
  def publishRefuseProposal(proposalId: ProposalId): Future[Unit]
  def resendRegistration(user: User, country: Country, requestContext: RequestContext): Future[Unit]
}

trait DefaultSendMailPublisherServiceComponent
    extends SendMailPublisherServiceComponent
    with MailJetTemplateConfigurationComponent
    with EventBusServiceComponent {
  this: UserServiceComponent
    with ProposalCoordinatorServiceComponent
    with QuestionServiceComponent
    with CrmTemplatesServiceComponent
    with OperationOfQuestionServiceComponent =>

  private def buildUrl(base: String, path: String, maybeUtm: Option[Utm], others: (String, String)*): String = {
    val builder = new QueryStringEncoder(path)
    (maybeUtm
      .fold(Map.empty[String, String])(
        utm =>
          Map(
            "utm_source" -> utm.source,
            "utm_medium" -> utm.medium,
            "utm_campaign" -> utm.campaign,
            "utm_term" -> utm.term,
            "utm_content" -> utm.content
          )
      ) ++ others.toMap).foreachEntry(builder.addParam)
    s"$base/${builder.toString}"
  }

  private def getProposalUrl(proposal: Proposal, questionSlug: String): String = {
    val country: String = proposal.creationContext.country.map(_.value).getOrElse("FR")

    buildUrl(
      base = mailJetTemplateConfiguration.mainFrontendUrl,
      path = s"$country/consultation/$questionSlug/proposal/${proposal.proposalId.value}/${proposal.slug}",
      maybeUtm = Some(Utm(campaign = questionSlug, term = "publication", content = "cta_share"))
    )
  }

  private def getAccountValidationUrl(
    user: User,
    verificationToken: String,
    requestContext: RequestContext,
    utmCampaign: String
  ): String = {
    val operationIdValue: String = requestContext.operationId.map(_.value).getOrElse("core")
    val country: String = requestContext.country.map(_.value).getOrElse("FR")
    val questionIdValue: String = requestContext.questionId.map(_.value).getOrElse("")

    buildUrl(
      base = mailJetTemplateConfiguration.mainFrontendUrl,
      path = s"${user.country.value}/account-activation/${user.userId.value}/$verificationToken",
      maybeUtm = Some(Utm(campaign = utmCampaign, term = "validation", content = "cta")),
      "operation" -> operationIdValue,
      "country" -> country,
      "question" -> questionIdValue
    )
  }

  private def getForgottenPasswordUrl(user: User, resetToken: String, requestContext: RequestContext): String = {
    val country: String = requestContext.country.map(_.value).getOrElse("FR")
    val operationIdValue: String = requestContext.operationId.map(_.value).getOrElse("core")
    val questionIdValue: String = requestContext.questionId.map(_.value).getOrElse("")

    val appPath = s"password-recovery/${user.userId.value}/$resetToken"

    val (base, path) = requestContext.applicationName match {
      case Some(ApplicationName.Backoffice) =>
        (mailJetTemplateConfiguration.backofficeUrl, s"#/$appPath")
      case _ =>
        (mailJetTemplateConfiguration.mainFrontendUrl, s"$country/$appPath")
    }

    buildUrl(
      base = base,
      path = path,
      maybeUtm = None,
      "operation" -> operationIdValue,
      "country" -> country,
      "question" -> questionIdValue
    )
  }

  private def sequenceUrlForProposal(
    isAccepted: Boolean,
    userType: UserType,
    questionSlug: String,
    proposal: Proposal
  ): String = {
    val country: String = proposal.creationContext.country.map(_.value).getOrElse("FR")
    val term: String = if (isAccepted) "publication" else "refus"
    val utmTerm: String = if (userType != UserType.UserTypeUser) s"${term}acteur" else term

    buildUrl(
      base = mailJetTemplateConfiguration.mainFrontendUrl,
      path = s"$country/consultation/$questionSlug/selection",
      maybeUtm = Some(Utm(content = "cta", campaign = questionSlug, term = utmTerm)),
      "introCard" -> "false"
    )
  }

  private def getLocale(country: Country): String = {
    s"${country.language.value}_${country.value}"
  }

  private def getUtmCampaignFromQuestionId(questionId: Option[QuestionId]): Future[String] = {
    questionId match {
      case Some(QuestionId("")) => Future.successful("unknown")
      case Some(id)             => questionService.getQuestion(id).map(_.map(_.slug).getOrElse("unknown"))
      case None                 => Future.successful("core")
    }
  }

  def resolveQuestionSlug(country: Country, requestContext: RequestContext): Future[String] = {
    requestContext.questionId
      .map(questionService.getQuestion)
      .orElse {
        requestContext.operationId.map(
          operationId =>
            questionService
              .findQuestion(maybeOperationId = Some(operationId), country = country, language = country.language)
        )
      } match {
      case Some(futureMaybeQuestion) => futureMaybeQuestion.map(_.map(_.slug).getOrElse("unknown"))
      case None                      => Future.successful("unknown")
    }
  }

  override def sendMailPublisherService: SendMailPublisherService = new DefaultSendMailPublisherService

  class DefaultSendMailPublisherService extends SendMailPublisherService {
    override def publishWelcome(user: User, country: Country, requestContext: RequestContext): Future[Unit] = {
      val locale = getLocale(country)
      val questionId = requestContext.questionId

      resolveQuestionSlug(country, requestContext).flatMap { questionSlug =>
        crmTemplatesService
          .findOne(questionId, locale)
          .map(_.foreach { crmTemplates =>
            eventBusService.publish(
              SendEmail.create(
                templateId = Some(crmTemplates.welcome.value.toInt),
                recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                from = Some(
                  Recipient(
                    name = Some(mailJetTemplateConfiguration.fromName),
                    email = mailJetTemplateConfiguration.from
                  )
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
          })
      }
    }

    override def publishRegistration(user: User, country: Country, requestContext: RequestContext): Future[Unit] = {
      val locale = getLocale(country)
      val questionId = requestContext.questionId

      user.verificationToken match {
        case Some(verificationToken) =>
          crmTemplatesService.findOne(questionId, locale).flatMap {
            case Some(crmTemplates) =>
              getUtmCampaignFromQuestionId(questionId).map { utmCampaign =>
                eventBusService.publish(
                  SendEmail.create(
                    templateId = Some(crmTemplates.registration.value.toInt),
                    recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                    from = Some(
                      Recipient(
                        name = Some(mailJetTemplateConfiguration.fromName),
                        email = mailJetTemplateConfiguration.from
                      )
                    ),
                    variables = Some(
                      Map(
                        "firstname" -> user.firstName.getOrElse(""),
                        "email_validation_url" -> getAccountValidationUrl(
                          user,
                          verificationToken,
                          requestContext,
                          utmCampaign
                        ),
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
              }
            case None => Future.successful {}
          }
        case _ =>
          Future.failed(
            new IllegalStateException(s"verification token required but not provided for user ${user.userId.value}")
          )
      }
    }

    def publishResendRegistration(user: User, country: Country, requestContext: RequestContext): Future[Unit] = {
      val locale = getLocale(country)
      val questionId = requestContext.questionId

      user.verificationToken match {
        case Some(verificationToken) =>
          crmTemplatesService.findOne(questionId, locale).flatMap {
            case Some(crmTemplates) =>
              getUtmCampaignFromQuestionId(questionId).map { utmCampaign =>
                eventBusService.publish(
                  SendEmail.create(
                    templateId = Some(crmTemplates.resendRegistration.value.toInt),
                    recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                    from = Some(
                      Recipient(
                        name = Some(mailJetTemplateConfiguration.fromName),
                        email = mailJetTemplateConfiguration.from
                      )
                    ),
                    variables = Some(
                      Map(
                        "firstname" -> user.firstName.getOrElse(""),
                        "email_validation_url" -> getAccountValidationUrl(
                          user,
                          verificationToken,
                          requestContext,
                          utmCampaign
                        )
                      )
                    ),
                    customCampaign = None,
                    monitoringCategory = Some(CrmTemplates.MonitoringCategory.account)
                  )
                )
              }
            case None => Future.successful {}
          }
        case _ =>
          Future.failed(
            new IllegalStateException(s"verification token required but not provided for user ${user.userId.value}")
          )
      }
    }

    override def publishForgottenPassword(
      user: User,
      country: Country,
      requestContext: RequestContext
    ): Future[Unit] = {
      val locale = getLocale(country)
      val questionId = requestContext.questionId

      user.resetToken match {
        case Some(resetToken) =>
          crmTemplatesService
            .findOne(questionId, locale)
            .map(_.foreach { crmTemplates =>
              eventBusService.publish(
                SendEmail.create(
                  templateId = Some(crmTemplates.forgottenPassword.value.toInt),
                  recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                  from = Some(
                    Recipient(
                      name = Some(mailJetTemplateConfiguration.fromName),
                      email = mailJetTemplateConfiguration.from
                    )
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
            })
        case _ =>
          Future.failed(
            new IllegalStateException(s"reset token required but not provided for user ${user.userId.value}")
          )
      }
    }

    override def publishForgottenPasswordOrganisation(
      organisation: User,
      country: Country,
      requestContext: RequestContext
    ): Future[Unit] = {
      val locale = getLocale(country)
      val questionId = requestContext.questionId

      organisation.resetToken match {
        case Some(resetToken) =>
          crmTemplatesService
            .findOne(questionId, locale)
            .map(_.foreach { crmTemplates =>
              eventBusService.publish(
                SendEmail.create(
                  templateId = Some(crmTemplates.forgottenPasswordOrganisation.value.toInt),
                  recipients = Seq(Recipient(email = organisation.email, name = organisation.fullName)),
                  from = Some(
                    Recipient(
                      name = Some(mailJetTemplateConfiguration.fromName),
                      email = mailJetTemplateConfiguration.from
                    )
                  ),
                  variables = Some(
                    Map(
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
            })
        case _ =>
          Future.failed(
            new IllegalStateException(
              s"reset token required but not provided for organisation ${organisation.userId.value}"
            )
          )
      }
    }

    override def publishEmailChanged(
      user: User,
      country: Country,
      requestContext: RequestContext,
      newEmail: String
    ): Future[Unit] = {
      val locale = getLocale(country)
      val questionId = requestContext.questionId

      crmTemplatesService
        .findOne(questionId, locale)
        .map(_.foreach { crmTemplates =>
          eventBusService.publish(
            SendEmail.create(
              templateId = Some(crmTemplates.organisationEmailChangeConfirmation.value.toInt),
              recipients = Seq(Recipient(email = user.email, name = user.displayName)),
              from = Some(
                Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
              ),
              variables = Some(Map("email" -> newEmail)),
              customCampaign = None,
              monitoringCategory = Some(CrmTemplates.MonitoringCategory.account)
            )
          )
        })
    }

    private def publishModerationEmail(
      proposalId: ProposalId,
      variables: (Question, User, Proposal) => Map[String, String],
      templateId: (CrmTemplates, UserType)  => TemplateId
    ): Future[Unit] = {

      val publishSendEmail = for {
        proposal <- OptionT(proposalCoordinatorService.getProposal(proposalId))
          .orFail(s"Proposal ${proposalId.value} not found")
        user <- OptionT(userService.getUser(proposal.author))
          .orFail(s"user ${proposal.author.value}, author of proposal ${proposalId.value} not found")
        questionId <- OptionT(Future.successful(proposal.questionId))
          .orFail(s"proposal ${proposal.proposalId} doesn't have a question!")
        question <- OptionT(questionService.getQuestion(questionId))
          .orFail(
            s"question ${proposal.questionId.fold("''")(_.value)} not found, it is on proposal ${proposal.proposalId}"
          )
        crmTemplates <- OptionT(crmTemplatesService.findOne(Some(questionId), getLocale(user.country))).orFail {
          val locale = getLocale(user.country)
          s"no crm templates for question ${questionId.value} and locale $locale"
        }
      } yield {
        if (user.emailVerified) {
          eventBusService.publish(
            SendEmail.create(
              templateId = Some(templateId(crmTemplates, user.userType).value.toInt),
              recipients = Seq(Recipient(email = user.email, name = user.fullName)),
              from = Some(
                Recipient(name = Some(mailJetTemplateConfiguration.fromName), email = mailJetTemplateConfiguration.from)
              ),
              variables = Some(variables(question, user, proposal)),
              customCampaign = None,
              monitoringCategory = Some(CrmTemplates.MonitoringCategory.moderation)
            )
          )
        }
      }

      publishSendEmail.getOrElseF(
        Future.failed(
          new IllegalStateException(
            s"Something went wrong unexpectedly while trying to send moderation email for proposal ${proposalId.value}"
          )
        )
      )
    }

    override def publishAcceptProposal(proposalId: ProposalId): Future[Unit] = {

      def variables(question: Question, user: User, proposal: Proposal): Map[String, String] = {
        Map(
          "proposal_url" -> getProposalUrl(proposal, question.slug),
          "proposal_text" -> proposal.content,
          "firstname" -> user.firstName.getOrElse(""),
          "organisation_name" -> user.organisationName.getOrElse(""),
          "operation" -> question.operationId.map(_.value).getOrElse(""),
          "question" -> question.questionId.value,
          "location" -> proposal.creationContext.location.getOrElse(""),
          "source" -> proposal.creationContext.source.getOrElse(""),
          "sequence_url" -> sequenceUrlForProposal(isAccepted = true, user.userType, question.slug, proposal)
        )
      }

      def template(crmTemplates: CrmTemplates, userType: UserType): TemplateId = {
        if (userType == UserType.UserTypeUser) {
          crmTemplates.proposalAccepted
        } else {
          crmTemplates.proposalAcceptedOrganisation
        }
      }

      publishModerationEmail(proposalId, variables, template)

    }

    override def publishRefuseProposal(proposalId: ProposalId): Future[Unit] = {

      def variables(question: Question, user: User, proposal: Proposal): Map[String, String] = {
        Map(
          "proposal_url" -> getProposalUrl(proposal, question.slug),
          "proposal_text" -> proposal.content,
          "refusal_reason" -> proposal.refusalReason.getOrElse(""),
          "firstname" -> user.firstName.getOrElse(""),
          "organisation_name" -> user.organisationName.getOrElse(""),
          "operation" -> question.operationId.map(_.value).getOrElse(""),
          "question" -> question.questionId.value,
          "location" -> proposal.creationContext.location.getOrElse(""),
          "source" -> proposal.creationContext.source.getOrElse(""),
          "sequence_url" -> sequenceUrlForProposal(isAccepted = false, user.userType, question.slug, proposal)
        )
      }

      def template(crmTemplates: CrmTemplates, userType: UserType): TemplateId = {
        if (userType == UserType.UserTypeUser) {
          crmTemplates.proposalRefused
        } else {
          crmTemplates.proposalRefusedOrganisation
        }
      }

      publishModerationEmail(proposalId, variables, template)
    }

    override def resendRegistration(user: User, country: Country, requestContext: RequestContext): Future[Unit] = {

      userService.changeEmailVerificationTokenIfNeeded(user.userId).flatMap {
        case Some(_) => publishResendRegistration(user, country, requestContext)
        case None    => Future.successful {}
      }
    }

    override def publishRegistrationB2B(user: User, country: Country, requestContext: RequestContext): Future[Unit] = {
      val locale = getLocale(country)

      user.resetToken match {
        case Some(resetToken) =>
          crmTemplatesService
            .findOne(None, locale)
            .map(_.foreach { crmTemplates =>
              eventBusService.publish(
                SendEmail.create(
                  templateId = Some(crmTemplates.registrationB2B.value.toInt),
                  recipients = Seq(Recipient(email = user.email, name = user.fullName)),
                  from = Some(
                    Recipient(
                      name = Some(mailJetTemplateConfiguration.fromName),
                      email = mailJetTemplateConfiguration.from
                    )
                  ),
                  variables = Some(
                    Map(
                      "mailto" -> user.email,
                      "forgotten_password_url" -> getForgottenPasswordUrl(user, resetToken, requestContext)
                    )
                  ),
                  customCampaign = None,
                  monitoringCategory = Some(CrmTemplates.MonitoringCategory.account)
                )
              )
            })
        case _ =>
          Future.failed(
            new IllegalStateException(s"reset token required but not provided for user ${user.userId.value}")
          )
      }
    }
  }
}

object DefaultSendMailPublisherServiceComponent {
  final case class Utm(
    source: String = "crm",
    medium: String = "email",
    campaign: String,
    term: String,
    content: String
  )
}
