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

package org.make.api.views

import java.time.ZonedDateTime
import java.util.UUID

import org.make.api.MakeUnitTest
import org.make.api.operation._
import org.make.api.proposal._
import org.make.api.question.{QuestionService, QuestionServiceComponent, SearchQuestionRequest}
import org.make.core.idea.{CountrySearchFilter, LanguageSearchFilter}
import org.make.core.operation._
import org.make.core.operation.indexed.{IndexedOperationOfQuestion, OperationOfQuestionSearchResult}
import org.make.core.proposal._
import org.make.core.proposal.indexed.{Author, IndexedProposal, IndexedScores, SequencePool}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
class HomeViewServiceComponentTest
    extends MakeUnitTest
    with DefaultHomeViewServiceComponent
    with QuestionServiceComponent
    with OperationOfQuestionServiceComponent
    with OperationServiceComponent
    with ProposalServiceComponent
    with ProposalSearchEngineComponent
    with CurrentOperationServiceComponent
    with FeaturedOperationServiceComponent {

  override val questionService: QuestionService = mock[QuestionService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val operationService: OperationService = mock[OperationService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val currentOperationService: CurrentOperationService = mock[CurrentOperationService]
  override val featuredOperationService: FeaturedOperationService = mock[FeaturedOperationService]

  val userId: UserId = UserId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()
  val defaultOperation: SimpleOperation = SimpleOperation(
    operationId = OperationId("default"),
    status = OperationStatus.Active,
    slug = "default",
    allowedSources = Seq("core"),
    defaultLanguage = Language("fr"),
    operationKind = OperationKind.PublicConsultation,
    createdAt = Some(now),
    updatedAt = Some(now)
  )
  val defaultQuestion: Question = Question(
    questionId = QuestionId("default"),
    slug = "question1",
    language = Language("fr"),
    country = Country("FR"),
    question = "question default ?",
    operationId = Some(defaultOperation.operationId),
    themeId = None
  )
  val defaultOperationOfQuestion = IndexedOperationOfQuestion(
    questionId = defaultQuestion.questionId,
    operationId = defaultOperation.operationId,
    startDate = Some(now.minusDays(20)),
    endDate = Some(now.plusDays(10)),
    operationTitle = "default opeOfQues",
    question = "",
    description = "Description opeOfQue",
    theme = QuestionTheme("#000000", "#000000", "#000000", "#000000"),
    imageUrl = None,
    country = Country("FR"),
    language = Language("fr"),
    operationKind = "",
    aboutUrl = Some("http://about")
  )

  feature("home view") {
    scenario("get home view data") {

      val operation1: SimpleOperation =
        defaultOperation.copy(
          operationId = OperationId("ope1"),
          slug = "ope1",
          operationKind = OperationKind.BusinessConsultation
        )
      val operation2: SimpleOperation =
        defaultOperation.copy(
          operationId = OperationId("ope2"),
          slug = "ope2",
          operationKind = OperationKind.PublicConsultation
        )
      val operation3: SimpleOperation =
        defaultOperation.copy(
          operationId = OperationId("ope3"),
          slug = "ope3",
          operationKind = OperationKind.GreatCause
        )
      val operation4: SimpleOperation =
        defaultOperation.copy(
          operationId = OperationId("ope4"),
          slug = "ope4",
          operationKind = OperationKind.PrivateConsultation
        )
      val operation5: SimpleOperation =
        defaultOperation.copy(
          operationId = OperationId("ope5"),
          slug = "ope5",
          operationKind = OperationKind.PublicConsultation
        )
      val operation6: SimpleOperation =
        defaultOperation.copy(
          operationId = OperationId("ope6"),
          slug = "ope6",
          operationKind = OperationKind.PublicConsultation
        )
      val question1: Question = defaultQuestion.copy(
        questionId = QuestionId("question1"),
        slug = "question1",
        question = "question 1 ?",
        operationId = Some(operation1.operationId)
      )
      val question2: Question = defaultQuestion.copy(
        questionId = QuestionId("question2"),
        slug = "question2",
        question = "question 2 ?",
        operationId = Some(operation2.operationId)
      )
      val question3: Question = defaultQuestion.copy(
        questionId = QuestionId("question3"),
        slug = "question3",
        question = "question 3 ?",
        operationId = Some(operation3.operationId)
      )
      val question4: Question = defaultQuestion.copy(
        questionId = QuestionId("question4"),
        slug = "question4",
        question = "question 4 ?",
        operationId = Some(operation4.operationId)
      )
      val question5: Question = defaultQuestion.copy(
        questionId = QuestionId("question5"),
        slug = "question5",
        question = "question 5 ?",
        operationId = Some(operation5.operationId)
      )
      val question6: Question = defaultQuestion.copy(
        questionId = QuestionId("question6"),
        slug = "question6",
        question = "question 6 ?",
        operationId = Some(operation6.operationId)
      )
      val operationOfQuestion1: IndexedOperationOfQuestion =
        defaultOperationOfQuestion.copy(questionId = question1.questionId, operationId = operation1.operationId)
      val operationOfQuestion2: IndexedOperationOfQuestion =
        defaultOperationOfQuestion.copy(questionId = question2.questionId, operationId = operation2.operationId)
      val operationOfQuestion3: IndexedOperationOfQuestion =
        defaultOperationOfQuestion.copy(questionId = question3.questionId, operationId = operation3.operationId)
      val operationOfQuestion4: IndexedOperationOfQuestion =
        defaultOperationOfQuestion.copy(questionId = question4.questionId, operationId = operation4.operationId)
      val operationOfQuestion5: IndexedOperationOfQuestion =
        defaultOperationOfQuestion.copy(
          questionId = question5.questionId,
          operationId = operation5.operationId,
          startDate = Some(now.plusDays(5))
        )
      val operationOfQuestion6: IndexedOperationOfQuestion =
        defaultOperationOfQuestion.copy(
          questionId = question6.questionId,
          operationId = operation6.operationId,
          startDate = Some(now.minusDays(8)),
          endDate = Some(now.minusDays(1))
        )

      val operations: Seq[SimpleOperation] = Seq(operation1, operation2, operation3, operation4, operation5, operation6)
      val questions: Seq[Question] = Seq(question1, question2, question3, question4, question5, question6)
      val operationOfQuestions = OperationOfQuestionSearchResult(
        total = 6L,
        results = Seq(
          operationOfQuestion1,
          operationOfQuestion2,
          operationOfQuestion3,
          operationOfQuestion4,
          operationOfQuestion5,
          operationOfQuestion6
        )
      )

      val requestContext: RequestContext =
        RequestContext.empty.copy(language = Some(Language("fr")), country = Some(Country("FR")))
      def indexedProposal(id: ProposalId): IndexedProposal = {
        IndexedProposal(
          id = id,
          userId = UserId(s"user-${id.value}"),
          content = s"proposal with id ${id.value}",
          slug = s"proposal-with-id-${id.value}",
          status = ProposalStatus.Pending,
          createdAt = DateHelper.now(),
          updatedAt = None,
          votes = Seq.empty,
          votesCount = 0,
          votesVerifiedCount = 0,
          toEnrich = false,
          scores = IndexedScores.empty,
          context = None,
          trending = None,
          labels = Seq.empty,
          author = Author(
            firstName = Some(id.value),
            organisationName = None,
            organisationSlug = None,
            postalCode = None,
            age = None,
            avatarUrl = None
          ),
          organisations = Seq.empty,
          country = Country("FR"),
          language = Language("fr"),
          themeId = None,
          tags = Seq.empty,
          ideaId = None,
          operationId = None,
          question = None,
          sequencePool = SequencePool.New,
          initialProposal = false,
          refusalReason = None,
          operationKind = None
        )
      }
      Mockito
        .when(operationService.findSimple())
        .thenReturn(Future.successful(operations))

      Mockito
        .when(
          questionService.searchQuestion(
            ArgumentMatchers.eq(
              SearchQuestionRequest(
                language = Some(Language("fr")),
                country = Some(Country("FR")),
                maybeQuestionIds = Some(questions.map(_.questionId)),
                limit = Some(questions.length)
              )
            )
          )
        )
        .thenReturn(Future.successful(questions))

      Mockito
        .when(
          operationOfQuestionService
            .search(
              searchQuery =
                OperationOfQuestionSearchQuery(limit = Some(10000), sort = Some("startDate"), order = Some("desc"))
            )
        )
        .thenReturn(Future.successful(operationOfQuestions))

      Mockito
        .when(
          elasticsearchProposalAPI.countProposalsByQuestion(ArgumentMatchers.eq(Option(questions.map(_.questionId))))
        )
        .thenReturn(
          Future.successful(
            Map(
              question1.questionId -> 2L,
              question2.questionId -> 5L,
              question3.questionId -> 20L,
              question4.questionId -> 50L
            )
          )
        )
      val searchQueryPopular = SearchQuery(
        limit = Some(2),
        sortAlgorithm = Some(PopularAlgorithm),
        filters = Some(
          SearchFilters(
            language = Some(LanguageSearchFilter(Language("fr"))),
            country = Some(CountrySearchFilter(Country("FR"))),
            question = Some(QuestionSearchFilter(Seq(QuestionId("question2"), QuestionId("question3"))))
          )
        )
      )
      val searchQueryControverse = searchQueryPopular.copy(sortAlgorithm = Some(ControversyAlgorithm))
      Mockito
        .when(
          proposalService.searchForUser(
            ArgumentMatchers.eq(Some(userId)),
            ArgumentMatchers.eq(searchQueryPopular),
            ArgumentMatchers.eq(requestContext)
          )
        )
        .thenReturn(
          Future.successful(
            ProposalsResultSeededResponse(
              0,
              Seq(
                ProposalResponse(
                  indexedProposal = indexedProposal(ProposalId("proposal1")),
                  myProposal = false,
                  voteAndQualifications = None,
                  proposalKey = "pr0p0541k3y"
                )
              ),
              None
            )
          )
        )
      Mockito
        .when(
          proposalService.searchForUser(
            ArgumentMatchers.eq(Some(userId)),
            ArgumentMatchers.eq(searchQueryControverse),
            ArgumentMatchers.eq(requestContext)
          )
        )
        .thenReturn(
          Future.successful(
            ProposalsResultSeededResponse(
              0,
              Seq(
                ProposalResponse(
                  indexedProposal = indexedProposal(ProposalId("proposal2")),
                  myProposal = false,
                  voteAndQualifications = None,
                  proposalKey = "pr0p0541k3y"
                )
              ),
              None
            )
          )
        )

      Mockito
        .when(currentOperationService.getAll)
        .thenReturn(
          Future.successful(
            Seq(
              CurrentOperation(
                currentOperationId = CurrentOperationId("current1"),
                questionId = question5.questionId,
                label = "current 1 label",
                description = "description current 1",
                picture = "http://picture-current-1",
                altPicture = "alt picture current 1",
                linkLabel = "link label current 1",
                internalLink = None,
                externalLink = None
              )
            )
          )
        )

      Mockito
        .when(featuredOperationService.getAll)
        .thenReturn(
          Future.successful(
            Seq(
              FeaturedOperation(
                featuredOperationId = FeaturedOperationId("featured1"),
                questionId = Some(question4.questionId),
                title = "featured 1 title",
                description = Some("featured 1 description"),
                landscapePicture = "http://featured1-landscape-picture",
                portraitPicture = "http://featured1-portrait-picture",
                altPicture = "featured1 alt picture",
                label = "featured 1 label",
                buttonLabel = "featured 1 button label",
                internalLink = None,
                externalLink = None,
                slot = 1
              )
            )
          )
        )

      val futureHomeViewResponse: Future[HomeViewResponse] =
        homeViewService.getHomeViewResponse(Language("fr"), Country("FR"), Some(userId), requestContext)
      whenReady(futureHomeViewResponse, Timeout(3.seconds)) { homeViewResponse =>
        homeViewResponse.businessConsultations.length shouldBe 1
        homeViewResponse.businessConsultations.head.aboutUrl shouldBe Some("http://about")
        homeViewResponse.businessConsultations.head.endDate shouldBe Some(now.plusDays(10))
        homeViewResponse.businessConsultations.head.startDate shouldBe Some(now.minusDays(20))
        homeViewResponse.businessConsultations.head.endDate shouldBe Some(now.plusDays(10))
        homeViewResponse.businessConsultations.head.question shouldBe "question 1 ?"
        homeViewResponse.businessConsultations.head.slug shouldBe Some("question1")
        homeViewResponse.businessConsultations.head.theme shouldBe BusinessConsultationThemeResponse(
          "#000000",
          "#000000"
        )
        homeViewResponse.currentConsultations.length shouldBe 1
        homeViewResponse.currentConsultations.head.description shouldBe "description current 1"
        homeViewResponse.currentConsultations.head.questionId.get shouldBe question5.questionId
        homeViewResponse.currentConsultations.head.startDate shouldBe Some(now.plusDays(5))
        homeViewResponse.currentConsultations.head.questionSlug.get shouldBe "question5"
        homeViewResponse.featuredConsultations.length shouldBe 1
        homeViewResponse.featuredConsultations.head.label shouldBe "featured 1 label"
        homeViewResponse.featuredConsultations.head.questionSlug shouldBe Some("question4")
        homeViewResponse.featuredConsultations.head.slot shouldBe 1

        homeViewResponse.popularProposals.length shouldBe 1
        homeViewResponse.popularProposals.head.id shouldBe ProposalId("proposal1")
        homeViewResponse.controverseProposals.length shouldBe 1
        homeViewResponse.controverseProposals.head.id shouldBe ProposalId("proposal2")
      }
    }
  }
}
