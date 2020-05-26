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
import org.make.api.proposal.{
  ProposalSearchEngineComponent,
  ProposalServiceComponent,
  ProposalsResultSeededResponse,
  SortAlgorithmConfigurationComponent
}
import org.make.api.question.{QuestionOfOperationResponse, QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.user.UserServiceComponent
import org.make.api.views.HomePageViewResponse.Highlights
import org.make.core.idea.{CountrySearchFilter, LanguageSearchFilter}
import org.make.core.operation._
import org.make.core.operation.{StatusSearchFilter => OOQStatusSearchFilter}
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.proposal._
import org.make.core.proposal.SortAlgorithm
import org.make.core.question.Question
import org.make.core.reference.{Country, Language}
import org.make.core.user.{UserId, UserType}
import org.make.core.{operation, DateHelper, RequestContext}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class QuestionWithDetail(
  question: Question,
  operationOfQuestion: IndexedOperationOfQuestion,
  kind: OperationKind,
  proposalCount: Long
) {
  def isOpenedConsultation: Boolean = {
    val now = DateHelper.now()
    (operationOfQuestion.startDate, operationOfQuestion.endDate) match {
      case (None, None)                     => true
      case (Some(startDate), None)          => startDate.isBefore(now)
      case (None, Some(endDate))            => endDate.isAfter(now)
      case (Some(startDate), Some(endDate)) => startDate.isBefore(now) && endDate.isAfter(now)
    }
  }
}

case class HomeViewData(
  popularProposals: Seq[Proposal],
  controverseProposals: Seq[Proposal],
  currentQuestions: Seq[QuestionWithDetail],
  businessQuestions: Seq[QuestionWithDetail],
  featuredQuestions: Seq[QuestionWithDetail]
)

trait HomeViewService {
  def getHomeViewResponse(
    language: Language,
    country: Country,
    userId: Option[UserId],
    requestContext: RequestContext
  ): Future[HomeViewResponse]

  def getHomePageViewResponse(country: Country, language: Language): Future[HomePageViewResponse]
}

trait HomeViewServiceComponent {
  def homeViewService: HomeViewService
}

trait DefaultHomeViewServiceComponent extends HomeViewServiceComponent {
  this: QuestionServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with OperationOfQuestionServiceComponent
    with OperationServiceComponent
    with ProposalServiceComponent
    with ProposalSearchEngineComponent
    with CurrentOperationServiceComponent
    with FeaturedOperationServiceComponent
    with SortAlgorithmConfigurationComponent
    with UserServiceComponent =>

  override lazy val homeViewService: HomeViewService = new DefaultHomeViewService

  class DefaultHomeViewService extends HomeViewService {

    val publicKinds: Seq[OperationKind] = Seq(OperationKind.GreatCause, OperationKind.PublicConsultation)
    val businessKinds: Seq[OperationKind] = Seq(OperationKind.BusinessConsultation)

    override def getHomeViewResponse(
      language: Language,
      country: Country,
      userId: Option[UserId],
      requestContext: RequestContext
    ): Future[HomeViewResponse] = {

      val futureAllQuestionWithDetails: Future[Seq[QuestionWithDetail]] = getAllQuestionWithDetails(language, country)
      val futurePublicOpenedConsultations: Future[Seq[QuestionWithDetail]] = futureAllQuestionWithDetails
        .map(_.filter { questionWithDetail =>
          publicKinds.contains(questionWithDetail.kind) && questionWithDetail.isOpenedConsultation
        })

      for {
        allQuestionsWithDetails <- futureAllQuestionWithDetails
        publicOpenedQuestions   <- futurePublicOpenedConsultations
        popularProposals <- getProposals(
          questionDetails = publicOpenedQuestions,
          userId = userId,
          language = language,
          country = country,
          sortAlgorithm = PopularAlgorithm(sortAlgorithmConfiguration.popularVoteCountThreshold),
          requestContext = requestContext
        )
        controversialProposals <- getProposals(
          questionDetails = publicOpenedQuestions,
          userId = userId,
          language = language,
          country = country,
          sortAlgorithm = ControversyAlgorithm(
            sortAlgorithmConfiguration.controversyThreshold,
            sortAlgorithmConfiguration.controversyVoteCountThreshold
          ),
          requestContext = requestContext
        )
        currentConsultations  <- currentOperationService.getAll
        featuredConsultations <- featuredOperationService.getAll
      } yield {
        val businessConsultations: Seq[QuestionWithDetail] =
          allQuestionsWithDetails.filter(questionWithDetail => businessKinds.contains(questionWithDetail.kind))
        HomeViewResponse(
          popularProposals = popularProposals.results,
          controverseProposals = controversialProposals.results,
          businessConsultations = getBusinessConsultationResponses(businessConsultations),
          featuredConsultations = getFeaturedConsultationResponses(featuredConsultations, allQuestionsWithDetails),
          currentConsultations = getCurrentConsultationResponses(currentConsultations, allQuestionsWithDetails)
        )
      }
    }

    override def getHomePageViewResponse(country: Country, language: Language): Future[HomePageViewResponse] = {

      def searchQuestionOfOperations(query: OperationOfQuestionSearchQuery): Future[Seq[QuestionOfOperationResponse]] =
        elasticsearchOperationOfQuestionAPI
          .searchOperationOfQuestions(
            query.copy(filters = query.filters.map(
              _.copy(
                language = Some(operation.LanguageSearchFilter(language)),
                country = Some(operation.CountrySearchFilter(country))
              )
            )
            )
          )
          .map(_.results.map(QuestionOfOperationResponse.apply))

      val futurePartnersCount = userService.adminCountUsers(
        email = None,
        firstName = None,
        lastName = None,
        role = None,
        userType = Some(UserType.UserTypeOrganisation)
      )

      val futureCurrentQuestions = searchQuestionOfOperations(
        OperationOfQuestionSearchQuery(
          filters = Some(
            OperationOfQuestionSearchFilters(status = Some(OOQStatusSearchFilter(OperationOfQuestion.Status.Open)))
          ),
          sortAlgorithm = Some(SortAlgorithm.Chronological)
        )
      )

      val futureFeaturedQuestions = searchQuestionOfOperations(
        OperationOfQuestionSearchQuery(
          filters = Some(OperationOfQuestionSearchFilters(featured = Some(FeaturedSearchFilter(true)))),
          sortAlgorithm = Some(SortAlgorithm.Featured)
        )
      )

      for {
        partnersCount     <- futurePartnersCount
        currentQuestions  <- futureCurrentQuestions
        featuredQuestions <- futureFeaturedQuestions
      } yield {
        HomePageViewResponse(
          highlights = Highlights(participantsCount = 0, proposalsCount = 0, partnersCount = partnersCount),
          currentQuestions = currentQuestions,
          featuredQuestions = featuredQuestions,
          articles = Nil
        )
      }
    }

    private def getBusinessConsultationResponses(
      businessConsultations: Seq[QuestionWithDetail]
    ): Seq[BusinessConsultationResponse] = {
      businessConsultations.map { consultation =>
        BusinessConsultationResponse(
          theme = BusinessConsultationThemeResponse(
            consultation.operationOfQuestion.theme.gradientStart,
            consultation.operationOfQuestion.theme.gradientEnd
          ),
          startDate = consultation.operationOfQuestion.startDate,
          endDate = consultation.operationOfQuestion.endDate,
          slug = Some(consultation.question.slug),
          aboutUrl = consultation.operationOfQuestion.aboutUrl,
          question = consultation.question.question
        )
      }
    }

    private def getCurrentConsultationResponses(
      currentConsultations: Seq[CurrentOperation],
      allQuestionsAndDetails: Seq[QuestionWithDetail]
    ): Seq[CurrentConsultationResponse] = {
      currentConsultations.map { current =>
        val maybeQuestionDetail: Option[QuestionWithDetail] =
          allQuestionsAndDetails.find(_.question.questionId == current.questionId)
        CurrentConsultationResponse(
          current = current,
          slug = maybeQuestionDetail.map(_.question.slug),
          startDate = maybeQuestionDetail.flatMap(_.operationOfQuestion.startDate),
          endDate = maybeQuestionDetail.flatMap(_.operationOfQuestion.endDate),
          proposalsNumber = maybeQuestionDetail.map(_.proposalCount).getOrElse(0)
        )
      }
    }

    private def getFeaturedConsultationResponses(
      featuredConsultations: Seq[FeaturedOperation],
      allQuestionsAndDetails: Seq[QuestionWithDetail]
    ): Seq[FeaturedConsultationResponse] = {
      featuredConsultations
        .sortBy(_.slot)
        .map(
          feat =>
            FeaturedConsultationResponse(feat, feat.questionId.flatMap { questionId =>
              allQuestionsAndDetails.find(_.question.questionId == questionId).map(_.question.slug)
            })
        )
    }

    private def getAllQuestionWithDetails(language: Language, country: Country): Future[Seq[QuestionWithDetail]] = {

      val maxLimit: Int = 10000
      for {
        operationOfQuestions <- operationOfQuestionService.search(searchQuery = OperationOfQuestionSearchQuery(
          limit = Some(maxLimit),
          sort = Some("startDate"),
          order = Some("desc"),
          filters = Some(
            OperationOfQuestionSearchFilters(
              language = Option(operation.LanguageSearchFilter(language)),
              country = Some(operation.CountrySearchFilter(country))
            )
          )
        )
        )
        operations <- operationService.findSimple()
        questions <- questionService.searchQuestion(
          SearchQuestionRequest(
            maybeQuestionIds = Some(operationOfQuestions.results.map(_.questionId)),
            limit = Some(operationOfQuestions.results.length)
          )
        )
        proposalsCount <- elasticsearchProposalAPI.countProposalsByQuestion(
          Some(questions.map(_.questionId)),
          Some(ProposalStatus.statusMap.values.toSeq),
          maybeUserId = None
        )
      } yield {
        for {
          question            <- questions
          operationOfQuestion <- operationOfQuestions.results.find(_.questionId == question.questionId)
          ope                 <- operations.find(_.operationId == operationOfQuestion.operationId)
        } yield QuestionWithDetail(
          question,
          operationOfQuestion,
          ope.operationKind,
          proposalsCount.getOrElse(question.questionId, 0)
        )

      }
    }

    private def getProposals(
      questionDetails: Seq[QuestionWithDetail],
      userId: Option[UserId],
      language: Language,
      country: Country,
      sortAlgorithm: SortAlgorithm,
      requestContext: RequestContext
    ): Future[ProposalsResultSeededResponse] = {

      proposalService.searchForUser(
        userId = userId,
        query = SearchQuery(
          limit = Some(2),
          sortAlgorithm = Some(sortAlgorithm),
          filters = Some(
            SearchFilters(
              language = Some(LanguageSearchFilter(language)),
              country = Some(CountrySearchFilter(country)),
              question = Some(QuestionSearchFilter(questionDetails.map(_.question.questionId)))
            )
          )
        ),
        requestContext = requestContext
      )
    }
  }
}
