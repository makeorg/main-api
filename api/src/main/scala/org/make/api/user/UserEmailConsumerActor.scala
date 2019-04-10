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

package org.make.api.user

import java.time.ZonedDateTime

import akka.actor.Props
import akka.util.Timeout
import com.sksamuel.avro4s.RecordFormat
import org.make.api.crmTemplates.CrmTemplatesService
import org.make.api.extensions.{MailJetTemplateConfigurationExtension, MakeSettingsExtension}
import org.make.api.operation.{OperationOfQuestionService, SearchOperationsOfQuestions}
import org.make.api.question.{QuestionService, SearchQuestionRequest}
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.api.technical.crm.{Recipient, SendEmail}
import org.make.api.technical.{ActorEventBusServiceComponent, AvroSerializers, KafkaConsumerActor, TimeSettings}
import org.make.api.userhistory.UserEvent._
import org.make.core.ApplicationName.{MainFrontend, Widget}
import org.make.core.RequestContext
import org.make.core.crmTemplate.CrmTemplates
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserEmailConsumerActor(userService: UserService,
                             questionService: QuestionService,
                             operationOfQuestionService: OperationOfQuestionService,
                             crmTemplatesService: CrmTemplatesService)
    extends KafkaConsumerActor[UserEventWrapper]
    with MakeSettingsExtension
    with MailJetTemplateConfigurationExtension
    with ActorEventBusServiceComponent
    with AvroSerializers {

  override protected lazy val kafkaTopic: String = UserProducerActor.topicKey
  override protected val format: RecordFormat[UserEventWrapper] = RecordFormat[UserEventWrapper]
  override val groupId = "user-email"

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override def handleMessage(message: UserEventWrapper): Future[Unit] = {
    message.event.fold(HandledMessages) match {
      case event: ResetPasswordEvent              => handleResetPasswordEvent(event)
      case event: UserRegisteredEvent             => handleUserRegisteredEventEvent(event)
      case event: UserValidatedAccountEvent       => handleUserValidatedAccountEvent(event)
      case event: UserConnectedEvent              => doNothing(event)
      case event: UserUpdatedTagEvent             => doNothing(event)
      case event: ResendValidationEmailEvent      => doNothing(event)
      case event: OrganisationRegisteredEvent     => doNothing(event)
      case event: OrganisationUpdatedEvent        => doNothing(event)
      case event: OrganisationInitializationEvent => handleOrganisationAskPassword(event)
      case event: UserUpdatedOptInNewsletterEvent => doNothing(event)
      case event: UserAnonymizedEvent             => doNothing(event)
      case event: UserFollowEvent                 => doNothing(event)
      case event: UserUnfollowEvent               => doNothing(event)
    }
  }

  private def getAccountValidationUrl(user: User, verificationToken: String, requestContext: RequestContext): String = {
    val operationIdValue: String = requestContext.operationId.map(_.value).getOrElse("core")
    val language: String = requestContext.language.map(_.value).getOrElse("fr")
    val country: String = requestContext.country.map(_.value).getOrElse("FR")
    val questionIdValue: String = requestContext.questionId.map(_.value).getOrElse("")

    val utmParams = "utm_source=crm&utm_medium=email&utm_campaign=core&utm_term=validation&utm_content=cta"
    val appParams = s"operation=$operationIdValue&language=$language&country=$country&question=$questionIdValue"

    if (requestContext.applicationName.contains(MainFrontend) || requestContext.applicationName.contains(Widget)) {
      val appPath =
        s"${user.country.value}-${user.language.value}/account-activation/${user.userId.value}/$verificationToken"
      s"${mailJetTemplateConfiguration.getMainFrontendUrl()}/$appPath?$appParams&$utmParams"
    } else {
      val appPath = s"${user.country.value}/account-activation/${user.userId.value}/$verificationToken"
      s"${mailJetTemplateConfiguration.getLegacyFrontendUrl()}?$utmParams#/$appPath?$appParams"
    }
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
      }
      .map(_.map(_.map(_.slug)))
      .getOrElse {
        if (BusinessConfig.coreIsAvailableForCountry(country)) {
          Future.successful(None)
        } else {
          operationOfQuestionService
            .search(
              0,
              None,
              None,
              None,
              SearchOperationsOfQuestions(questionIds = None, operationId = None, openAt = Some(ZonedDateTime.now()))
            )
            .flatMap { opOfQuestion =>
              questionService
                .searchQuestion(SearchQuestionRequest(country = Some(country), language = Some(language)))
                .map(_.filter(question => opOfQuestion.map(_.questionId).contains(question.questionId)))
            }
            .map(_.headOption.map(_.slug))
        }
      }
      .map(_.getOrElse("unknown"))
  }

  def handleUserValidatedAccountEvent(event: UserValidatedAccountEvent): Future[Unit] = {
    val country = event.country
    val language = event.language
    getUserWithValidEmail(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val requestContext = event.requestContext
        val futureQuestionSlug: Future[String] = resolveQuestionSlug(country, language, requestContext)
        futureQuestionSlug.map { questionSlug =>
          val locale = s"${language.value}_${country.value}"
          val questionId = event.requestContext.questionId
          crmTemplatesService.find(0, None, questionId, Some(locale)).map {
            case Seq(crmTemplates) =>
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
                      "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                      "question" -> event.requestContext.question.getOrElse(""),
                      "location" -> event.requestContext.location.getOrElse(""),
                      "source" -> event.requestContext.source.getOrElse("")
                    )
                  ),
                  customCampaign = None,
                  monitoringCategory = Some(CrmTemplates.MonitoringCategory.welcome)
                )
              )
            case seq if seq.length > 1 =>
              log.warning(s"Concurrent templates for question: $questionId and locale $locale. Mail not sent.")
            case _ => log.warning(s"No templates found for question: $questionId and locale $locale. Mail not sent.")
          }
        }
      }
    }
  }

  def handleUserRegisteredEventEvent(event: UserRegisteredEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.language
        val country = event.country

        val locale = s"${language.value}_${country.value}"
        val questionId = event.requestContext.questionId
        crmTemplatesService.find(0, None, questionId, Some(locale)).map {
          case Seq(crmTemplates) =>
            val verificationToken: String = user.verificationToken match {
              case Some(token) => token
              case _           => throw new IllegalStateException
            }

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
                    "email_validation_url" -> getAccountValidationUrl(user, verificationToken, event.requestContext),
                    "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = None,
                monitoringCategory = Some(CrmTemplates.MonitoringCategory.account)
              )
            )
          case seq if seq.length > 1 =>
            log.warning(s"Concurrent templates for question: $questionId and locale $locale. Mail not sent.")
          case _ => log.warning(s"No templates found for question: $questionId and locale $locale. Mail not sent.")
        }
      }
    }

  }

  private def handleResetPasswordEvent(event: ResetPasswordEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.language
        val country = event.country
        val locale = s"${language.value}_${country.value}"
        val questionId = event.requestContext.questionId
        crmTemplatesService.find(0, None, questionId, Some(locale)).map {
          case Seq(crmTemplates) =>
            val resetToken: String = user.resetToken match {
              case Some(token) => token
              case _           => throw new IllegalStateException("reset token required")
            }

            context.system.eventStream.publish(
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
                    "forgotten_password_url" -> getForgottenPasswordUrl(user, resetToken, event.requestContext),
                    "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = None,
                monitoringCategory = Some(CrmTemplates.MonitoringCategory.account)
              )
            )
          case seq if seq.length > 1 =>
            log.warning(s"Concurrent templates for question: $questionId and locale $locale. Mail not sent.")
          case _ => log.warning(s"No templates found for question: $questionId and locale $locale. Mail not sent.")
        }

      }
    }
  }

  private def handleOrganisationAskPassword(event: OrganisationInitializationEvent): Future[Unit] = {
    getUserWithValidEmail(event.userId).map { maybeUser =>
      maybeUser.foreach { user =>
        val language = event.language
        val country = event.country

        val locale = s"${language.value}_${country.value}"
        val questionId = event.requestContext.questionId
        crmTemplatesService.find(0, None, questionId, Some(locale)).map {
          case Seq(crmTemplates) =>
            val resetToken: String = user.resetToken match {
              case Some(token) => token
              case _           => throw new IllegalStateException("reset token required")
            }

            context.system.eventStream.publish(
              SendEmail.create(
                templateId = Some(crmTemplates.forgottenPasswordOrganisation.value.toInt),
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
                    "forgotten_password_url" -> getForgottenPasswordUrl(user, resetToken, event.requestContext),
                    "operation" -> event.requestContext.operationId.map(_.value).getOrElse(""),
                    "question" -> event.requestContext.question.getOrElse(""),
                    "location" -> event.requestContext.location.getOrElse(""),
                    "source" -> event.requestContext.source.getOrElse("")
                  )
                ),
                customCampaign = None,
                monitoringCategory = Some(CrmTemplates.MonitoringCategory.account)
              )
            )
          case seq if seq.length > 1 =>
            log.warning(s"Concurrent templates for question: $questionId and locale $locale. Mail not sent.")
          case _ => log.warning(s"No templates found for question: $questionId and locale $locale. Mail not sent.")
        }
      }

    }
  }

  private def getUserWithValidEmail(userId: UserId): Future[Option[User]] = {
    userService.getUser(userId).map {
      case Some(user) if user.isHardBounce =>
        log.info(s"an hardbounced user (${user.email}) will be ignored by email consumer")
        None
      case other => other
    }
  }
}

object UserEmailConsumerActor {
  def props(userService: UserService,
            questionService: QuestionService,
            operationOfQuestionService: OperationOfQuestionService,
            crmTemplatesService: CrmTemplatesService): Props =
    Props(new UserEmailConsumerActor(userService, questionService, operationOfQuestionService, crmTemplatesService))
  val name: String = "user-events-consumer"
}
