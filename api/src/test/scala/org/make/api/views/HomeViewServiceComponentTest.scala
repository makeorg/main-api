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

import cats.data.NonEmptyList
import org.make.api.MakeUnitTest
import org.make.api.operation._
import org.make.api.post.{PostService, PostServiceComponent}
import org.make.api.question.QuestionOfOperationResponse
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.views.HomePageViewResponse.Highlights
import org.make.core.operation.indexed.{IndexedOperationOfQuestion, OperationOfQuestionSearchResult}
import org.make.core.operation._
import org.make.core.post.PostId
import org.make.core.post.indexed._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.{Role, UserId, UserType}
import org.make.core.{DateHelper, Order}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class HomeViewServiceComponentTest
    extends MakeUnitTest
    with DefaultHomeViewServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with UserServiceComponent
    with PostServiceComponent {

  override val elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine =
    mock[OperationOfQuestionSearchEngine]
  override val userService: UserService = mock[UserService]
  override val postService: PostService = mock[PostService]

  val userId: UserId = UserId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()
  val defaultOperation: SimpleOperation = SimpleOperation(
    operationId = OperationId("default"),
    status = OperationStatus.Active,
    slug = "default",
    operationKind = OperationKind.BusinessConsultation,
    createdAt = Some(now),
    updatedAt = Some(now)
  )
  val defaultQuestion: Question = Question(
    questionId = QuestionId("default"),
    slug = "question1",
    language = Language("fr"),
    countries = NonEmptyList.of(Country("FR")),
    question = "question default ?",
    shortTitle = None,
    operationId = Some(defaultOperation.operationId)
  )
  val defaultOperationOfQuestion: IndexedOperationOfQuestion = IndexedOperationOfQuestion(
    questionId = defaultQuestion.questionId,
    operationId = defaultOperation.operationId,
    startDate = Some(now.minusDays(20)),
    endDate = Some(now.plusDays(10)),
    operationTitle = "default opeOfQues",
    question = "",
    slug = "",
    questionShortTitle = defaultQuestion.shortTitle,
    description = "Description opeOfQue",
    theme = QuestionTheme("#000000", "#000000", "#000000", "#000000", None, None),
    consultationImage = None,
    consultationImageAlt = None,
    descriptionImage = None,
    descriptionImageAlt = None,
    countries = NonEmptyList.of(Country("FR")),
    language = Language("fr"),
    operationKind = "",
    aboutUrl = Some("http://about"),
    resultsLink = None,
    proposalsCount = 42,
    participantsCount = 84,
    actions = None,
    featured = false,
    status = OperationOfQuestion.Status.Finished
  )

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
      operationKind = OperationKind.BusinessConsultation
    )
  val operation3: SimpleOperation =
    defaultOperation.copy(operationId = OperationId("ope3"), slug = "ope3", operationKind = OperationKind.GreatCause)
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
      operationKind = OperationKind.BusinessConsultation
    )
  val operation6: SimpleOperation =
    defaultOperation.copy(
      operationId = OperationId("ope6"),
      slug = "ope6",
      operationKind = OperationKind.BusinessConsultation
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
    defaultOperationOfQuestion.copy(
      questionId = question1.questionId,
      operationId = operation1.operationId,
      featured = true
    )
  val operationOfQuestion2: IndexedOperationOfQuestion =
    defaultOperationOfQuestion.copy(
      questionId = question2.questionId,
      operationId = operation2.operationId,
      status = OperationOfQuestion.Status.Open
    )
  val operationOfQuestion3: IndexedOperationOfQuestion =
    defaultOperationOfQuestion.copy(
      questionId = question3.questionId,
      operationId = operation3.operationId,
      endDate = defaultOperationOfQuestion.endDate.map(_.plusDays(1)),
      featured = true,
      status = OperationOfQuestion.Status.Open
    )
  val operationOfQuestion4: IndexedOperationOfQuestion =
    defaultOperationOfQuestion.copy(questionId = question4.questionId, operationId = operation4.operationId)
  val operationOfQuestion5: IndexedOperationOfQuestion =
    defaultOperationOfQuestion.copy(
      questionId = question5.questionId,
      operationId = operation5.operationId,
      startDate = Some(now.plusDays(5)),
      status = OperationOfQuestion.Status.Upcoming
    )
  val operationOfQuestion6: IndexedOperationOfQuestion =
    defaultOperationOfQuestion.copy(
      questionId = question6.questionId,
      operationId = operation6.operationId,
      startDate = Some(now.minusDays(8)),
      endDate = Some(now.minusDays(1)),
      status = OperationOfQuestion.Status.Upcoming
    )

  val operations: Seq[SimpleOperation] = Seq(operation1, operation2, operation3, operation4, operation5, operation6)
  val questions: Seq[Question] = Seq(question1, question2, question3, question4, question5, question6)
  val operationOfQuestions: OperationOfQuestionSearchResult = OperationOfQuestionSearchResult(
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

  Feature("home view") {
    Scenario("home-page view") {

      when(
        userService.adminCountUsers(
          any[Option[String]],
          any[Option[String]],
          any[Option[String]],
          any[Option[Role]],
          eqTo(Some(UserType.UserTypeOrganisation))
        )
      ).thenReturn(Future.successful(9001))

      when(elasticsearchOperationOfQuestionAPI.highlights())
        .thenReturn(Future.successful(Highlights(42, 84, 0)))

      when(
        elasticsearchOperationOfQuestionAPI
          .searchOperationOfQuestions(
            argThat(
              (arg: OperationOfQuestionSearchQuery) =>
                arg match {
                  case OperationOfQuestionSearchQuery(
                      Some(
                        OperationOfQuestionSearchFilters(_, _, _, _, _, _, _, _, Some(FeaturedSearchFilter(true)), _)
                      ),
                      _,
                      _,
                      _,
                      _,
                      _
                      ) =>
                    true
                  case _ => false
                }
            )
          )
      ).thenReturn(Future.successful {
        val results = operationOfQuestions.results.filter(_.featured).sortBy(_.endDate).reverse
        OperationOfQuestionSearchResult(results.size, results)
      })

      when(
        elasticsearchOperationOfQuestionAPI
          .searchOperationOfQuestions(
            argThat(
              (arg: OperationOfQuestionSearchQuery) =>
                arg match {
                  case OperationOfQuestionSearchQuery(
                      Some(OperationOfQuestionSearchFilters(_, _, _, _, _, _, _, _, _, Some(StatusSearchFilter(_)))),
                      _,
                      _,
                      _,
                      _,
                      _
                      ) =>
                    true
                  case _ => false
                }
            )
          )
      ).thenReturn(Future.successful {
        val results = operationOfQuestions.results
          .filter(_.status == OperationOfQuestion.Status.Open)
          .sortBy(e => (e.featured, e.endDate))
          .reverse
        OperationOfQuestionSearchResult(results.size, results)
      })

      val indexedPosts: Seq[IndexedPost] =
        Seq(indexedPost(PostId("post-id-1")), indexedPost(PostId("post-id-2")))
      val postsResponses: Seq[HomePageViewResponse.PostResponse] =
        indexedPosts.map(HomePageViewResponse.PostResponse.fromIndexedPost)

      when(
        postService.search(
          eqTo(
            PostSearchQuery(
              filters = Some(PostSearchFilters(displayHome = Some(DisplayHomeSearchFilter(true)))),
              sort = Some(PostElasticsearchFieldNames.postDate),
              order = Some(Order.desc),
              limit = Some(3)
            )
          )
        )
      ).thenReturn(Future.successful(PostSearchResult(2, indexedPosts)))

      val futureHomePageViewResponse: Future[HomePageViewResponse] =
        homeViewService.getHomePageViewResponse(Country("FR"))
      whenReady(futureHomePageViewResponse, Timeout(3.seconds)) { homePageViewResponse =>
        homePageViewResponse.posts should be(postsResponses)
        homePageViewResponse.currentQuestions should be(
          Seq(operationOfQuestion3, operationOfQuestion2).map(QuestionOfOperationResponse.apply)
        )
        homePageViewResponse.featuredQuestions should be(
          Seq(operationOfQuestion3, operationOfQuestion1).map(QuestionOfOperationResponse.apply)
        )
        homePageViewResponse.highlights should be(Highlights(42, 84, 9001))
      }

    }
  }
}
