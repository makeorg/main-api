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
import org.make.api.feature.{
  ActiveFeatureService,
  ActiveFeatureServiceComponent,
  FeatureService,
  FeatureServiceComponent
}
import org.make.api.operation.{PersistentOperationOfQuestionService, _}
import org.make.api.organisation.OrganisationsSearchResultResponse
import org.make.api.partner.{PartnerService, PartnerServiceComponent}
import org.make.api.proposal.{
  ProposalSearchEngine,
  ProposalSearchEngineComponent,
  ProposalService,
  ProposalServiceComponent,
  ProposalsResultResponse
}
import org.make.api.sequence.{SequenceResult, SequenceService}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, Feature, FeatureId}
import org.make.core.operation.indexed.{IndexedOperationOfQuestion, OperationOfQuestionSearchResult}
import org.make.core.operation.{OperationId, OperationOfQuestion, _}
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.proposal.ProposalId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.user._
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar

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
    with MakeAuthentication
    with FeatureServiceComponent
    with ActiveFeatureServiceComponent
    with ProposalSearchEngineComponent
    with TagServiceComponent
    with ProposalServiceComponent {

  override val questionService: QuestionService = mock[QuestionService]
  override val sequenceService: SequenceService = mock[SequenceService]
  override val persistentOperationOfQuestionService: PersistentOperationOfQuestionService =
    mock[PersistentOperationOfQuestionService]
  override val operationService: OperationService = mock[OperationService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val partnerService: PartnerService = mock[PartnerService]
  override val featureService: FeatureService = mock[FeatureService]
  override val activeFeatureService: ActiveFeatureService = mock[ActiveFeatureService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val tagService: TagService = mock[TagService]
  override val proposalService: ProposalService = mock[ProposalService]

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
    metas = Metas(title = None, description = None, picture = None),
    theme = QuestionTheme.default,
    description = OperationOfQuestion.defaultDescription,
    consultationImage = None,
    descriptionImage = None,
    displayResults = false
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
      metas = Metas(title = None, description = None, picture = None),
      theme = QuestionTheme.default,
      description = OperationOfQuestion.defaultDescription,
      consultationImage = None,
      descriptionImage = None,
      displayResults = false
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
    val activeFeature1 = ActiveFeature(
      activeFeatureId = ActiveFeatureId("af1"),
      featureId = FeatureId("f1"),
      maybeQuestionId = Some(baseQuestion.questionId)
    )
    val feature1 = Feature(featureId = FeatureId("f1"), name = "feature 1", slug = "f1")

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
        sort = Some("weight"),
        order = Some("DESC"),
        partnerKind = None
      )
    ).thenReturn(Future.successful(Seq(partner, partner2)))
    when(activeFeatureService.find(maybeQuestionId = Some(baseQuestion.questionId)))
      .thenReturn(Future.successful(Seq(activeFeature1)))
    when(featureService.findByFeatureIds(featureIds = Seq(FeatureId("f1"))))
      .thenReturn(Future.successful(Seq(feature1)))

    when(operationOfQuestionService.findByOperationId(baseOperationOfQuestion.operationId))
      .thenReturn(Future.successful(Seq(baseOperationOfQuestion)))

    when(questionService.getQuestions(Seq(baseQuestion.questionId))).thenReturn(Future.successful(Seq(baseQuestion)))

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
        questionDetailsResponse.operation.questions.size should be(1)
        questionDetailsResponse.operation.questions.map(_.questionId) should contain(baseQuestion.questionId)
        questionDetailsResponse.activeFeatures should be(Seq("f1"))
      }
    }
    scenario("get by slug") {
      Given("a registered question")
      When("I get question details by slug")
      Then("I get a question with details")
      Get("/questions/question-slug/details") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val questionDetailsResponse: QuestionDetailsResponse = entityAs[QuestionDetailsResponse]
        questionDetailsResponse.questionId should be(baseQuestion.questionId)
      }
    }
  }

  feature("search question") {

    val questionResult: IndexedOperationOfQuestion = IndexedOperationOfQuestion(
      questionId = QuestionId("question-id"),
      question = "Question ?",
      slug = "question-slug",
      startDate = None,
      endDate = None,
      theme =
        QuestionTheme(gradientStart = "#000000", gradientEnd = "#ffffff", color = "#424242", fontColor = "#242424"),
      description = "awesome description",
      consultationImage = None,
      country = Country("FR"),
      language = Language("fr"),
      operationId = OperationId("operation-id"),
      operationTitle = "title",
      operationKind = OperationKind.BusinessConsultation.shortName,
      aboutUrl = None
    )

    when(operationOfQuestionService.search(any[OperationOfQuestionSearchQuery]))
      .thenReturn(Future.successful(OperationOfQuestionSearchResult(1, Seq(questionResult))))

    scenario("search all") {
      Get("/questions/search") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val res: OperationOfQuestionSearchResult = entityAs[OperationOfQuestionSearchResult]
        res.total shouldBe 1
        res.results.head.questionId shouldBe QuestionId("question-id")
      }
    }

    scenario("search all full valid params") {
      Get(
        "/questions/search?questionIds=1234,5678&questionContent=content&description=desc&startDate=2042-04-02T00:00:00.000Z&endDate=2042-04-20T00:00:00.000Z&operationKinds=BUSINESS_CONSULTATION,GREAT_CAUSE&language=fr&country=FR&limit=42&skip=1&sort=question&order=ASC"
      ) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val res: OperationOfQuestionSearchResult = entityAs[OperationOfQuestionSearchResult]
        res.total shouldBe 1
        res.results.head.questionId shouldBe QuestionId("question-id")
      }
    }

    scenario("validation error on sort") {
      Get("/questions/search?sort=invalid") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("validation error on order") {
      Get("/questions/search?order=invalid") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("popular tags") {
    scenario("fake question") {
      when(questionService.getQuestion(ArgumentMatchers.eq(QuestionId("fake")))).thenReturn(Future.successful(None))
      Get("/questions/fake/popular-tags") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    def newTag(s: String): Tag =
      Tag(TagId(s), s, TagDisplay.Displayed, TagTypeId("type"), 42F, None, None, None, Country("FR"), Language("fr"))

    val tag1 = newTag("tag1")
    val tag2 = newTag("tag2")
    val tag3 = newTag("tag3")

    when(questionService.getQuestion(ArgumentMatchers.eq(QuestionId("question-id"))))
      .thenReturn(Future.successful(Some(baseQuestion)))

    scenario("all tags") {
      when(
        elasticsearchProposalAPI
          .getPopularTagsByProposal(ArgumentMatchers.eq(QuestionId("question-id")), ArgumentMatchers.eq(10))
      ).thenReturn(
        Future.successful(
          Seq(
            PopularTagResponse(TagId("tag1"), "tag1", 1L),
            PopularTagResponse(TagId("tag2"), "tag2", 5L),
            PopularTagResponse(TagId("tag3"), "tag3", 3L)
          )
        )
      )
      when(tagService.findByTagIds(ArgumentMatchers.eq(Seq(TagId("tag2"), TagId("tag3"), TagId("tag1")))))
        .thenReturn(Future.successful(Seq(tag1, tag2, tag3)))

      Get("/questions/question-id/popular-tags") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val res: Seq[PopularTagResponse] = entityAs[Seq[PopularTagResponse]]
        res.size shouldBe 3
        res.head.tagId shouldBe TagId("tag2")
        res(1).tagId shouldBe TagId("tag3")
        res(2).tagId shouldBe TagId("tag1")
      }
    }

    scenario("with limit and skip") {
      when(
        elasticsearchProposalAPI
          .getPopularTagsByProposal(ArgumentMatchers.eq(QuestionId("question-id")), ArgumentMatchers.eq(3))
      ).thenReturn(
        Future.successful(
          Seq(
            PopularTagResponse(TagId("tag1"), "tag1", 1L),
            PopularTagResponse(TagId("tag2"), "tag2", 5L),
            PopularTagResponse(TagId("tag3"), "tag3", 3L)
          )
        )
      )
      when(tagService.findByTagIds(ArgumentMatchers.eq(Seq(TagId("tag3"), TagId("tag1")))))
        .thenReturn(Future.successful(Seq(tag3, tag1)))

      Get("/questions/question-id/popular-tags?limit=2&skip=1") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val res: Seq[PopularTagResponse] = entityAs[Seq[PopularTagResponse]]
        res.size shouldBe 2
        res.head.tagId shouldBe TagId("tag3")
        res(1).tagId shouldBe TagId("tag1")
      }
    }
  }

  feature("get top proposals") {
    scenario("fake question") {
      when(questionService.getQuestion(ArgumentMatchers.eq(QuestionId("fake")))).thenReturn(Future.successful(None))
      Get("/questions/fake/top-proposals") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("get top proposals") {
      when(questionService.getQuestion(ArgumentMatchers.eq(QuestionId("question-id"))))
        .thenReturn(Future.successful(Some(baseQuestion)))

      when(proposalService.getTopProposals(any[Option[UserId]], any[QuestionId], any[Int], any[RequestContext]))
        .thenReturn(Future.successful(ProposalsResultResponse(total = 0, results = Seq.empty)))

      Get("/questions/question-id/top-proposals") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }

    }
  }

  feature("get partners") {
    scenario("fake question") {
      when(questionService.getQuestion(ArgumentMatchers.eq(QuestionId("fake")))).thenReturn(Future.successful(None))
      Get("/questions/fake/partners") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    def newPartner(partnerId: String, organisationId: Option[String] = None) =
      Partner(
        PartnerId(partnerId),
        partnerId,
        None,
        None,
        organisationId.map(UserId.apply),
        PartnerKind.Actor,
        QuestionId("question-id"),
        0F
      )

    scenario("invalid sortAlgorithm") {
      Get("/questions/question-id/partners?sortAlgorithm=fake") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("partners without organisationId") {
      when(
        partnerService.find(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(Some(1000)),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(QuestionId("question-id"))),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(newPartner("partner1-no-orga"), newPartner("partner2-no-orga"), newPartner("partner3-no-orga"))
        )
      )
      when(
        questionService.getPartners(
          ArgumentMatchers.eq(QuestionId("question-id")),
          ArgumentMatchers.eq(Seq.empty),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(Future.successful(OrganisationSearchResult(0L, Seq.empty)))

      Get("/questions/question-id/partners") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val res: OrganisationSearchResult = entityAs[OrganisationSearchResult]
        res.total shouldBe 0
      }
    }

    scenario("all partners with participation algorithm and partnerKind") {
      def newIndexedOrganisation(organisationId: String) =
        IndexedOrganisation(
          UserId(organisationId),
          Some(organisationId),
          None,
          None,
          None,
          publicProfile = true,
          0,
          0,
          Language("fr"),
          Country("FR"),
          None,
          Seq.empty
        )
      when(
        partnerService.find(
          ArgumentMatchers.eq(0),
          ArgumentMatchers.eq(Some(1000)),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(QuestionId("question-id"))),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(Some(PartnerKind.Actor))
        )
      ).thenReturn(
        Future.successful(
          Seq(
            newPartner("partner1", Some("organisation-1")),
            newPartner("partner2"),
            newPartner("partner3", Some("organisation-2"))
          )
        )
      )
      when(
        questionService.getPartners(
          ArgumentMatchers.eq(QuestionId("question-id")),
          ArgumentMatchers.eq(Seq(UserId("organisation-1"), UserId("organisation-2"))),
          ArgumentMatchers.eq(Some(ParticipationAlgorithm(QuestionId("question-id")))),
          ArgumentMatchers.eq(Some(42)),
          ArgumentMatchers.eq(Some(14))
        )
      ).thenReturn(
        Future.successful(
          OrganisationSearchResult(
            2L,
            Seq(newIndexedOrganisation("organisation-1"), newIndexedOrganisation("organisation-2"))
          )
        )
      )

      Get("/questions/question-id/partners?sortAlgorithm=participation&partnerKind=ACTOR&limit=42&skip=14") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val res: OrganisationsSearchResultResponse = entityAs[OrganisationsSearchResultResponse]
        res.total shouldBe 2L
        res.results.exists(_.organisationId.value == "organisation-1") shouldBe true
        res.results.exists(_.organisationId.value == "organisation-2") shouldBe true
      }
    }
  }

  feature("get question personalities") {
    scenario("bad request") {
      Get("/questions/question-id/personalities?personalityRole=WRONG") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("ok response") {
      when(
        questionService.getQuestionPersonalities(
          start = 0,
          end = None,
          questionId = QuestionId("question-id"),
          personalityRole = None
        )
      ).thenReturn(Future.successful(Seq.empty))

      Get("/questions/question-id/personalities") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

}
