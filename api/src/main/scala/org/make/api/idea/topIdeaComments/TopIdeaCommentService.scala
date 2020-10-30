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

package org.make.api.idea.topIdeaComments

import akka.stream.scaladsl.{Sink, Source}
import org.make.api.ActorSystemComponent
import org.make.api.question.{QuestionTopIdeaCommentsPersonalityResponse, QuestionTopIdeaCommentsResponse}
import org.make.api.technical.IdGeneratorComponent
import org.make.core.idea.{CommentQualificationKey, CommentVoteKey, TopIdeaComment, TopIdeaCommentId, TopIdeaId}
import org.make.api.user.UserServiceComponent
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait TopIdeaCommentService {
  def get(topIdeaCommentId: TopIdeaCommentId): Future[Option[TopIdeaComment]]
  def create(
    topIdeaId: TopIdeaId,
    personalityId: UserId,
    comment1: Option[String],
    comment2: Option[String],
    comment3: Option[String],
    vote: CommentVoteKey,
    qualification: Option[CommentQualificationKey]
  ): Future[TopIdeaComment]
  def search(
    start: Start,
    end: Option[End],
    topIdeaIds: Option[Seq[TopIdeaId]],
    personalityIds: Option[Seq[UserId]]
  ): Future[Seq[TopIdeaComment]]
  def getCommentsWithPersonality(topIdeaIds: Seq[TopIdeaId]): Future[Seq[QuestionTopIdeaCommentsResponse]]
  def countForAll(topIdeaIds: Seq[TopIdeaId]): Future[Map[String, Int]]
}

trait TopIdeaCommentServiceComponent {
  def topIdeaCommentService: TopIdeaCommentService
}

trait DefaultTopIdeaCommentServiceComponent extends TopIdeaCommentServiceComponent {
  this: PersistentTopIdeaCommentServiceComponent
    with ActorSystemComponent
    with IdGeneratorComponent
    with UserServiceComponent =>

  override val topIdeaCommentService: DefaultTopIdeaCommentService = new DefaultTopIdeaCommentService

  class DefaultTopIdeaCommentService extends TopIdeaCommentService {

    override def get(topIdeaCommentId: TopIdeaCommentId): Future[Option[TopIdeaComment]] =
      persistentTopIdeaCommentService.getById(topIdeaCommentId)

    override def create(
      topIdeaId: TopIdeaId,
      personalityId: UserId,
      comment1: Option[String],
      comment2: Option[String],
      comment3: Option[String],
      vote: CommentVoteKey,
      qualification: Option[CommentQualificationKey]
    ): Future[TopIdeaComment] = {
      persistentTopIdeaCommentService.persist(
        TopIdeaComment(
          topIdeaCommentId = idGenerator.nextTopIdeaCommentId(),
          topIdeaId = topIdeaId,
          personalityId = personalityId,
          comment1 = comment1,
          comment2 = comment2,
          comment3 = comment3,
          vote = vote,
          qualification = qualification
        )
      )
    }

    override def search(
      start: Start,
      end: Option[End],
      topIdeaIds: Option[Seq[TopIdeaId]],
      personalityIds: Option[Seq[UserId]]
    ): Future[Seq[TopIdeaComment]] = {
      persistentTopIdeaCommentService.search(start, end, topIdeaIds, personalityIds)
    }

    override def getCommentsWithPersonality(
      topIdeaIds: Seq[TopIdeaId]
    ): Future[Seq[QuestionTopIdeaCommentsResponse]] = {
      Source
        .future(
          persistentTopIdeaCommentService
            .search(start = Start.zero, end = None, topIdeaIds = Some(topIdeaIds), personalityIds = None)
        )
        .mapConcat(identity)
        .mapAsync(1) { topIdeaComment =>
          userService.getPersonality(topIdeaComment.personalityId).map((topIdeaComment, _))
        }
        .collect {
          case (topIdeaComment, Some(personality)) => (topIdeaComment, personality)
        }
        .map {
          case (topIdeaComment, personality) =>
            QuestionTopIdeaCommentsResponse(
              id = topIdeaComment.topIdeaCommentId,
              personality = QuestionTopIdeaCommentsPersonalityResponse(
                personalityId = personality.userId,
                displayName = personality.displayName,
                avatarUrl = personality.profile.flatMap(_.avatarUrl),
                politicalParty = personality.profile.flatMap(_.politicalParty)
              ),
              comment1 = topIdeaComment.comment1,
              comment2 = topIdeaComment.comment2,
              comment3 = topIdeaComment.comment3,
              vote = topIdeaComment.vote,
              qualification = topIdeaComment.qualification
            )
        }
        .runWith(Sink.seq[QuestionTopIdeaCommentsResponse])
    }

    override def countForAll(topIdeaIds: Seq[TopIdeaId]): Future[Map[String, Int]] =
      persistentTopIdeaCommentService.countForAll(topIdeaIds)
  }
}
