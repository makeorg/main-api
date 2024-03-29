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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import eu.timepit.refined.auto._
import org.make.api.MakeApiTestBase
import org.make.api.demographics.{ActiveDemographicsCardService, ActiveDemographicsCardServiceComponent}
import org.make.api.feature.{
  ActiveFeatureService,
  ActiveFeatureServiceComponent,
  FeatureService,
  FeatureServiceComponent
}
import org.make.api.idea.topIdeaComments.{TopIdeaCommentService, TopIdeaCommentServiceComponent}
import org.make.api.keyword.{KeywordService, KeywordServiceComponent}
import org.make.api.operation._
import org.make.api.organisation.OrganisationsSearchResultResponse
import org.make.api.partner.{PartnerService, PartnerServiceComponent}
import org.make.api.personality.{PersonalityRoleService, PersonalityRoleServiceComponent}
import org.make.api.proposal._
import org.make.api.sequence.SequenceService
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.core.feature.{ActiveFeature, ActiveFeatureId, FeatureId, FeatureSlug, Feature => Feat}
import org.make.core.idea.{IdeaId, TopIdea, TopIdeaId, TopIdeaScores}
import org.make.core.keyword.Keyword
import org.make.core.operation.indexed.{IndexedOperationOfQuestion, OperationOfQuestionSearchResult}
import org.make.core.operation.{OperationId, _}
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.personality.{PersonalityRole, PersonalityRoleId}
import org.make.core.proposal.indexed.{ProposalsSearchResult, SequencePool, Zone}
import org.make.core.proposal.{PopularAlgorithm, ProposalId, QuestionSearchFilter, SearchFilters, SearchQuery}
import org.make.core.question.{Question, QuestionId, TopProposalsMode}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.user._
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult}
import org.make.core.{DateHelper, Order, RequestContext, ValidationError}

import java.time.ZonedDateTime
import scala.collection.immutable.Seq
import scala.concurrent.Future

class QuestionApiTest
    extends MakeApiTestBase
    with DefaultQuestionApiComponent
    with ActiveDemographicsCardServiceComponent
    with ActiveFeatureServiceComponent
    with FeatureServiceComponent
    with KeywordServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with OperationOfQuestionServiceComponent
    with OperationServiceComponent
    with PartnerServiceComponent
    with PersonalityRoleServiceComponent
    with ProposalSearchEngineComponent
    with ProposalServiceComponent
    with QuestionServiceComponent
    with TagServiceComponent
    with TopIdeaCommentServiceComponent {

  override val questionService: QuestionService = mock[QuestionService]
  override val sequenceService: SequenceService = mock[SequenceService]
  override val operationService: OperationService = mock[OperationService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val partnerService: PartnerService = mock[PartnerService]
  override val featureService: FeatureService = mock[FeatureService]
  override val activeFeatureService: ActiveFeatureService = mock[ActiveFeatureService]
  override val elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine =
    mock[OperationOfQuestionSearchEngine]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val tagService: TagService = mock[TagService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val topIdeaCommentService: TopIdeaCommentService = mock[TopIdeaCommentService]
  override val personalityRoleService: PersonalityRoleService = mock[PersonalityRoleService]
  override val keywordService: KeywordService = mock[KeywordService]
  override val activeDemographicsCardService: ActiveDemographicsCardService = mock[ActiveDemographicsCardService]

  val routes: Route = sealRoute(questionApi.routes)

  val baseQuestion: Question = Question(
    questionId = QuestionId("questionid"),
    slug = "question-slug",
    countries = NonEmptyList.of(Country("FR")),
    language = Language("fr"),
    question = "the question",
    shortTitle = None,
    operationId = Some(OperationId("operationid"))
  )
  val baseOperation: Operation = Operation(
    status = OperationStatus.Active,
    operationId = OperationId("operationid"),
    slug = "operation-slug",
    operationKind = OperationKind.BusinessConsultation,
    events = List.empty,
    questions = Seq.empty,
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now())
  )

  val baseOperationOfQuestion: OperationOfQuestion = operationOfQuestion(
    questionId = baseQuestion.questionId,
    operationId = baseOperation.operationId,
    startDate = ZonedDateTime.parse("2018-10-21T10:15:30+00:00"),
    endDate = ZonedDateTime.parse("2068-10-21T10:15:30+00:00"),
    landingSequenceId = SequenceId("sequenceId"),
    resultsLink = Some(ResultsLink.Internal.TopIdeas)
  )

  val now: ZonedDateTime = DateHelper.now()
  val baseSimpleOperation: SimpleOperation = SimpleOperation(
    baseOperation.operationId,
    baseOperation.status,
    baseOperation.slug,
    baseOperation.operationKind,
    baseOperation.createdAt,
    baseOperation.updatedAt
  )
  val openOperationOfQuestion: IndexedOperationOfQuestion = IndexedOperationOfQuestion
    .createFromOperationOfQuestion(
      baseOperationOfQuestion.copy(startDate = now.minusDays(1), endDate = now.plusDays(1)),
      baseSimpleOperation,
      baseQuestion
    )
    .copy(top20ConsensusThreshold = Some(0.5))
  val finishedOperationOfQuestion: IndexedOperationOfQuestion =
    IndexedOperationOfQuestion.createFromOperationOfQuestion(
      baseOperationOfQuestion.copy(startDate = now.minusDays(2), endDate = now.minusDays(1)),
      baseSimpleOperation,
      baseQuestion
    )
  val upcomingOperationOfQuestion: IndexedOperationOfQuestion =
    IndexedOperationOfQuestion.createFromOperationOfQuestion(
      baseOperationOfQuestion.copy(startDate = now.plusDays(1), endDate = now.plusDays(2)),
      baseSimpleOperation,
      baseQuestion
    )
  val indexedOperationOfQuestions: Seq[IndexedOperationOfQuestion] =
    Seq(openOperationOfQuestion, finishedOperationOfQuestion, upcomingOperationOfQuestion)

  when(operationOfQuestionService.search(any[OperationOfQuestionSearchQuery])).thenAnswer {
    query: OperationOfQuestionSearchQuery =>
      val result =
        indexedOperationOfQuestions.filter(
          i => query.filters.flatMap(_.status).map(_.status).fold(true)(_.toList.contains(i.status))
        )
      Future.successful(OperationOfQuestionSearchResult(result.size, result))
  }

  when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(any)).thenAnswer { _: QuestionId =>
    Future.successful(Some(openOperationOfQuestion))
  }

  when(elasticsearchProposalAPI.countProposals(any[SearchQuery])).thenAnswer { query: SearchQuery =>
    Future.successful((query.filters.flatMap(_.zone.map(_.zone)) match {
      case Some(Zone.Consensus) =>
        (96 * query.filters.flatMap(_.minScoreLowerBound.map(_.minLowerBound)).getOrElse(0d)).toInt
      case Some(Zone.Controversy) => 24
      case _                      => 0
    }) / (if (query.filters.flatMap(_.sequencePool.map(_.sequencePool)).contains(SequencePool.Tested)) 1 else 2))
  }

  Feature("get question details") {

    val partner: Partner = Partner(
      partnerId = PartnerId("partner1"),
      name = "partner1",
      logo = Some("logo"),
      link = None,
      organisationId = None,
      partnerKind = PartnerKind.Founder,
      questionId = baseQuestion.questionId,
      weight = 20f
    )
    val partner2: Partner = partner.copy(partnerId = PartnerId("partner2"), name = "partner2")

    val activeFeature1 = ActiveFeature(
      activeFeatureId = ActiveFeatureId("af1"),
      featureId = FeatureId("f1"),
      maybeQuestionId = Some(baseQuestion.questionId)
    )

    val activeFeature2 = ActiveFeature(
      activeFeatureId = ActiveFeatureId("af2"),
      featureId = FeatureId("f2"),
      maybeQuestionId = Some(baseQuestion.questionId)
    )

    val feature1 = Feat(featureId = FeatureId("f1"), name = "feature 1", slug = FeatureSlug("f1"))

    val feature2 =
      Feat(featureId = FeatureId("f2"), name = "Activate widget intro card", slug = FeatureSlug.DisplayIntroCardWidget)

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
        start = Start.zero,
        end = None,
        sort = Some("weight"),
        order = Some(Order.desc),
        partnerKind = None
      )
    ).thenReturn(Future.successful(Seq(partner, partner2)))

    when(featureService.findByFeatureIds(featureIds = Seq(FeatureId("f1"))))
      .thenReturn(Future.successful(Seq(feature1)))

    when(featureService.findByFeatureIds(featureIds = Seq(FeatureId("f2"))))
      .thenReturn(Future.successful(Seq(feature2)))

    when(operationOfQuestionService.findByOperationId(baseOperationOfQuestion.operationId))
      .thenReturn(Future.successful(Seq(baseOperationOfQuestion)))

    when(questionService.getQuestions(Seq(baseQuestion.questionId))).thenReturn(Future.successful(Seq(baseQuestion)))

    Scenario("get by id") {
      Given("a registered question")
      When("I get question details by id")
      Then("I get a question with details")

      when(activeFeatureService.find(maybeQuestionId = Some(Seq(baseQuestion.questionId))))
        .thenReturn(Future.successful(Seq(activeFeature1)))
      when(activeDemographicsCardService.count(any[Option[QuestionId]], eqTo(None))).thenReturn(Future.successful(1))

      Get("/questions/questionid/details") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val questionDetailsResponse: QuestionDetailsResponse = entityAs[QuestionDetailsResponse]
        questionDetailsResponse.operationId should be(baseOperation.operationId)
        questionDetailsResponse.slug should be(baseQuestion.slug)
        questionDetailsResponse.countries should be(baseQuestion.countries)
        questionDetailsResponse.language should be(baseQuestion.language)
        questionDetailsResponse.wording.title should be(baseOperationOfQuestion.operationTitle)
        questionDetailsResponse.startDate should be(baseOperationOfQuestion.startDate)
        questionDetailsResponse.endDate should be(baseOperationOfQuestion.endDate)
        questionDetailsResponse.operation.questions.size should be(1)
        questionDetailsResponse.operation.questions.map(_.questionId) should contain(baseQuestion.questionId)
        questionDetailsResponse.operation.questions.flatMap(_.resultsLink) should contain(
          ResultsLinkResponse(ResultsLinkKind.Internal, ResultsLink.Internal.TopIdeas.value)
        )
        questionDetailsResponse.activeFeatures should be(Seq(FeatureSlug("f1")))
        questionDetailsResponse.activeFeatureData.topProposal should be(empty)
        questionDetailsResponse.controversyCount shouldBe 24
        questionDetailsResponse.topProposalCount shouldBe 48
        questionDetailsResponse.hasDemographics shouldBe true
      }
    }

    Scenario("get by slug") {
      Given("a registered question")
      When("I get question details by slug")
      Then("I get a question with details")

      when(activeFeatureService.find(maybeQuestionId = Some(Seq(baseQuestion.questionId))))
        .thenReturn(Future.successful(Seq(activeFeature1)))

      Get("/questions/question-slug/details") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val questionDetailsResponse: QuestionDetailsResponse = entityAs[QuestionDetailsResponse]
        questionDetailsResponse.questionId should be(baseQuestion.questionId)
      }
    }

    Scenario("with widget intro card activated and no demographics") {
      when(activeFeatureService.find(maybeQuestionId = Some(Seq(baseQuestion.questionId))))
        .thenReturn(Future.successful(Seq(activeFeature2)))
      when(activeDemographicsCardService.count(any[Option[QuestionId]], eqTo(None))).thenReturn(Future.successful(0))

      val searchRequest = SearchQuery(
        limit = Some(1),
        sortAlgorithm = Some(PopularAlgorithm),
        filters = Some(SearchFilters(question = Some(QuestionSearchFilter(Seq(baseQuestion.questionId)))))
      )

      val proposalId = ProposalId("top-proposal")
      when(proposalService.search(searchRequest, RequestContext.empty))
        .thenReturn(Future.successful(ProposalsSearchResult(1, Seq(indexedProposal(proposalId)))))

      Get("/questions/question-slug/details") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val questionDetailsResponse: QuestionDetailsResponse = entityAs[QuestionDetailsResponse]
        questionDetailsResponse.questionId should be(baseQuestion.questionId)
        questionDetailsResponse.activeFeatureData.topProposal.map(_.id) should contain(proposalId)
        questionDetailsResponse.hasDemographics shouldBe false
      }
    }
  }

  Feature("search question") {

    Scenario("search all") {
      Get("/questions/search") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val res: OperationOfQuestionSearchResult = entityAs[OperationOfQuestionSearchResult]
        res.total shouldBe 3
        res.results.foreach(_.questionId shouldBe QuestionId("questionid"))
      }
    }

    Scenario("search all full valid params") {
      Get(
        "/questions/search?questionIds=1234,5678&questionContent=content&description=desc&startDate=2042-04-02T00:00:00.000Z&endDate=2042-04-20T00:00:00.000Z&operationKinds=BUSINESS_CONSULTATION,GREAT_CAUSE&language=fr&country=FR&limit=42&skip=1&sort=question&order=ASC"
      ) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val res: OperationOfQuestionSearchResult = entityAs[OperationOfQuestionSearchResult]
        res.total shouldBe 3
        res.results.foreach(_.questionId shouldBe QuestionId("questionid"))
      }
    }

    Scenario("validation error on sort") {
      Get("/questions/search?sort=invalid") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("validation error on order") {
      Get("/questions/search?order=invalid") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("popular tags") {
    Scenario("fake question") {
      when(questionService.getCachedQuestion(eqTo(QuestionId("fake")))).thenReturn(Future.successful(None))
      Get("/questions/fake/popular-tags") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    def newTag(s: String): Tag =
      Tag(TagId(s), s, TagDisplay.Displayed, TagTypeId("type"), 42f, None, None)

    val tag1 = newTag("tag1")
    val tag2 = newTag("tag2")
    val tag3 = newTag("tag3")

    when(questionService.getCachedQuestion(eqTo(QuestionId("question-id"))))
      .thenReturn(Future.successful(Some(baseQuestion)))

    Scenario("all tags") {
      when(
        elasticsearchProposalAPI
          .getPopularTagsByProposal(eqTo(QuestionId("question-id")), eqTo(Int.MaxValue))
      ).thenReturn(
        Future.successful(
          Seq(
            PopularTagResponse(TagId("tag1"), "tag1", 1L),
            PopularTagResponse(TagId("tag2"), "tag2", 5L),
            PopularTagResponse(TagId("tag3"), "tag3", 3L)
          )
        )
      )
      when(tagService.findByTagIds(eqTo(Seq(TagId("tag2"), TagId("tag3"), TagId("tag1")))))
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

    Scenario("with limit and skip") {
      when(
        elasticsearchProposalAPI
          .getPopularTagsByProposal(eqTo(QuestionId("question-id")), eqTo(3))
      ).thenReturn(
        Future.successful(
          Seq(
            PopularTagResponse(TagId("tag1"), "tag1", 1L),
            PopularTagResponse(TagId("tag2"), "tag2", 5L),
            PopularTagResponse(TagId("tag3"), "tag3", 3L)
          )
        )
      )
      when(tagService.findByTagIds(eqTo(Seq(TagId("tag3"), TagId("tag1")))))
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

  Feature("get top proposals") {
    Scenario("fake question") {
      when(questionService.getCachedQuestion(eqTo(QuestionId("fake")))).thenReturn(Future.successful(None))
      Get("/questions/fake/top-proposals") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("get top proposals") {
      when(questionService.getCachedQuestion(eqTo(QuestionId("question-id"))))
        .thenReturn(Future.successful(Some(baseQuestion)))

      when(
        proposalService
          .getTopProposals(
            any[Option[UserId]],
            any[QuestionId],
            any[Int],
            any[Option[TopProposalsMode]],
            any[RequestContext]
          )
      ).thenReturn(Future.successful(ProposalsResultResponse(total = 0, results = Seq.empty)))

      Get("/questions/question-id/top-proposals") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }

    }
  }

  Feature("get partners") {
    Scenario("fake question") {
      when(questionService.getCachedQuestion(eqTo(QuestionId("fake")))).thenReturn(Future.successful(None))
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
        0f
      )

    Scenario("invalid sortAlgorithm") {
      Get("/questions/question-id/partners?sortAlgorithm=fake") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("partners without organisationId") {
      when(
        partnerService.find(
          eqTo(Start.zero),
          eqTo(Some(End(1000))),
          eqTo(None),
          eqTo(None),
          eqTo(Some(QuestionId("question-id"))),
          eqTo(None),
          eqTo(None)
        )
      ).thenReturn(
        Future.successful(
          Seq(newPartner("partner1-no-orga"), newPartner("partner2-no-orga"), newPartner("partner3-no-orga"))
        )
      )
      when(
        questionService
          .getPartners(eqTo(QuestionId("question-id")), eqTo(Seq.empty), eqTo(None), eqTo(None), eqTo(None))
      ).thenReturn(Future.successful(OrganisationSearchResult(0L, Seq.empty)))

      Get("/questions/question-id/partners") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val res: OrganisationSearchResult = entityAs[OrganisationSearchResult]
        res.total shouldBe 0
      }
    }

    Scenario("all partners with participation algorithm and partnerKind") {
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
          Country("FR"),
          None,
          Seq.empty
        )
      when(
        partnerService.find(
          eqTo(Start.zero),
          eqTo(Some(End(1000))),
          eqTo(None),
          eqTo(None),
          eqTo(Some(QuestionId("question-id"))),
          eqTo(None),
          eqTo(Some(PartnerKind.Actor))
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
          eqTo(QuestionId("question-id")),
          eqTo(Seq(UserId("organisation-1"), UserId("organisation-2"))),
          eqTo(Some(ParticipationAlgorithm(QuestionId("question-id")))),
          eqTo(Some(42)),
          eqTo(Some(14))
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

  Feature("get question personalities") {
    Scenario("bad request") {

      when(
        personalityRoleService
          .find(start = Start.zero, end = None, sort = None, order = None, roleIds = None, name = Some("WRONG"))
      ).thenReturn(Future.successful(Seq.empty))

      Get("/questions/question-id/personalities?personalityRole=WRONG") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("ok response") {
      when(
        questionService.getQuestionPersonalities(
          start = Start.zero,
          end = None,
          questionId = QuestionId("question-id"),
          personalityRoleId = None
        )
      ).thenReturn(Future.successful(Seq.empty))
      when(
        personalityRoleService
          .find(start = Start.zero, end = None, sort = None, order = None, roleIds = None, name = None)
      ).thenReturn(Future.successful(Seq(PersonalityRole(PersonalityRoleId("candidate"), "CANDIDATE"))))

      Get("/questions/question-id/personalities") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("get question top ideas") {

    Scenario("ok response") {
      when(
        questionService.getTopIdeas(
          start = eqTo(Start.zero),
          end = eqTo(None),
          seed = eqTo(None),
          questionId = eqTo(QuestionId("question-id"))
        )
      ).thenReturn(Future.successful(QuestionTopIdeasResponseWithSeed(Seq.empty, 42)))

      Get("/questions/question-id/top-ideas") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("get topIdea by id") {
    Scenario("ok response") {
      when(questionService.getTopIdea(eqTo(TopIdeaId("top-idea-id")), eqTo(QuestionId("question-id")), eqTo(None)))
        .thenReturn(
          Future.successful(
            Some(
              QuestionTopIdeaResultWithSeed(
                topIdea = TopIdea(
                  topIdeaId = TopIdeaId("top-idea-id"),
                  ideaId = IdeaId("idea-id"),
                  questionId = QuestionId("question-id"),
                  name = "name",
                  label = "label",
                  scores = TopIdeaScores(0, 0, 0),
                  weight = 0
                ),
                avatars = Seq.empty,
                proposalsCount = 0,
                seed = 42
              )
            )
          )
        )

      when(topIdeaCommentService.getCommentsWithPersonality(topIdeaIds = Seq(TopIdeaId("top-idea-id"))))
        .thenReturn(Future.successful(Seq.empty))

      Get("/questions/question-id/top-ideas/top-idea-id") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("not found") {
      when(questionService.getTopIdea(eqTo(TopIdeaId("not-found")), eqTo(QuestionId("question-id")), eqTo(None)))
        .thenReturn(Future.successful(None))

      Get("/questions/question-id/top-ideas/not-found") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  Feature("list") {

    Scenario("all statuses") {
      Get("/questions?country=FR&language=fr") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val response = entityAs[QuestionListResponse]
        response.results should contain theSameElementsAs indexedOperationOfQuestions.map(
          QuestionOfOperationResponse.apply
        )
      }
    }

    Scenario("one status") {
      Get("/questions?country=FR&language=fr&status=open") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val response = entityAs[QuestionListResponse]
        response.results should be(Seq(QuestionOfOperationResponse.apply(openOperationOfQuestion)))
      }
    }

    Scenario("invalid status") {
      Get("/questions?country=FR&language=fr&status=foo") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

  }

  Feature("featured-proposals") {
    Scenario("fake question") {
      when(questionService.getCachedQuestion(eqTo(QuestionId("fake")))).thenReturn(Future.successful(None))
      Get("/questions/fake/featured-proposals") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("get featured proposals") {
      when(questionService.getCachedQuestion(eqTo(QuestionId("question-id"))))
        .thenReturn(Future.successful(Some(baseQuestion)))

      when(
        proposalService.questionFeaturedProposals(
          any[QuestionId],
          any[Int],
          any[Int],
          any[Option[Int]],
          any[Option[UserId]],
          any[RequestContext]
        )
      ).thenReturn(Future.successful(ProposalsResultSeededResponse(total = 0, results = Seq.empty, seed = None)))

      Get("/questions/question-id/featured-proposals?maxPartnerProposals=5&limit=10") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }

    }

    Scenario("invalid") {
      Get("/questions/question-id/featured-proposals?maxPartnerProposals=-1&limit=-2") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        errors.size shouldBe 3
        errors.map(_.field).toSet shouldBe Set("maxPartnerProposals", "limit")
      }
    }
  }

  Feature("keywords") {
    Scenario("fake question") {
      when(questionService.getCachedQuestion(eqTo(QuestionId("fake")))).thenReturn(Future.successful(None))
      Get("/questions/fake/keywords") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("get keywords") {
      val qId = QuestionId("question-id")
      when(questionService.getCachedQuestion(eqTo(qId)))
        .thenReturn(Future.successful(Some(baseQuestion)))

      when(keywordService.findTop(eqTo(qId), eqTo(5)))
        .thenReturn(
          Future.successful(
            Seq(
              Keyword(qId, "key", "label", 4.2f, 14, topKeyword = true),
              Keyword(qId, "key-2", "label-2", -4.2f, 21, topKeyword = true)
            )
          )
        )

      Get("/questions/question-id/keywords?limit=5") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }
}
