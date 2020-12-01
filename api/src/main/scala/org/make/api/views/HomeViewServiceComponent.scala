/*
 *  Make.org Core API
 *  Copyright (C) 2019 Make.org
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

import org.make.api.operation._
import org.make.api.post.PostServiceComponent
import org.make.api.question.QuestionOfOperationResponse
import org.make.api.user.UserServiceComponent
import org.make.api.views.HomePageViewResponse.PostResponse
import org.make.core.Order
import org.make.core.operation._
import org.make.core.operation.indexed.OperationOfQuestionElasticsearchFieldName
import org.make.core.post.indexed.{
  DisplayHomeSearchFilter,
  PostCountryFilter,
  PostElasticsearchFieldNames,
  PostSearchFilters,
  PostSearchQuery
}
import org.make.core.reference.Country
import org.make.core.user.UserType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait HomeViewService {
  def getHomePageViewResponse(country: Country): Future[HomePageViewResponse]
}

trait HomeViewServiceComponent {
  def homeViewService: HomeViewService
}

trait DefaultHomeViewServiceComponent extends HomeViewServiceComponent {
  this: OperationOfQuestionSearchEngineComponent with UserServiceComponent with PostServiceComponent =>

  override lazy val homeViewService: HomeViewService = new DefaultHomeViewService

  class DefaultHomeViewService extends HomeViewService {

    override def getHomePageViewResponse(country: Country): Future[HomePageViewResponse] = {

      def searchQuestionOfOperations(query: OperationOfQuestionSearchQuery): Future[Seq[QuestionOfOperationResponse]] =
        elasticsearchOperationOfQuestionAPI
          .searchOperationOfQuestions(
            query.copy(filters = query.filters.map(_.copy(country = Some(CountrySearchFilter(country)))))
          )
          .map(_.results.map(QuestionOfOperationResponse.apply))

      val futurePartnersCount = userService.adminCountUsers(
        email = None,
        firstName = None,
        lastName = None,
        role = None,
        userType = Some(UserType.UserTypeOrganisation)
      )

      val futureOtherCounts = elasticsearchOperationOfQuestionAPI.highlights()

      val futureCurrentQuestions = searchQuestionOfOperations(
        OperationOfQuestionSearchQuery(
          filters =
            Some(OperationOfQuestionSearchFilters(status = Some(StatusSearchFilter(OperationOfQuestion.Status.Open)))),
          limit = Some(4),
          sortAlgorithm = Some(SortAlgorithm.Featured)
        )
      )

      val futurePastQuestions = searchQuestionOfOperations(
        OperationOfQuestionSearchQuery(
          filters = Some(
            OperationOfQuestionSearchFilters(
              status = Some(StatusSearchFilter(OperationOfQuestion.Status.Finished)),
              hasResults = Some(HasResultsSearchFilter)
            )
          ),
          limit = Some(4),
          sort = Some(OperationOfQuestionElasticsearchFieldName.endDate),
          order = Some(Order.desc)
        )
      )

      val futureFeaturedQuestions = searchQuestionOfOperations(
        OperationOfQuestionSearchQuery(
          filters = Some(OperationOfQuestionSearchFilters(featured = Some(FeaturedSearchFilter(true)))),
          sortAlgorithm = Some(SortAlgorithm.Chronological)
        )
      )

      val futurePosts: Future[Seq[PostResponse]] = postService
        .search(
          PostSearchQuery(
            filters = Some(
              PostSearchFilters(
                displayHome = Some(DisplayHomeSearchFilter(true)),
                country = Some(PostCountryFilter(country))
              )
            ),
            sort = Some(PostElasticsearchFieldNames.postDate),
            order = Some(Order.desc),
            limit = Some(3)
          )
        )
        .map(_.results.map(HomePageViewResponse.PostResponse.fromIndexedPost))

      for {
        partnersCount     <- futurePartnersCount
        otherCounts       <- futureOtherCounts
        currentQuestions  <- futureCurrentQuestions
        pastQuestions     <- futurePastQuestions
        featuredQuestions <- futureFeaturedQuestions
        posts             <- futurePosts
      } yield {
        HomePageViewResponse(
          highlights = otherCounts.copy(partnersCount = partnersCount),
          currentQuestions = currentQuestions,
          pastQuestions = pastQuestions,
          featuredQuestions = featuredQuestions,
          posts = posts
        )
      }
    }
  }
}
