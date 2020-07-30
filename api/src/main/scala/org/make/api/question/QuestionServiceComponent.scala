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

import akka.stream.scaladsl.{Sink, Source}
import cats.data.NonEmptyList
import org.make.api.ActorSystemComponent
import org.make.api.idea.topIdeaComments.PersistentTopIdeaCommentServiceComponent
import org.make.api.idea.{PersistentTopIdeaServiceComponent, TopIdeaServiceComponent}
import org.make.api.organisation.OrganisationSearchEngineComponent
import org.make.api.personality.QuestionPersonalityServiceComponent
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.technical.{IdGeneratorComponent, MakeRandom}
import org.make.api.user.UserServiceComponent
import org.make.core.idea.{TopIdea, TopIdeaId}
import org.make.core.operation.OperationId
import org.make.core.personality.PersonalityRoleId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user._
import org.make.core.user.indexed.OrganisationSearchResult
import org.make.core.{ValidationError, ValidationFailedError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait QuestionService {
  def getQuestion(questionId: QuestionId): Future[Option[Question]]
  def getQuestions(questionIds: Seq[QuestionId]): Future[Seq[Question]]
  def getQuestionByQuestionIdValueOrSlug(questionIdValueOrSlug: String): Future[Option[Question]]
  def findQuestion(
    maybeOperationId: Option[OperationId],
    country: Country,
    language: Language
  ): Future[Option[Question]]
  def searchQuestion(request: SearchQuestionRequest): Future[Seq[Question]]
  def countQuestion(request: SearchQuestionRequest): Future[Int]
  def createQuestion(
    countries: NonEmptyList[Country],
    language: Language,
    question: String,
    shortTitle: Option[String],
    slug: String
  ): Future[Question]
  def getQuestionPersonalities(
    start: Int,
    end: Option[Int],
    questionId: QuestionId,
    personalityRoleId: Option[PersonalityRoleId]
  ): Future[Seq[QuestionPersonalityResponse]]
  def getPartners(
    questionId: QuestionId,
    organisationIds: Seq[UserId],
    sortAlgorithm: Option[OrganisationSortAlgorithm],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[OrganisationSearchResult]
  def getTopIdeas(
    start: Int,
    end: Option[Int],
    seed: Option[Int],
    questionId: QuestionId
  ): Future[QuestionTopIdeasResponseWithSeed]
  def getTopIdea(
    topIdeaId: TopIdeaId,
    questionId: QuestionId,
    seed: Option[Int]
  ): Future[Option[QuestionTopIdeaResultWithSeed]]
}

case class SearchQuestionRequest(
  maybeQuestionIds: Option[Seq[QuestionId]] = None,
  maybeOperationIds: Option[Seq[OperationId]] = None,
  country: Option[Country] = None,
  language: Option[Language] = None,
  maybeSlug: Option[String] = None,
  skip: Option[Int] = None,
  limit: Option[Int] = None,
  sort: Option[String] = None,
  order: Option[String] = None
)

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
    with PersistentTopIdeaServiceComponent
    with PersistentTopIdeaCommentServiceComponent =>

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

    override def findQuestion(
      maybeOperationId: Option[OperationId],
      country: Country,
      language: Language
    ): Future[Option[Question]] = {

      maybeOperationId match {
        case None =>
          Future.failed(
            ValidationFailedError(
              Seq(ValidationError("unknown", "mandatory", Some("operationId must be provided to question search")))
            )
          )
        case _ =>
          persistentQuestionService
            .find(
              SearchQuestionRequest(
                country = Some(country),
                language = Some(language),
                maybeOperationIds = maybeOperationId.map(operationId => Seq(operationId))
              )
            )
            .map(_.headOption)
      }

    }

    override def searchQuestion(request: SearchQuestionRequest): Future[Seq[Question]] = {
      persistentQuestionService.find(request)
    }

    override def countQuestion(request: SearchQuestionRequest): Future[Int] = {
      persistentQuestionService.count(request)
    }

    override def createQuestion(
      countries: NonEmptyList[Country],
      language: Language,
      question: String,
      shortTitle: Option[String],
      slug: String
    ): Future[Question] = {
      persistentQuestionService.persist(
        Question(
          questionId = idGenerator.nextQuestionId(),
          slug = slug,
          countries = countries,
          language = language,
          question = question,
          shortTitle = shortTitle,
          operationId = None
        )
      )
    }

    override def getQuestionPersonalities(
      start: Int,
      end: Option[Int],
      questionId: QuestionId,
      personalityRoleId: Option[PersonalityRoleId]
    ): Future[Seq[QuestionPersonalityResponse]] = {
      Source
        .future(
          questionPersonalityService.find(
            start = start,
            end = end,
            sort = None,
            order = None,
            userId = None,
            questionId = Some(questionId),
            personalityRoleId = personalityRoleId
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
              gender = user.profile.flatMap(_.gender.map(_.value))
            )
        }
        .runWith(Sink.seq[QuestionPersonalityResponse])
    }

    override def getPartners(
      questionId: QuestionId,
      organisationIds: Seq[UserId],
      sortAlgorithm: Option[OrganisationSortAlgorithm],
      limit: Option[Int],
      skip: Option[Int]
    ): Future[OrganisationSearchResult] = {
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

    override def getTopIdeas(
      start: Int,
      end: Option[Int],
      seed: Option[Int],
      questionId: QuestionId
    ): Future[QuestionTopIdeasResponseWithSeed] = {

      val randomSeed = seed.getOrElse(MakeRandom.nextInt())

      topIdeaService
        .search(start = start, end = end, ideaId = None, questionIds = Some(Seq(questionId)), name = None)
        .flatMap { topIdeas =>
          persistentTopIdeaCommentService.countForAll(topIdeas.map(_.topIdeaId)).flatMap { commentByTopIdea =>
            elasticsearchProposalAPI
              .getRandomProposalsByIdeaWithAvatar(ideaIds = topIdeas.map(_.ideaId), randomSeed)
              .map { avatarsAndProposalsCountByIdea =>
                topIdeas.map { topIdea =>
                  val ideaAvatarsCount = avatarsAndProposalsCountByIdea
                    .getOrElse(topIdea.ideaId, AvatarsAndProposalsCount(Seq.empty, 0))
                  QuestionTopIdeaWithAvatarResponse(
                    id = topIdea.topIdeaId,
                    ideaId = topIdea.ideaId,
                    questionId = topIdea.questionId,
                    name = topIdea.name,
                    label = topIdea.label,
                    scores = topIdea.scores,
                    proposalsCount = ideaAvatarsCount.proposalsCount,
                    avatars = ideaAvatarsCount.avatars,
                    weight = topIdea.weight,
                    commentsCount = commentByTopIdea.getOrElse(topIdea.topIdeaId.value, 0)
                  )
                }
              }
          }
        }
        .map(result => QuestionTopIdeasResponseWithSeed(result, randomSeed))
    }

    def getTopIdea(
      topIdeaId: TopIdeaId,
      questionId: QuestionId,
      seed: Option[Int]
    ): Future[Option[QuestionTopIdeaResultWithSeed]] = {
      val randomSeed = seed.getOrElse(MakeRandom.nextInt())

      persistentTopIdeaService.getByIdAndQuestionId(topIdeaId, questionId).flatMap {
        case None => Future.successful(None)
        case Some(topIdea) =>
          elasticsearchProposalAPI
            .getRandomProposalsByIdeaWithAvatar(ideaIds = Seq(topIdea.ideaId), randomSeed)
            .map { result =>
              val ideaAvatarsCount = result.getOrElse(topIdea.ideaId, AvatarsAndProposalsCount(Seq.empty, 0))
              Some(
                QuestionTopIdeaResultWithSeed(
                  topIdea = topIdea,
                  proposalsCount = ideaAvatarsCount.proposalsCount,
                  avatars = ideaAvatarsCount.avatars,
                  seed = randomSeed
                )
              )
            }
      }
    }
  }
}

final case class AvatarsAndProposalsCount(avatars: Seq[String], proposalsCount: Int)

final case class QuestionTopIdeaResultWithSeed(topIdea: TopIdea, avatars: Seq[String], proposalsCount: Int, seed: Int)
