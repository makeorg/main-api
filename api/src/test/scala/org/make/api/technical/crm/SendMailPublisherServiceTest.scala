/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

import cats.data.NonEmptyList
import org.make.api.{MakeUnitTest, TestUtils}
import org.make.api.crmTemplates.{CrmTemplatesService, CrmTemplatesServiceComponent}
import org.make.api.extensions.MailJetTemplateConfiguration
import org.make.api.operation.{OperationOfQuestionService, OperationOfQuestionServiceComponent}
import org.make.api.proposal.{ProposalCoordinatorService, ProposalCoordinatorServiceComponent}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.technical.EventBusService
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.core.crmTemplate.{CrmTemplateKind, TemplateId}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.make.core.user.{User, UserId, UserType}
import org.mockito.{ArgumentCaptor, Mockito}

import java.time.ZonedDateTime
import scala.concurrent.Future

class SendMailPublisherServiceTest
    extends MakeUnitTest
    with DefaultSendMailPublisherServiceComponent
    with CrmTemplatesServiceComponent
    with OperationOfQuestionServiceComponent
    with ProposalCoordinatorServiceComponent
    with QuestionServiceComponent
    with UserServiceComponent {

  override val crmTemplatesService: CrmTemplatesService = mock[CrmTemplatesService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val mailJetTemplateConfiguration: MailJetTemplateConfiguration = mock[MailJetTemplateConfiguration]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val questionService: QuestionService = mock[QuestionService]
  override val userService: UserService = mock[UserService]

  private val question = TestUtils.question(
    id = QuestionId("questionId"),
    countries = NonEmptyList.of(Country("DE"), Country("LU")),
    language = Language("de")
  )
  private val organisation =
    TestUtils.user(id = UserId("organisationId"), country = Country("BE"), userType = UserType.UserTypeOrganisation)
  private val user = TestUtils.user(id = UserId("userId"), country = Country("FR"))
  private val requestContext =
    RequestContext.empty.copy(country = Some(Country("DE")), questionId = Some(question.questionId))
  private val organisationProposal =
    proposal(
      id = ProposalId("B2BProposalId"),
      author = organisation.userId,
      questionId = question.questionId,
      refusalReason = Some("invalid"),
      requestContext = requestContext.copy(country = Some(Country("LU")))
    )
  private val userProposal =
    proposal(
      id = ProposalId("proposalId"),
      author = user.userId,
      questionId = question.questionId,
      refusalReason = Some("gibberish"),
      requestContext = requestContext
    )

  when(crmTemplatesService.find(any[CrmTemplateKind], any[Option[QuestionId]], any[Country])).thenAnswer(
    (kind: CrmTemplateKind, _: Option[QuestionId], _: Country) =>
      Future.successful(Some(TemplateId(CrmTemplateKind.values.indexOf(kind).toString)))
  )
  when(proposalCoordinatorService.getProposal(organisationProposal.proposalId))
    .thenReturn(Future.successful(Some(organisationProposal)))
  when(proposalCoordinatorService.getProposal(userProposal.proposalId))
    .thenReturn(Future.successful(Some(userProposal)))
  when(questionService.getQuestion(question.questionId)).thenReturn(Future.successful(Some(question)))
  when(userService.changeEmailVerificationTokenIfNeeded(any[UserId])).thenAnswer { id: UserId =>
    Future.successful(
      Some(
        user(
          id,
          verificationToken = Some("verification"),
          verificationTokenExpiresAt = Some(ZonedDateTime.now().plusDays(1))
        )
      )
    )
  }
  when(userService.getUser(organisation.userId)).thenReturn(Future.successful(Some(organisation)))
  when(userService.getUser(user.userId)).thenReturn(Future.successful(Some(user)))

  Feature("publish email events") {

    for {
      (kind, call, user, tests) <- Seq(
        (
          CrmTemplateKind.Welcome,
          sendMailPublisherService.publishWelcome(_, _),
          user,
          Seq(Map("registration_context" -> "unknown"), Map("registration_context" -> "question-slug"))
        ),
        (
          CrmTemplateKind.Registration,
          sendMailPublisherService.publishRegistration(_, _),
          user.copy(verificationToken = Some("verification")),
          Seq(
            Map(
              "email_validation_url" -> "/FR/account-activation/userId/verification?country=FR&utm_content=cta&utm_campaign=core&utm_medium=email&utm_term=validation&utm_source=crm&question="
            ),
            Map(
              "email_validation_url" -> "/FR/account-activation/userId/verification?country=DE&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=validation&utm_source=crm&question=questionId"
            )
          )
        ),
        (
          CrmTemplateKind.ResendRegistration,
          sendMailPublisherService.resendRegistration(_, _),
          user.copy(verificationToken = Some("verification")),
          Seq(
            Map(
              "email_validation_url" -> "/FR/account-activation/userId/verification?country=FR&utm_content=cta&utm_campaign=core&utm_medium=email&utm_term=validation&utm_source=crm&question="
            ),
            Map(
              "email_validation_url" -> "/FR/account-activation/userId/verification?country=DE&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=validation&utm_source=crm&question=questionId"
            )
          )
        ),
        (
          CrmTemplateKind.ForgottenPassword,
          sendMailPublisherService.publishForgottenPassword(_, _),
          user.copy(resetToken = Some("reset")),
          Seq(
            Map("forgotten_password_url" -> "/FR/password-recovery/userId/reset"),
            Map("forgotten_password_url" -> "/FR/password-recovery/userId/reset")
          )
        ),
        (
          CrmTemplateKind.B2BForgottenPassword,
          sendMailPublisherService.publishForgottenPasswordOrganisation(_, _),
          organisation.copy(resetToken = Some("reset")),
          Seq(
            Map("forgotten_password_url" -> "/BE/password-recovery/organisationId/reset"),
            Map("forgotten_password_url" -> "/BE/password-recovery/organisationId/reset")
          )
        ),
        (
          CrmTemplateKind.B2BEmailChanged,
          sendMailPublisherService.publishEmailChanged(_, _, "newemail@example.com"),
          organisation,
          Seq(Map("email" -> "newemail@example.com"), Map("email" -> "newemail@example.com"))
        ),
        (
          CrmTemplateKind.B2BRegistration,
          sendMailPublisherService.publishRegistrationB2B(_, _),
          organisation.copy(resetToken = Some("reset")),
          Seq(
            Map("forgotten_password_url" -> "/BE/password-recovery/organisationId/reset"),
            Map("forgotten_password_url" -> "/BE/password-recovery/organisationId/reset")
          )
        ),
        (
          CrmTemplateKind.ProposalAccepted,
          (_: User, _: RequestContext) => sendMailPublisherService.publishAcceptProposal(ProposalId("proposalId")),
          user,
          Seq(
            Map(
              "proposal_url" -> "/DE/consultation/question-slug/proposal/proposalId/il-faut-tester-l-indexation-des-propositions?utm_source=crm&utm_content=cta_share&utm_campaign=question-slug&utm_medium=email&utm_term=publication",
              "sequence_url" -> "/DE/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=publication"
            ),
            Map(
              "proposal_url" -> "/DE/consultation/question-slug/proposal/proposalId/il-faut-tester-l-indexation-des-propositions?utm_source=crm&utm_content=cta_share&utm_campaign=question-slug&utm_medium=email&utm_term=publication",
              "sequence_url" -> "/DE/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=publication"
            )
          )
        ),
        (
          CrmTemplateKind.ProposalRefused,
          (_: User, _: RequestContext) => sendMailPublisherService.publishRefuseProposal(ProposalId("proposalId")),
          user,
          Seq(
            Map(
              "refusal_reason" -> "gibberish",
              "sequence_url" -> "/DE/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=refus"
            ),
            Map(
              "refusal_reason" -> "gibberish",
              "sequence_url" -> "/DE/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=refus"
            )
          )
        ),
        (
          CrmTemplateKind.B2BProposalAccepted,
          (_: User, _: RequestContext) => sendMailPublisherService.publishAcceptProposal(ProposalId("B2BProposalId")),
          organisation,
          Seq(
            Map(
              "proposal_url" -> "/LU/consultation/question-slug/proposal/B2BProposalId/il-faut-tester-l-indexation-des-propositions?utm_source=crm&utm_content=cta_share&utm_campaign=question-slug&utm_medium=email&utm_term=publicationacteur",
              "sequence_url" -> "/LU/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=publicationacteur"
            ),
            Map(
              "proposal_url" -> "/LU/consultation/question-slug/proposal/B2BProposalId/il-faut-tester-l-indexation-des-propositions?utm_source=crm&utm_content=cta_share&utm_campaign=question-slug&utm_medium=email&utm_term=publicationacteur",
              "sequence_url" -> "/LU/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=publicationacteur"
            )
          )
        ),
        (
          CrmTemplateKind.B2BProposalRefused,
          (_: User, _: RequestContext) => sendMailPublisherService.publishRefuseProposal(ProposalId("B2BProposalId")),
          organisation,
          Seq(
            Map(
              "refusal_reason" -> "invalid",
              "sequence_url" -> "/LU/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=refusacteur"
            ),
            Map(
              "refusal_reason" -> "invalid",
              "sequence_url" -> "/LU/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=refusacteur"
            )
          )
        )
      )
      (variables, (label, context)) <- tests.zip(Seq(("empty", RequestContext.empty), ("question", requestContext)))
    } {
      Scenario(s"${kind.entryName} with $label context") {
        Mockito.clearInvocations(eventBusService)
        whenReady(call(user, context)) { _ =>
          val captor = ArgumentCaptor.forClass[SendEmail, SendEmail](classOf[SendEmail])
          verify(eventBusService).publish(captor.capture())
          val event = captor.getValue
          event.templateId shouldBe Some(CrmTemplateKind.values.indexOf(kind))
          variables.foreach {
            case (key, value) => event.variables.flatMap(_.get(key)) shouldBe Some(value)
          }
        }
      }
    }

  }

}
