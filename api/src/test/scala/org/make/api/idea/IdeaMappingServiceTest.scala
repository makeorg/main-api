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
import org.make.api.MakeUnitTest
import org.make.api.question.{PersistentQuestionService, PersistentQuestionServiceComponent}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class IdeaMappingServiceTest
    extends MakeUnitTest
    with DefaultIdeaMappingServiceComponent
    with PersistentIdeaMappingServiceComponent
    with PersistentIdeaServiceComponent
    with TagServiceComponent
    with PersistentQuestionServiceComponent
    with IdGeneratorComponent {

  override val persistentIdeaMappingService: PersistentIdeaMappingService = mock[PersistentIdeaMappingService]
  override val persistentIdeaService: PersistentIdeaService = mock[PersistentIdeaService]
  override val tagService: TagService = mock[TagService]
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]
  override val idGenerator: IdGenerator = mock[IdGenerator]

  def createTag(tagId: TagId, label: String): Tag = Tag(
    tagId = tagId,
    label = label,
    display = TagDisplay.Inherit,
    tagTypeId = TagTypeId("some-id"),
    weight = 0.0F,
    operationId = None,
    questionId = Some(QuestionId("my-question")),
    themeId = None,
    country = Country("FR"),
    language = Language("fr")
  )

  when(persistentIdeaMappingService.updateMapping(any[IdeaMapping]))
    .thenAnswer(invocation => Future.successful(Some(invocation.getArgument[IdeaMapping](0))))

  when(persistentIdeaService.persist(any[Idea]))
    .thenAnswer(invocation => Future.successful(invocation.getArgument[Idea](0)))

  feature("changeIdea") {
    scenario("changing for a non-existent mapping") {
      when(persistentIdeaMappingService.get(IdeaMappingId("unknown"))).thenReturn(Future.successful(None))

      whenReady(
        ideaMappingService
          .changeIdea(IdeaMappingId = IdeaMappingId("unknown"), newIdea = IdeaId("some-id"), migrateProposals = true),
        Timeout(5.seconds)
      )(_ should be(None))
    }

    scenario("normal case") {
      when(persistentIdeaMappingService.get(IdeaMappingId("changeIdea")))
        .thenReturn(
          Future.successful(
            Some(IdeaMapping(IdeaMappingId("changeIdea"), QuestionId("question"), None, None, IdeaId("original-idea")))
          )
        )

      whenReady(
        ideaMappingService
          .changeIdea(IdeaMappingId = IdeaMappingId("changeIdea"), newIdea = IdeaId("new-id"), migrateProposals = true),
        Timeout(5.seconds)
      ) { maybeMapping =>
        maybeMapping.map(_.ideaId) should be(Some(IdeaId("new-id")))

      }
    }
  }

  feature("getOrCreateMapping") {
    scenario("existing mapping") {
      when(
        persistentIdeaMappingService
          .find(Some(QuestionId("my-question")), Some(Right(TagId("tag-1"))), Some(Right(TagId("tag-2"))), None)
      ).thenReturn(
        Future.successful(
          Seq(
            IdeaMapping(
              IdeaMappingId("mapping-1"),
              QuestionId("my-question"),
              Some(TagId("tag-1")),
              Some(TagId("tag-2")),
              IdeaId("first-idea")
            )
          )
        )
      )

      val mapping =
        ideaMappingService.getOrCreateMapping(QuestionId("my-question"), Some(TagId("tag-1")), Some(TagId("tag-2")))

      whenReady(mapping, Timeout(5.seconds)) {
        _.id should be(IdeaMappingId("mapping-1"))
      }
    }

    scenario("multiple mappings") {
      when(
        persistentIdeaMappingService
          .find(Some(QuestionId("my-question")), Some(Right(TagId("tag-3"))), Some(Right(TagId("tag-4"))), None)
      ).thenReturn(
        Future.successful(
          Seq(
            IdeaMapping(
              IdeaMappingId("mapping-2"),
              QuestionId("my-question"),
              Some(TagId("tag-3")),
              Some(TagId("tag-4")),
              IdeaId("second-idea")
            ),
            IdeaMapping(
              IdeaMappingId("mapping-3"),
              QuestionId("my-question"),
              Some(TagId("tag-3")),
              Some(TagId("tag-4")),
              IdeaId("third-idea")
            )
          )
        )
      )

      val mapping =
        ideaMappingService.getOrCreateMapping(QuestionId("my-question"), Some(TagId("tag-3")), Some(TagId("tag-4")))

      whenReady(mapping, Timeout(5.seconds)) {
        _.id should be(IdeaMappingId("mapping-2"))
      }
    }

    scenario("missing mapping") {

      when(
        persistentIdeaMappingService
          .find(Some(QuestionId("my-question")), Some(Right(TagId("tag-5"))), Some(Right(TagId("tag-6"))), None)
      ).thenReturn(Future.successful(Seq.empty))

      when(persistentQuestionService.getById(QuestionId("my-question"))).thenReturn(
        Future.successful(
          Some(
            Question(
              questionId = QuestionId("my-question"),
              slug = "my-question",
              country = Country("FR"),
              language = Language("fr"),
              question = "my question ?",
              operationId = None,
              themeId = None
            )
          )
        )
      )

      when(tagService.findByTagIds(Seq(TagId("tag-5"), TagId("tag-6"))))
        .thenReturn(Future.successful(Seq(createTag(TagId("tag-5"), "tag 5"), createTag(TagId("tag-6"), "tag 6"))))

      when(idGenerator.nextIdeaId()).thenReturn(IdeaId("my-ultimate-idea"))
      when(idGenerator.nextIdeaMappingId()).thenReturn(IdeaMappingId("mapping-2"))

      val ideaMapping = IdeaMapping(
        IdeaMappingId("mapping-2"),
        QuestionId("my-question"),
        Some(TagId("tag-5")),
        Some(TagId("tag-6")),
        IdeaId("my-ultimate-idea")
      )

      when(persistentIdeaMappingService.persist(ideaMapping)).thenReturn(Future.successful(ideaMapping))

      val mapping =
        ideaMappingService.getOrCreateMapping(QuestionId("my-question"), Some(TagId("tag-5")), Some(TagId("tag-6")))

      whenReady(mapping, Timeout(5.seconds)) { ideaMapping =>
        ideaMapping.id should be(IdeaMappingId("mapping-2"))
      }
    }

    scenario("missing mapping on None / None") {

      when(
        persistentIdeaMappingService
          .find(Some(QuestionId("my-question")), Some(Left(None)), Some(Left(None)), None)
      ).thenReturn(Future.successful(Seq.empty))

      when(persistentQuestionService.getById(QuestionId("my-question"))).thenReturn(
        Future.successful(
          Some(
            Question(
              questionId = QuestionId("my-question"),
              slug = "my-question",
              country = Country("FR"),
              language = Language("fr"),
              question = "my question ?",
              operationId = None,
              themeId = None
            )
          )
        )
      )

      when(tagService.findByTagIds(Seq())).thenReturn(Future.successful(Seq()))

      when(idGenerator.nextIdeaId()).thenReturn(IdeaId("my-ultimate-idea-2"))
      when(idGenerator.nextIdeaMappingId()).thenReturn(IdeaMappingId("mapping-3"))

      val ideaMapping =
        IdeaMapping(IdeaMappingId("mapping-3"), QuestionId("my-question"), None, None, IdeaId("my-ultimate-idea-2"))

      when(persistentIdeaMappingService.persist(ideaMapping)).thenReturn(Future.successful(ideaMapping))

      val mapping =
        ideaMappingService.getOrCreateMapping(QuestionId("my-question"), None, None)

      whenReady(mapping, Timeout(5.seconds)) { ideaMapping =>
        ideaMapping.id should be(IdeaMappingId("mapping-3"))
      }
    }

  }

}
