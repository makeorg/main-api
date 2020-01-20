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

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.make.api.ActorSystemComponent
import org.make.api.idea.{PersistentTopIdeaServiceComponent, TopIdeaServiceComponent}
import org.make.api.organisation.OrganisationSearchEngineComponent
import org.make.api.personality.QuestionPersonalityServiceComponent
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.technical.{IdGeneratorComponent, MakeRandom}
import org.make.api.user.UserServiceComponent
import org.make.core.idea.{TopIdea, TopIdeaId}
import org.make.core.operation.OperationId
import org.make.core.personality.PersonalityRole
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.user._
import org.make.core.user.indexed.OrganisationSearchResult
import org.make.core.{RequestContext, ValidationError, ValidationFailedError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait QuestionService {
  def getQuestion(questionId: QuestionId): Future[Option[Question]]
  def getQuestions(questionIds: Seq[QuestionId]): Future[Seq[Question]]
  def getQuestionByQuestionIdValueOrSlug(questionIdValueOrSlug: String): Future[Option[Question]]
  def findQuestion(maybeThemeId: Option[ThemeId],
                   maybeOperationId: Option[OperationId],
                   country: Country,
                   language: Language): Future[Option[Question]]
  def searchQuestion(request: SearchQuestionRequest): Future[Seq[Question]]
  def countQuestion(request: SearchQuestionRequest): Future[Int]
  def findQuestionByQuestionIdOrThemeOrOperation(maybeQuestionId: Option[QuestionId],
                                                 maybeThemeId: Option[ThemeId],
                                                 maybeOperationId: Option[OperationId],
                                                 country: Country,
                                                 language: Language): Future[Option[Question]]
  def createQuestion(country: Country, language: Language, question: String, slug: String): Future[Question]
  def getQuestionPersonalities(start: Int,
                               end: Option[Int],
                               questionId: QuestionId,
                               personalityRole: Option[PersonalityRole]): Future[Seq[QuestionPersonalityResponse]]
  def getPartners(questionId: QuestionId,
                  organisationIds: Seq[UserId],
                  sortAlgorithm: Option[OrganisationSortAlgorithm],
                  limit: Option[Int],
                  skip: Option[Int]): Future[OrganisationSearchResult]
  def getTopIdeas(start: Int,
                  end: Option[Int],
                  seed: Option[Int],
                  questionId: QuestionId,
                  requestContext: RequestContext): Future[QuestionTopIdeasResponseWithSeed]
  def getTopIdea(topIdeaId: TopIdeaId, questionId: QuestionId): Future[Option[QuestionTopIdeaResponse]]
}

case class SearchQuestionRequest(maybeQuestionIds: Option[Seq[QuestionId]] = None,
                                 maybeThemeId: Option[ThemeId] = None,
                                 maybeOperationIds: Option[Seq[OperationId]] = None,
                                 country: Option[Country] = None,
                                 language: Option[Language] = None,
                                 maybeSlug: Option[String] = None,
                                 skip: Option[Int] = None,
                                 limit: Option[Int] = None,
                                 sort: Option[String] = None,
                                 order: Option[String] = None)

trait QuestionServiceComponent {
  def questionService: QuestionService
}

trait DefaultQuestionService extends QuestionServiceComponent {
  this: PersistentQuestionServiceComponent
    with ActorSystemComponent
    with IdGeneratorComponent
    with QuestionPersonalityServiceComponent
    with UserServiceComponent
    with OrganisationSearchEngineComponent
    with TopIdeaServiceComponent
    with ProposalSearchEngineComponent
    with PersistentTopIdeaServiceComponent =>

  override lazy val questionService: QuestionService = new DefaultQuestionService

  class DefaultQuestionService extends QuestionService {

    override def getQuestions(questionIds: Seq[QuestionId]): Future[Seq[Question]] = {
      persistentQuestionService.getByIds(questionIds)
    }

    override def getQuestionByQuestionIdValueOrSlug(questionIdValueOrSlug: String): Future[Option[Question]] = {
      persistentQuestionService.getByQuestionIdValueOrSlug(questionIdValueOrSlug)
    }

    override def getQuestion(questionId: QuestionId): Future[Option[Question]] = {
      persistentQuestionService.getById(questionId)
    }

    override def findQuestion(maybeThemeId: Option[ThemeId],
                              maybeOperationId: Option[OperationId],
                              country: Country,
                              language: Language): Future[Option[Question]] = {

      (maybeOperationId, maybeThemeId) match {
        case (None, None) =>
          Future.failed(
            ValidationFailedError(
              Seq(
                ValidationError(
                  "unknown",
                  "mandatory",
                  Some("operationId or themeId must be provided to question search")
                )
              )
            )
          )
        case (Some(_), Some(_)) =>
          Future.failed(
            ValidationFailedError(
              Seq(
                ValidationError(
                  "unknown",
                  "mandatory",
                  Some("You must provide only one of themeId or operationId to question search")
                )
              )
            )
          )
        case _ =>
          persistentQuestionService
            .find(
              SearchQuestionRequest(
                country = Some(country),
                language = Some(language),
                maybeOperationIds = maybeOperationId.map(operationId => Seq(operationId)),
                maybeThemeId = maybeThemeId
              )
            )
            .map(_.headOption)
      }

    }
    override def findQuestionByQuestionIdOrThemeOrOperation(maybeQuestionId: Option[QuestionId],
                                                            maybeThemeId: Option[ThemeId],
                                                            maybeOperationId: Option[OperationId],
                                                            country: Country,
                                                            language: Language): Future[Option[Question]] = {

      // If all ids are None, return None
      maybeQuestionId
        .orElse(maybeOperationId)
        .orElse(maybeThemeId)
        .map { _ =>
          maybeQuestionId.map(getQuestion).getOrElse(findQuestion(maybeThemeId, maybeOperationId, country, language))
        }
        .getOrElse(Future.successful(None))
    }

    override def searchQuestion(request: SearchQuestionRequest): Future[Seq[Question]] = {
      persistentQuestionService.find(request)
    }

    override def countQuestion(request: SearchQuestionRequest): Future[Int] = {
      persistentQuestionService.count(request)
    }

    override def createQuestion(country: Country,
                                language: Language,
                                question: String,
                                slug: String): Future[Question] = {
      persistentQuestionService.persist(
        Question(
          questionId = idGenerator.nextQuestionId(),
          slug = slug,
          country = country,
          language = language,
          question = question,
          operationId = None,
          themeId = None
        )
      )
    }

    implicit private val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

    override def getQuestionPersonalities(
      start: Int,
      end: Option[Int],
      questionId: QuestionId,
      personalityRole: Option[PersonalityRole]
    ): Future[Seq[QuestionPersonalityResponse]] = {
      Source
        .fromFuture(
          questionPersonalityService.find(
            start = start,
            end = end,
            sort = None,
            order = None,
            userId = None,
            questionId = Some(questionId),
            personalityRole = personalityRole
          )
        )
        .mapConcat(identity)
        .mapAsync(1) { personality =>
          userService.getPersonality(personality.userId)
        }
        .collect {
          case Some(user) =>
            QuestionPersonalityResponse(
              userId = user.userId,
              firstName = user.firstName,
              lastName = user.lastName,
              politicalParty = user.profile.flatMap(_.politicalParty),
              avatarUrl = user.profile.flatMap(_.avatarUrl),
              gender = user.profile.flatMap(_.gender.map(_.shortName))
            )
        }
        .runWith(Sink.seq[QuestionPersonalityResponse])
    }

    override def getPartners(questionId: QuestionId,
                             organisationIds: Seq[UserId],
                             sortAlgorithm: Option[OrganisationSortAlgorithm],
                             limit: Option[Int],
                             skip: Option[Int]): Future[OrganisationSearchResult] = {
      val query = OrganisationSearchQuery(
        filters = Some(OrganisationSearchFilters(organisationIds = Some(OrganisationIdsSearchFilter(organisationIds)))),
        sortAlgorithm = sortAlgorithm,
        limit = sortAlgorithm.map(_ => 1000).orElse(limit),
        skip = sortAlgorithm.map(_  => 0).orElse(skip)
      )

      elasticsearchOrganisationAPI.searchOrganisations(query).map { organisationSearchResult =>
        sortAlgorithm.collect {
          case ParticipationAlgorithm(_) =>
            organisationSearchResult.copy(results = organisationSearchResult.results.sortBy { orga =>
              orga.countsByQuestion.collect {
                case counts if counts.questionId == questionId =>
                  counts.proposalsCount + counts.votesCount
              }.sum * -1
            }.slice(skip.getOrElse(0), skip.getOrElse(0) + limit.getOrElse(organisationSearchResult.total.toInt)))
        }.getOrElse(organisationSearchResult)
      }

    }

    override def getTopIdeas(start: Int,
                             end: Option[Int],
                             seed: Option[Int],
                             questionId: QuestionId,
                             requestContext: RequestContext): Future[QuestionTopIdeasResponseWithSeed] = {

      val randomSeed = seed.getOrElse(MakeRandom.random.nextInt())

      topIdeaService
        .search(start = start, end = end, ideaId = None, questionId = Some(questionId), name = None)
        .flatMap { topIdeas =>
          val emptyResult = topIdeas.map { topIdea =>
            QuestionTopIdeaWithAvatarResponse(
              id = topIdea.topIdeaId,
              ideaId = topIdea.ideaId,
              questionId = topIdea.questionId,
              name = topIdea.name,
              scores = topIdea.scores,
              proposalsCount = 0,
              avatars = Seq.empty,
              weight = topIdea.weight
            )
          }
          elasticsearchProposalAPI
            .getRandomProposalsByIdeaWithAvatar(ideaIds = topIdeas.map(_.ideaId), randomSeed)
            .map {
              case avatarsAndProposalsCountByIdea if avatarsAndProposalsCountByIdea.isEmpty => emptyResult
              case avatarsAndProposalsCountByIdea =>
                avatarsAndProposalsCountByIdea.toSeq.flatMap {
                  case (ideaId, avatarsAndProposalsCount) =>
                    topIdeas.find(_.ideaId.value == ideaId.value).map { topIdea =>
                      QuestionTopIdeaWithAvatarResponse(
                        id = topIdea.topIdeaId,
                        ideaId = topIdea.ideaId,
                        questionId = topIdea.questionId,
                        name = topIdea.name,
                        scores = topIdea.scores,
                        proposalsCount = avatarsAndProposalsCount.proposalsCount,
                        avatars = avatarsAndProposalsCount.avatars,
                        weight = topIdea.weight
                      )
                    }
                }
            }
        }
        .map(result => QuestionTopIdeasResponseWithSeed(result, randomSeed))
    }

    def getTopIdea(topIdeaId: TopIdeaId, questionId: QuestionId): Future[Option[QuestionTopIdeaResponse]] = {
      persistentTopIdeaService.getByIdAndQuestionId(topIdeaId, questionId).map { maybeTopIdea =>
        maybeTopIdea.map { topIdea =>
          QuestionTopIdeaResponse(
            id = topIdeaId,
            ideaId = topIdea.ideaId,
            questionId = questionId,
            name = topIdea.name,
            scores = topIdea.scores
          )
        }
      }
    }
  }
}

final case class AvatarsAndProposalsCount(avatars: Seq[String], proposalsCount: Int)

final case class AvatarsTopIdeaAndProposalsCount(avatars: Seq[String], topIdea: TopIdea, proposalsCount: Int)
