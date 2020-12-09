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
import org.make.core.reference.Country
import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.make.core.user.{User, UserId, UserType}
import org.mockito.{ArgumentCaptor, Mockito}

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

  private val country = Country("FR")
  private val question = TestUtils.question(id = QuestionId("question"))
  private val organisation = TestUtils.user(id = UserId("organisation"), userType = UserType.UserTypeOrganisation)
  private val user = TestUtils.user(id = UserId("user"), country = country)
  private val organisationProposal =
    proposal(id = ProposalId("B2BProposal"), author = organisation.userId, refusalReason = Some("invalid"))
  private val userProposal =
    proposal(id = ProposalId("proposal"), author = user.userId, refusalReason = Some("gibberish"))
  private val requestContext = RequestContext.empty.copy(questionId = Some(question.questionId))

  when(crmTemplatesService.find(any[CrmTemplateKind], any[Option[QuestionId]], any[Country])).thenAnswer(
    (kind: CrmTemplateKind, _: Option[QuestionId], _: Country) =>
      Future.successful(Some(TemplateId(CrmTemplateKind.values.indexOf(kind).toString)))
  )
  when(proposalCoordinatorService.getProposal(organisationProposal.proposalId))
    .thenReturn(Future.successful(Some(organisationProposal)))
  when(proposalCoordinatorService.getProposal(userProposal.proposalId))
    .thenReturn(Future.successful(Some(userProposal)))
  when(questionService.getQuestion(question.questionId)).thenReturn(Future.successful(Some(question)))
  when(userService.changeEmailVerificationTokenIfNeeded(any[UserId]))
    .thenReturn(Future.successful(Some("verification")))
  when(userService.getUser(UserId("organisation"))).thenReturn(Future.successful(Some(organisation)))
  when(userService.getUser(UserId("user"))).thenReturn(Future.successful(Some(user)))

  Feature("publish email events") {

    Seq(
      (
        CrmTemplateKind.Welcome,
        sendMailPublisherService.publishWelcome(_, _, _),
        user,
        "registration_context" -> "question-slug"
      ),
      (
        CrmTemplateKind.Registration,
        sendMailPublisherService.publishRegistration(_, _, _),
        user.copy(verificationToken = Some("verification")),
        "email_validation_url" -> "/FR/account-activation/user/verification?country=FR&operation=core&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=validation&utm_source=crm&question=question"
      ),
      (
        CrmTemplateKind.ResendRegistration,
        sendMailPublisherService.resendRegistration(_, _, _),
        user.copy(verificationToken = Some("verification")),
        "email_validation_url" -> "/FR/account-activation/user/verification?country=FR&operation=core&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=validation&utm_source=crm&question=question"
      ),
      (
        CrmTemplateKind.ForgottenPassword,
        sendMailPublisherService.publishForgottenPassword(_, _, _),
        user.copy(resetToken = Some("reset")),
        "forgotten_password_url" -> "/FR/password-recovery/user/reset?operation=core&country=FR&question=question"
      ),
      (
        CrmTemplateKind.B2BForgottenPassword,
        sendMailPublisherService.publishForgottenPasswordOrganisation(_, _, _),
        user.copy(resetToken = Some("reset")),
        "forgotten_password_url" -> "/FR/password-recovery/user/reset?operation=core&country=FR&question=question"
      ),
      (
        CrmTemplateKind.B2BEmailChanged,
        sendMailPublisherService.publishEmailChanged(_, _, _, "newemail@example.com"),
        user,
        "email" -> "newemail@example.com"
      ),
      (
        CrmTemplateKind.B2BRegistration,
        sendMailPublisherService.publishRegistrationB2B(_, _, _),
        user.copy(resetToken = Some("reset")),
        "forgotten_password_url" -> "/FR/password-recovery/user/reset?operation=core&country=FR&question=question"
      ),
      (
        CrmTemplateKind.ProposalAccepted,
        (_: User, _: Country, _: RequestContext) =>
          sendMailPublisherService.publishAcceptProposal(ProposalId("proposal")),
        user,
        "sequence_url" -> "/FR/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=publication"
      ),
      (
        CrmTemplateKind.ProposalRefused,
        (_: User, _: Country, _: RequestContext) =>
          sendMailPublisherService.publishRefuseProposal(ProposalId("proposal")),
        user,
        "refusal_reason" -> "gibberish"
      ),
      (
        CrmTemplateKind.B2BProposalAccepted,
        (_: User, _: Country, _: RequestContext) =>
          sendMailPublisherService.publishAcceptProposal(ProposalId("B2BProposal")),
        organisation,
        "sequence_url" -> "/FR/consultation/question-slug/selection?introCard=false&utm_source=crm&utm_content=cta&utm_campaign=question-slug&utm_medium=email&utm_term=publicationacteur"
      ),
      (
        CrmTemplateKind.B2BProposalRefused,
        (_: User, _: Country, _: RequestContext) =>
          sendMailPublisherService.publishRefuseProposal(ProposalId("B2BProposal")),
        organisation,
        "refusal_reason" -> "invalid"
      )
    ).foreach {
      case (kind, call, user, (key, value)) =>
        Scenario(kind.entryName) {
          Mockito.clearInvocations(eventBusService)
          whenReady(call(user, country, requestContext)) { _ =>
            val captor = ArgumentCaptor.forClass[SendEmail, SendEmail](classOf[SendEmail])
            verify(eventBusService).publish(captor.capture())
            val event = captor.getValue
            event.templateId shouldBe Some(CrmTemplateKind.values.indexOf(kind))
            event.variables.flatMap(_.get(key)) shouldBe Some(value)
          }
        }
    }

  }

}
