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

package org.make.api.question

import java.time.ZonedDateTime

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.{PersistentOperationOfQuestionService, _}
import org.make.api.partner.{PartnerService, PartnerServiceComponent}
import org.make.api.sequence.{SequenceResult, SequenceService}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.operation.{OperationId, OperationOfQuestion, _}
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.proposal.ProposalId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar

import scala.collection.immutable.Seq
import scala.concurrent.Future

class QuestionApiTest
    extends MakeApiTestBase
    with MockitoSugar
    with DefaultQuestionApiComponent
    with QuestionServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with OperationServiceComponent
    with OperationOfQuestionServiceComponent
    with PartnerServiceComponent
    with MakeSettingsComponent
    with MakeAuthentication {

  override val questionService: QuestionService = mock[QuestionService]
  override val sequenceService: SequenceService = mock[SequenceService]
  override val persistentOperationOfQuestionService: PersistentOperationOfQuestionService =
    mock[PersistentOperationOfQuestionService]
  override val operationService: OperationService = mock[OperationService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val partnerService: PartnerService = mock[PartnerService]

  val routes: Route = sealRoute(questionApi.routes)

  val baseQuestion = Question(
    questionId = QuestionId("questionid"),
    slug = "question-slug",
    country = Country("FR"),
    language = Language("fr"),
    question = "the question",
    operationId = Some(OperationId("operationid")),
    themeId = None
  )
  val baseOperation = Operation(
    status = OperationStatus.Active,
    operationId = OperationId("operationid"),
    slug = "operation-slug",
    defaultLanguage = Language("fr"),
    allowedSources = Seq("core"),
    operationKind = OperationKind.PublicConsultation,
    events = List.empty,
    questions = Seq.empty,
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now())
  )

  val baseOperationOfQuestion = OperationOfQuestion(
    questionId = baseQuestion.questionId,
    operationId = baseOperation.operationId,
    startDate = Some(ZonedDateTime.parse("2018-10-21T10:15:30+00:00")),
    endDate = None,
    operationTitle = "operation title",
    landingSequenceId = SequenceId("sequenceId"),
    canPropose = true,
    sequenceCardsConfiguration = SequenceCardsConfiguration(
      introCard = IntroCard(enabled = true, title = None, description = None),
      pushProposalCard = PushProposalCard(enabled = true),
      signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
      finalCard = FinalCard(
        enabled = true,
        sharingEnabled = false,
        title = None,
        shareDescription = None,
        learnMoreTitle = None,
        learnMoreTextButton = None,
        linkUrl = None
      )
    ),
    aboutUrl = None,
    metas = Metas(title = None, description = None, picture = None)
  )

  feature("start sequence by question id") {
    val baseOperationOfQuestion = OperationOfQuestion(
      QuestionId("question-id"),
      OperationId("foo-operation-id"),
      None,
      None,
      "Foo operation",
      SequenceId("sequence-id"),
      canPropose = true,
      sequenceCardsConfiguration = SequenceCardsConfiguration(
        introCard = IntroCard(enabled = true, title = None, description = None),
        pushProposalCard = PushProposalCard(enabled = true),
        signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
        finalCard = FinalCard(
          enabled = true,
          sharingEnabled = false,
          title = None,
          shareDescription = None,
          learnMoreTitle = None,
          learnMoreTextButton = None,
          linkUrl = None
        )
      ),
      aboutUrl = None,
      metas = Metas(title = None, description = None, picture = None)
    )
    scenario("valid question") {
      when(persistentOperationOfQuestionService.getById(any[QuestionId]))
        .thenReturn(Future.successful(Some(baseOperationOfQuestion)))
      when(
        sequenceService.startNewSequence(
          maybeUserId = ArgumentMatchers.any[Option[UserId]],
          sequenceId = ArgumentMatchers.any[SequenceId],
          includedProposals = ArgumentMatchers.any[Seq[ProposalId]],
          tagsIds = ArgumentMatchers.any[Option[Seq[TagId]]],
          requestContext = ArgumentMatchers.any[RequestContext]
        )
      ).thenReturn(Future.successful(Some(SequenceResult(SequenceId("sequence-id"), "title", "slug", Seq.empty))))
      Get("/questions/question-id/start-sequence") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
    scenario("invalid question") {
      when(persistentOperationOfQuestionService.getById(any[QuestionId]))
        .thenReturn(Future.successful(None))
      Get("/questions/question-id/start-sequence") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  feature("get question details") {

    val partner: Partner = Partner(
      partnerId = PartnerId("partner1"),
      name = "partner1",
      logo = Some("logo"),
      link = None,
      organisationId = None,
      partnerKind = PartnerKind.Founder,
      questionId = baseQuestion.questionId,
      weight = 20F
    )
    val partner2: Partner = partner.copy(partnerId = PartnerId("partner2"), name = "partner2")

    when(questionService.getQuestionByQuestionIdValueOrSlug(baseQuestion.slug))
      .thenReturn(Future.successful(Some(baseQuestion)))
    when(questionService.getQuestionByQuestionIdValueOrSlug(baseQuestion.questionId.value))
      .thenReturn(Future.successful(Some(baseQuestion)))
    when(operationOfQuestionService.findByQuestionId(baseQuestion.questionId))
      .thenReturn(Future.successful(Some(baseOperationOfQuestion)))
    when(operationService.findOne(baseQuestion.operationId.get)).thenReturn(Future.successful(Some(baseOperation)))
    when(
      partnerService.find(
        questionId = Some(baseQuestion.questionId),
        organisationId = None,
        start = 0,
        end = None,
        sort = None,
        order = None
      )
    ).thenReturn(Future.successful(Seq(partner, partner2)))

    scenario("get by id") {
      Given("a registered question")
      When("I get question details by id")
      Then("I get a question with details")
      Get("/questions/questionid/details") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val questionDetailsResponse: QuestionDetailsResponse = entityAs[QuestionDetailsResponse]
        questionDetailsResponse.operationId should be(baseOperation.operationId)
        questionDetailsResponse.slug should be(baseQuestion.slug)
        questionDetailsResponse.allowedSources should be(baseOperation.allowedSources)
        questionDetailsResponse.country should be(baseQuestion.country)
        questionDetailsResponse.language should be(baseQuestion.language)
        questionDetailsResponse.wording.title should be(baseOperationOfQuestion.operationTitle)
        questionDetailsResponse.startDate should be(baseOperationOfQuestion.startDate)
        questionDetailsResponse.endDate should be(baseOperationOfQuestion.endDate)
      }
    }
    scenario("get by slug") {
      Given("a registered question")
      When("I get question details by slug")
      Then("I get a question with details")
      Get("/questions/questionid/details") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val questionDetailsResponse: QuestionDetailsResponse = entityAs[QuestionDetailsResponse]
        questionDetailsResponse.questionId should be(baseQuestion.questionId)
      }
    }
  }

}
