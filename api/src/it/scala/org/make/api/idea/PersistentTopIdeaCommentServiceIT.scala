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

package org.make.api.idea

import cats.data.NonEmptyList
import org.make.api.{DatabaseTest, TestUtilsIT}
import org.make.api.idea.topIdeaComments.DefaultPersistentTopIdeaCommentServiceComponent
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.core.idea._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import org.make.core.technical.Pagination.Start

class PersistentTopIdeaCommentServiceIT
    extends DatabaseTest
    with DefaultPersistentTopIdeaCommentServiceComponent
    with DefaultPersistentTopIdeaServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentIdeaServiceComponent {

  override protected val cockroachExposedPort: Int = 40021

  private val question: Question = Question(
    questionId = QuestionId("question"),
    slug = "question-slug",
    countries = NonEmptyList.of(Country("FR")),
    language = Language("fr"),
    question = "question ?",
    shortTitle = None,
    operationId = None
  )

  private val ideas = Seq(
    Idea(IdeaId("idea-0"), "test-0", createdAt = None, updatedAt = None),
    Idea(IdeaId("idea-1"), "test-1", createdAt = None, updatedAt = None),
    Idea(IdeaId("idea-2"), "test-2", createdAt = None, updatedAt = None),
    Idea(IdeaId("idea-3"), "test-3", createdAt = None, updatedAt = None),
    Idea(IdeaId("idea-4"), "test-4", createdAt = None, updatedAt = None)
  )

  private val topIdeas: Seq[TopIdea] = Seq(
    TopIdea(
      TopIdeaId("top-idea-id-0"),
      ideas(0).ideaId,
      question.questionId,
      "top-idea-0",
      "label",
      TopIdeaScores(0, 0, 0),
      0
    ),
    TopIdea(
      TopIdeaId("top-idea-id-1"),
      ideas(1).ideaId,
      question.questionId,
      "top-idea-1",
      "label",
      TopIdeaScores(0, 0, 0),
      0
    ),
    TopIdea(
      TopIdeaId("top-idea-id-2"),
      ideas(2).ideaId,
      question.questionId,
      "top-idea-2",
      "label",
      TopIdeaScores(0, 0, 0),
      0
    ),
    TopIdea(
      TopIdeaId("top-idea-id-3"),
      ideas(3).ideaId,
      question.questionId,
      "top-idea-3",
      "label",
      TopIdeaScores(0, 0, 0),
      0
    ),
    TopIdea(
      TopIdeaId("top-idea-id-4"),
      ideas(4).ideaId,
      question.questionId,
      "top-idea-4",
      "label",
      TopIdeaScores(0, 0, 0),
      0
    )
  )

  val personalities: Seq[User] = Seq(
    TestUtilsIT.user(UserId("personality-0"), email = "personality-0@make.org"),
    TestUtilsIT.user(UserId("personality-1"), email = "personality-1@make.org"),
    TestUtilsIT.user(UserId("personality-2"), email = "personality-2@make.org"),
    TestUtilsIT.user(UserId("personality-3"), email = "personality-3@make.org"),
    TestUtilsIT.user(UserId("personality-4"), email = "personality-4@make.org")
  )

  def initForTopIdeaComments: Future[Unit] = {
    for {
      _ <- persistentQuestionService.persist(question)
      _ <- Future.traverse(ideas)(persistentIdeaService.persist)
      _ <- Future.traverse(topIdeas)(persistentTopIdeaService.persist)
      _ <- Future.traverse(personalities)(persistentUserService.persist)
    } yield ()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(initForTopIdeaComments, 5.seconds)
  }

  def waitForCompletion(f: Future[_]): Unit = whenReady(f, Timeout(2.seconds))(_ => ())

  Feature("top idea comment CRUD") {

    Scenario("create top idea comment") {
      val insert = persistentTopIdeaCommentService.persist(
        TopIdeaComment(
          TopIdeaCommentId("create-1"),
          topIdeas.head.topIdeaId,
          personalities.head.userId,
          Some("comment"),
          None,
          None,
          CommentVoteKey.Agree,
          Some(CommentQualificationKey.Doable)
        )
      )

      whenReady(insert, Timeout(5.seconds)) {
        _.topIdeaCommentId should be(TopIdeaCommentId("create-1"))
      }
    }

    Scenario("update and get top idea comment") {

      val topIdeaComment =
        TopIdeaComment(
          TopIdeaCommentId("update-1"),
          topIdeas(1).topIdeaId,
          personalities(1).userId,
          Some("comment"),
          None,
          None,
          CommentVoteKey.Agree,
          Some(CommentQualificationKey.Doable)
        )

      waitForCompletion(persistentTopIdeaCommentService.persist(topIdeaComment))

      whenReady(persistentTopIdeaCommentService.getById(TopIdeaCommentId("update-1")), Timeout(5.seconds)) {
        maybeTopIdeaComment =>
          maybeTopIdeaComment.map(_.topIdeaId) shouldBe Some(topIdeaComment.topIdeaId)
          maybeTopIdeaComment.flatMap(_.comment1) shouldBe Some("comment")
          maybeTopIdeaComment.flatMap(_.comment2) shouldBe empty
      }

      waitForCompletion(
        persistentTopIdeaCommentService.modify(
          topIdeaComment.copy(
            topIdeaId = topIdeas(2).topIdeaId,
            personalityId = personalities(2).userId,
            comment1 = None,
            comment2 = Some("new comment"),
            comment3 = None,
            qualification = None
          )
        )
      )

      whenReady(persistentTopIdeaCommentService.getById(TopIdeaCommentId("update-1")), Timeout(5.seconds)) {
        case None => fail()
        case Some(topIdeaComment) =>
          topIdeaComment.topIdeaId shouldBe topIdeas(2).topIdeaId
          topIdeaComment.personalityId shouldBe personalities(2).userId
          topIdeaComment.comment1 shouldBe None
          topIdeaComment.comment2 shouldBe Some("new comment")
          topIdeaComment.comment3 shouldBe None
          topIdeaComment.qualification shouldBe None
      }

    }

    Scenario("search & count top idea comments") {
      val topIdeaComments: Seq[TopIdeaComment] = Seq(
        TopIdeaComment(
          TopIdeaCommentId("find-1"),
          topIdeas(4).topIdeaId,
          personalities(3).userId,
          Some("comment"),
          None,
          None,
          CommentVoteKey.Agree,
          Some(CommentQualificationKey.Doable)
        ),
        TopIdeaComment(
          TopIdeaCommentId("find-2"),
          topIdeas(4).topIdeaId,
          personalities(4).userId,
          Some("comment"),
          None,
          None,
          CommentVoteKey.Agree,
          Some(CommentQualificationKey.Doable)
        ),
        TopIdeaComment(
          TopIdeaCommentId("find-3"),
          topIdeas(3).topIdeaId,
          personalities(3).userId,
          Some("comment"),
          None,
          None,
          CommentVoteKey.Agree,
          Some(CommentQualificationKey.Doable)
        ),
        TopIdeaComment(
          TopIdeaCommentId("find-4"),
          topIdeas(3).topIdeaId,
          personalities(4).userId,
          Some("comment"),
          None,
          None,
          CommentVoteKey.Agree,
          Some(CommentQualificationKey.Doable)
        )
      )

      waitForCompletion(Future.traverse(topIdeaComments)(persistentTopIdeaCommentService.persist))

      whenReady(
        persistentTopIdeaCommentService.search(Start.zero, None, Some(Seq(topIdeas(4).topIdeaId)), None),
        Timeout(5.seconds)
      ) { results =>
        results.map(_.topIdeaCommentId.value).sorted should be(Seq("find-1", "find-2"))
      }

      whenReady(
        persistentTopIdeaCommentService.search(Start.zero, None, None, Some(Seq(personalities(3).userId))),
        Timeout(5.seconds)
      ) { results =>
        results.map(_.topIdeaCommentId.value).sorted should be(Seq("find-1", "find-3"))
      }

      whenReady(
        persistentTopIdeaCommentService
          .search(Start.zero, None, Some(Seq(topIdeas(4).topIdeaId)), Some(Seq(personalities(3).userId))),
        Timeout(5.seconds)
      ) { results =>
        results.map(_.topIdeaCommentId.value).sorted should be(Seq("find-1"))
      }

      //COUNT
      whenReady(persistentTopIdeaCommentService.count(Some(Seq(topIdeas(4).topIdeaId)), None), Timeout(5.seconds)) {
        _ shouldBe 2
      }
      whenReady(persistentTopIdeaCommentService.count(None, Some(Seq(personalities(3).userId))), Timeout(5.seconds)) {
        _ shouldBe 2
      }
      whenReady(
        persistentTopIdeaCommentService.count(Some(Seq(topIdeas(4).topIdeaId)), Some(Seq(personalities(3).userId))),
        Timeout(5.seconds)
      ) {
        _ shouldBe 1
      }

      //COUNT FOR ALL
      whenReady(persistentTopIdeaCommentService.countForAll(topIdeaComments.map(_.topIdeaId)), Timeout(5.seconds)) {
        result =>
          result.size should be(2)
          result(topIdeas(4).topIdeaId.value) should be(2)
      }
    }
  }
}
