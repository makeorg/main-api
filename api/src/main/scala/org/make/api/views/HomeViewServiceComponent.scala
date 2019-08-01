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

import java.time.ZonedDateTime

import org.make.api.operation._
import org.make.api.proposal.{ProposalSearchEngineComponent, ProposalServiceComponent, ProposalsResultSeededResponse}
import org.make.api.question.{QuestionServiceComponent, SearchQuestionRequest}
import org.make.core.RequestContext
import org.make.core.idea.{CountrySearchFilter, LanguageSearchFilter}
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.operation._
import org.make.core.proposal._
import org.make.core.question.Question
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class QuestionWithDetail(question: Question,
                              operationOfQuestion: IndexedOperationOfQuestion,
                              kind: OperationKind,
                              proposalCount: Long)
case class HomeViewData(popularProposals: Seq[Proposal],
                        controverseProposals: Seq[Proposal],
                        currentQuestions: Seq[QuestionWithDetail],
                        businessQuestions: Seq[QuestionWithDetail],
                        featuredQuestions: Seq[QuestionWithDetail])

trait HomeViewService {
  def getHomeViewResponse(language: Language,
                          country: Country,
                          userId: Option[UserId],
                          requestContext: RequestContext): Future[HomeViewResponse]
}

trait HomeViewServiceComponent {
  def homeViewService: HomeViewService
}

trait DefaultHomeViewServiceComponent extends HomeViewServiceComponent {
  this: QuestionServiceComponent
    with OperationOfQuestionServiceComponent
    with OperationServiceComponent
    with ProposalServiceComponent
    with ProposalSearchEngineComponent
    with CurrentOperationServiceComponent
    with FeaturedOperationServiceComponent =>

  override lazy val homeViewService: HomeViewService = new DefaultHomeViewService

  class DefaultHomeViewService extends HomeViewService {

    val publicKinds: Seq[OperationKind] = Seq(OperationKind.GreatCause, OperationKind.PublicConsultation)
    val businessKinds: Seq[OperationKind] = Seq(OperationKind.BusinessConsultation)

    override def getHomeViewResponse(language: Language,
                                     country: Country,
                                     userId: Option[UserId],
                                     requestContext: RequestContext): Future[HomeViewResponse] = {

      val futureAllQuestionWithDetails: Future[Seq[QuestionWithDetail]] = getAllQuestionWithDetails(language, country)
      val futurePublicStartedConsultations: Future[Seq[QuestionWithDetail]] = futureAllQuestionWithDetails
        .map(_.filter { questionWithDetail =>
          publicKinds.contains(questionWithDetail.kind) && questionWithDetail.operationOfQuestion.startDate
            .forall(_.isBefore(ZonedDateTime.now()))
        })

      for {
        allQuestionsWithDetails <- futureAllQuestionWithDetails
        publicStartedQuestions  <- futurePublicStartedConsultations
        popularProposals <- getProposals(
          questionDetails = publicStartedQuestions,
          userId = userId,
          language = language,
          country = country,
          sortAlgorithm = PopularAlgorithm,
          requestContext = requestContext
        )
        controversialProposals <- getProposals(
          questionDetails = publicStartedQuestions,
          userId = userId,
          language = language,
          country = country,
          sortAlgorithm = ControversyAlgorithm,
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
              allQuestionsAndDetails.find(_.operationOfQuestion.questionId == questionId).map(_.question.slug)
            })
        )
    }

    private def getAllQuestionWithDetails(language: Language, country: Country): Future[Seq[QuestionWithDetail]] = {
      for {
        operations <- operationService.findSimple()
        questions <- questionService.searchQuestion(
          SearchQuestionRequest(
            language = Some(language),
            country = Some(country),
            maybeOperationIds = Some(operations.map(_.operationId))
          )
        )
        operationOfQuestions <- operationOfQuestionService.search(
          searchQuery = OperationOfQuestionSearchQuery(
            filters = Some(
              OperationOfQuestionSearchFilters(questionIds = Some(QuestionIdsSearchFilter(questions.map(_.questionId))))
            )
          )
        )
        proposalsCount <- elasticsearchProposalAPI.countProposalsByQuestion(Option(questions.map(_.questionId)))
      } yield
        questions.flatMap { question =>
          operationOfQuestions.results.find(_.questionId == question.questionId).flatMap { operationOfQuestion =>
            operations.find(_.operationId == operationOfQuestion.operationId).map { ope =>
              QuestionWithDetail(
                question,
                operationOfQuestion,
                ope.operationKind,
                proposalsCount.getOrElse(question.questionId, 0)
              )
            }
          }
        }
    }

    private def getProposals(questionDetails: Seq[QuestionWithDetail],
                             userId: Option[UserId],
                             language: Language,
                             country: Country,
                             sortAlgorithm: SortAlgorithm,
                             requestContext: RequestContext): Future[ProposalsResultSeededResponse] = {

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
