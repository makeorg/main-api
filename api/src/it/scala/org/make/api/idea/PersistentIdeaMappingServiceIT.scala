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
import org.make.api.DatabaseTest
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentIdeaMappingServiceIT
    extends DatabaseTest
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentIdeaMappingServiceComponent
    with DefaultPersistentIdeaServiceComponent
    with DefaultPersistentTagServiceComponent {

  override protected val cockroachExposedPort: Int = 40012

  def persistMapping(mapping: IdeaMapping): Future[IdeaMapping] = persistentIdeaMappingService.persist(mapping)

  val findMapping: (
    Int,
    Option[Int],
    Option[String],
    Option[String],
    Option[QuestionId],
    Option[TagIdOrNone],
    Option[TagIdOrNone],
    Option[IdeaId]
  ) => Future[Seq[IdeaMapping]] =
    persistentIdeaMappingService.find

  def waitForCompletion(f: Future[_]): Unit = whenReady(f, Timeout(5.seconds))(_ => ())

  val stake: TagTypeId = TagTypeId("c0d8d858-8b04-4dd9-add6-fa65443b622b")

  def createTag(id: TagId, questionId: QuestionId): Tag = {
    Tag(id, id.value, TagDisplay.Displayed, stake, 0.0f, None, Some(questionId), Country("FR"), Language("fr"))
  }

  def createQuestion(id: QuestionId): Question = Question(
    questionId = id,
    slug = id.value,
    country = Country("FR"),
    language = Language("fr"),
    question = id.value,
    shortTitle = None,
    operationId = None
  )

  feature("idea mapping CRUD") {

    scenario("create idea mappings") {

      val questionId = QuestionId("create-idea-mappings")

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId))
        _ <- persistentIdeaService.persist(Idea(IdeaId("some-idea"), "test", createdAt = None, updatedAt = None))
        _ <- persistentIdeaService.persist(Idea(IdeaId("idea-1"), "test", createdAt = None, updatedAt = None))
        _ <- persistentTagService.persist(createTag(TagId("tag-1"), questionId))
        _ <- persistentTagService.persist(createTag(TagId("tag-2"), questionId))
      } yield ()

      waitForCompletion(insertDependencies)

      val insert =
        persistMapping(IdeaMapping(IdeaMappingId("123"), questionId, None, None, IdeaId("some-idea")))

      whenReady(insert, Timeout(5.seconds)) { _.id should be(IdeaMappingId("123")) }

      // You cannot insert mapping for unknown tags
      val withUnknownTag =
        persistMapping(
          IdeaMapping(
            IdeaMappingId("456"),
            questionId,
            Some(TagId("unknown")),
            Some(TagId("unknown")),
            IdeaId("some-idea")
          )
        )

      waitForCompletion(withUnknownTag.failed)

      val normal =
        persistMapping(
          IdeaMapping(IdeaMappingId("567"), questionId, Some(TagId("tag-1")), Some(TagId("tag-2")), IdeaId("idea-1"))
        )

      whenReady(normal, Timeout(5.seconds)) { _.id should be(IdeaMappingId("567")) }

      val unknownIdea =
        persistMapping(
          IdeaMapping(
            IdeaMappingId("678"),
            questionId,
            Some(TagId("tag-1")),
            Some(TagId("tag-2")),
            IdeaId("unknown-idea")
          )
        )

      waitForCompletion(unknownIdea.failed)
    }

    scenario("update and get") {

      val questionId = QuestionId("update-and-get")

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId))
        _ <- persistentTagService.persist(createTag(TagId("update-1"), questionId))
        _ <- persistentTagService.persist(createTag(TagId("update-2"), questionId))
        _ <- persistentIdeaService.persist(Idea(IdeaId("update-1"), "Update 1", createdAt = None, updatedAt = None))
        _ <- persistentIdeaService.persist(Idea(IdeaId("update-2"), "Update 2", createdAt = None, updatedAt = None))
      } yield ()

      waitForCompletion(insertDependencies)

      val mapping =
        IdeaMapping(
          IdeaMappingId("update-1"),
          questionId,
          Some(TagId("update-1")),
          Some(TagId("update-2")),
          IdeaId("update-1")
        )

      waitForCompletion(persistMapping(mapping))

      whenReady(persistentIdeaMappingService.get(IdeaMappingId("update-1")), Timeout(5.seconds)) { maybeMapping =>
        maybeMapping should contain(mapping)
      }

      waitForCompletion(persistentIdeaMappingService.updateMapping(mapping.copy(ideaId = IdeaId("update-2"))))

      whenReady(persistentIdeaMappingService.get(IdeaMappingId("update-1")), Timeout(5.seconds)) { maybeMapping =>
        maybeMapping should contain(mapping.copy(ideaId = IdeaId("update-2")))
      }

    }

    scenario("Finding mappings") {
      val questionId1 = QuestionId("finding-mappings-1")
      val questionId2 = QuestionId("finding-mappings-2")
      val idea1 = IdeaId("find-1")
      val idea2 = IdeaId("find-2")
      val tag1 = TagId("find-1")
      val tag2 = TagId("find-2")
      val tag3 = TagId("find-3")
      val tag4 = TagId("find-4")

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId1))
        _ <- persistentQuestionService.persist(createQuestion(questionId2))
        _ <- persistentTagService.persist(createTag(tag1, questionId1))
        _ <- persistentTagService.persist(createTag(tag2, questionId1))
        _ <- persistentTagService.persist(createTag(tag3, questionId1))
        _ <- persistentTagService.persist(createTag(tag4, questionId1))
        _ <- persistentIdeaService.persist(Idea(idea1, "Find 1", createdAt = None, updatedAt = None))
        _ <- persistentIdeaService.persist(Idea(idea2, "Find 2", createdAt = None, updatedAt = None))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("find-1"), questionId1, Some(tag1), Some(tag2), idea1))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("find-2"), questionId1, Some(tag1), Some(tag4), idea1))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("find-3"), questionId1, Some(tag3), Some(tag4), idea2))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("find-4"), questionId1, Some(tag3), Some(tag2), idea1))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("find-5"), questionId1, None, Some(tag2), idea2))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("find-6"), questionId1, Some(tag1), None, idea2))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("find-7"), questionId1, None, None, idea1))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("find-8"), questionId2, None, None, idea1))
      } yield ()

      waitForCompletion(insertDependencies)

      whenReady(findMapping(0, None, None, None, None, Some(Right(tag1)), None, None), Timeout(5.seconds)) { results =>
        results.map(_.id.value).sorted should be(Seq("find-1", "find-2", "find-6"))
      }

      whenReady(findMapping(0, None, None, None, None, None, Some(Right(tag1)), None), Timeout(5.seconds)) { results =>
        results should be(empty)
      }

      whenReady(findMapping(0, None, None, None, None, None, None, Some(idea2)), Timeout(5.seconds)) { results =>
        results.map(_.id.value).sorted should be(Seq("find-3", "find-5", "find-6"))
      }

      whenReady(findMapping(0, None, None, None, None, Some(Left(None)), None, None), Timeout(5.seconds)) { results =>
        results.map(_.id.value).filter(_.startsWith("find")).sorted should be(Seq("find-5", "find-7", "find-8"))
      }

      whenReady(findMapping(0, None, None, None, None, None, Some(Left(None)), None), Timeout(5.seconds)) { results =>
        results.map(_.id.value).filter(_.startsWith("find")).sorted should be(Seq("find-6", "find-7", "find-8"))
      }

      whenReady(findMapping(0, None, None, None, Some(questionId1), None, Some(Left(None)), None), Timeout(5.seconds)) {
        results =>
          results.map(_.id.value).sorted should be(Seq("find-6", "find-7"))
      }

      whenReady(findMapping(0, None, None, None, Some(questionId2), None, Some(Left(None)), None), Timeout(5.seconds)) {
        results =>
          results.map(_.id.value).sorted should be(Seq("find-8"))
      }
    }

    scenario("count mappings") {
      val questionId = QuestionId("count-mappings-1")
      val idea = IdeaId("count-1")
      val tag1 = TagId("count-1")
      val tag2 = TagId("count-2")

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId))
        _ <- persistentTagService.persist(createTag(tag1, questionId))
        _ <- persistentTagService.persist(createTag(tag2, questionId))
        _ <- persistentIdeaService.persist(Idea(idea, "Count", createdAt = None, updatedAt = None))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("count-1"), questionId, Some(tag1), Some(tag2), idea))
        _ <- persistMapping(IdeaMapping(IdeaMappingId("count-2"), questionId, None, Some(tag2), idea))
      } yield ()

      waitForCompletion(insertDependencies)

      whenReady(persistentIdeaMappingService.count(None, Some(Right(tag1)), None, None), Timeout(5.seconds)) {
        _ shouldBe 1
      }

      whenReady(persistentIdeaMappingService.count(None, None, Some(Right(tag1)), None), Timeout(5.seconds)) {
        _ shouldBe 0
      }

      whenReady(persistentIdeaMappingService.count(Some(questionId), Some(Left(None)), None, None), Timeout(5.seconds)) {
        _ shouldBe 1
      }

      whenReady(persistentIdeaMappingService.count(Some(questionId), None, Some(Left(None)), None), Timeout(5.seconds)) {
        _ shouldBe 0
      }
    }
  }
}
