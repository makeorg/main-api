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

import akka.actor.ActorSystem
import org.make.api.{ActorSystemComponent, MakeUnitTest, TestUtils}
import org.make.api.technical.IdGeneratorComponent
import org.make.core.idea.CommentVoteKey
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.core.idea.{TopIdeaComment, TopIdeaCommentId, TopIdeaId}
import org.make.core.technical.IdGenerator
import org.make.core.user.{UserId, UserType}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.make.core.technical.Pagination.Start

class TopIdeaCommentServiceTest
    extends MakeUnitTest
    with DefaultTopIdeaCommentServiceComponent
    with ActorSystemComponent
    with IdGeneratorComponent
    with PersistentTopIdeaCommentServiceComponent
    with UserServiceComponent {

  override val actorSystem: ActorSystem = ActorSystem()
  override val persistentTopIdeaCommentService: PersistentTopIdeaCommentService = mock[PersistentTopIdeaCommentService]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val userService: UserService = mock[UserService]

  when(idGenerator.nextTopIdeaCommentId()).thenReturn(TopIdeaCommentId("comment-id"))

  Feature("create and get") {
    val comment =
      TopIdeaComment(
        TopIdeaCommentId("comment-id"),
        TopIdeaId("top-idea-id"),
        UserId("personality-id"),
        Some("comment 1"),
        None,
        None,
        CommentVoteKey.Agree,
        None
      )

    Scenario("create an idea") {
      topIdeaCommentService.create(
        comment.topIdeaId,
        comment.personalityId,
        comment.comment1,
        comment.comment2,
        comment.comment3,
        comment.vote,
        comment.qualification
      )
      verify(persistentTopIdeaCommentService)
        .persist(eqTo(comment))
    }

    Scenario("get an idea") {
      topIdeaCommentService.get(comment.topIdeaCommentId)
      verify(persistentTopIdeaCommentService)
        .getById(eqTo(comment.topIdeaCommentId))
    }
  }

  Feature("search") {
    Scenario("search all  top idea comment") {
      val start = Start(42)
      val end = None
      val topIdeaIds = None
      val personalityIds = Some(Seq(UserId("some-user"), UserId("another-user")))
      topIdeaCommentService.search(start, end, topIdeaIds, personalityIds)
      verify(persistentTopIdeaCommentService).search(start, end, topIdeaIds, personalityIds)
    }
  }

  Feature("get comments with personality") {
    Scenario("get comments with personality") {
      when(
        persistentTopIdeaCommentService
          .search(
            start = Start.zero,
            end = None,
            topIdeaIds = Some(Seq(TopIdeaId("top-idea-id"))),
            personalityIds = None
          )
      ).thenReturn(
        Future.successful(
          Seq(
            TopIdeaComment(
              topIdeaCommentId = TopIdeaCommentId("top-idea-comment-id-1"),
              topIdeaId = TopIdeaId("top-idea-id"),
              personalityId = UserId("personality-1"),
              comment1 = Some("comment1"),
              comment2 = None,
              comment3 = None,
              vote = CommentVoteKey.Agree,
              qualification = None
            ),
            TopIdeaComment(
              topIdeaCommentId = TopIdeaCommentId("top-idea-comment-id-2"),
              topIdeaId = TopIdeaId("top-idea-id"),
              personalityId = UserId("personality-2"),
              comment1 = Some("comment1"),
              comment2 = Some("comment2"),
              comment3 = None,
              vote = CommentVoteKey.Disagree,
              qualification = None
            )
          )
        )
      )

      when(userService.getPersonality(UserId("personality-1"))).thenReturn(
        Future.successful(Some(TestUtils.user(id = UserId("personality-1"), userType = UserType.UserTypePersonality)))
      )

      when(userService.getPersonality(UserId("personality-2"))).thenReturn(
        Future.successful(Some(TestUtils.user(id = UserId("personality-2"), userType = UserType.UserTypePersonality)))
      )

      whenReady(
        topIdeaCommentService.getCommentsWithPersonality(topIdeaIds = Seq(TopIdeaId("top-idea-id"))),
        Timeout(3.seconds)
      ) { result =>
        result.size should be(2)
        result.map(_.id) should contain(TopIdeaCommentId("top-idea-comment-id-1"))
        result.map(_.personality.personalityId) should contain(UserId("personality-1"))
      }
    }
  }

  Feature("count for all ideas") {
    Scenario("empty list") {
      topIdeaCommentService.countForAll(Seq.empty)
      verify(persistentTopIdeaCommentService).countForAll(Seq.empty)
    }

    Scenario("full list") {
      topIdeaCommentService.countForAll(Seq(TopIdeaId("top-idea-id-1"), TopIdeaId("top-idea-id-2")))
      verify(persistentTopIdeaCommentService)
        .countForAll(Seq(TopIdeaId("top-idea-id-1"), TopIdeaId("top-idea-id-2")))
    }
  }
}
