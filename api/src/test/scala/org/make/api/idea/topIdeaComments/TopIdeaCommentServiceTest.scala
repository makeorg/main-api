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

import org.make.api.MakeUnitTest
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.idea.{CommentVoteKey, TopIdeaComment, TopIdeaCommentId, TopIdeaId}
import org.make.core.user.UserId
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}

class TopIdeaCommentServiceTest
    extends MakeUnitTest
    with DefaultTopIdeaCommentServiceComponent
    with IdGeneratorComponent
    with PersistentTopIdeaCommentServiceComponent {

  override val persistentTopIdeaCommentService: PersistentTopIdeaCommentService = mock[PersistentTopIdeaCommentService]
  override val idGenerator: IdGenerator = mock[IdGenerator]

  when(idGenerator.nextTopIdeaCommentId()).thenReturn(TopIdeaCommentId("comment-id"))

  feature("create and get") {
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

    scenario("create an idea") {
      topIdeaCommentService.create(
        comment.topIdeaId,
        comment.personalityId,
        comment.comment1,
        comment.comment2,
        comment.comment3,
        comment.vote,
        comment.qualification
      )
      Mockito
        .verify(persistentTopIdeaCommentService)
        .persist(ArgumentMatchers.eq(comment))
    }

    scenario("get an idea") {
      topIdeaCommentService.get(comment.topIdeaCommentId)
      Mockito
        .verify(persistentTopIdeaCommentService)
        .getById(ArgumentMatchers.eq(comment.topIdeaCommentId))
    }
  }

  feature("search") {
    scenario("search all  top idea comment") {
      val start = 42
      val end = None
      val topIdeaIds = None
      val personalityIds = Some(Seq(UserId("some-user"), UserId("another-user")))
      topIdeaCommentService.search(start, end, topIdeaIds, personalityIds)
      Mockito.verify(persistentTopIdeaCommentService).search(start, end, topIdeaIds, personalityIds)
    }
  }
}
