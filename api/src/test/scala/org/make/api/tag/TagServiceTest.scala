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

package org.make.api.tag

import cats.data.NonEmptyList
import org.make.api.MakeUnitTest
import org.make.api.proposal.ProposalSearchEngine
import org.make.api.tagtype.{PersistentTagTypeService, PersistentTagTypeServiceComponent, TagTypeService}
import org.make.api.technical.{DefaultIdGeneratorComponent, EventBusService, EventBusServiceComponent}
import org.make.core.operation.OperationId
import org.make.core.proposal.SearchQuery
import org.make.core.proposal.indexed.{IndexedTag, ProposalsSearchResult}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId, _}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TagServiceTest
    extends MakeUnitTest
    with DefaultTagServiceComponent
    with PersistentTagServiceComponent
    with PersistentTagTypeServiceComponent
    with EventBusServiceComponent
    with DefaultIdGeneratorComponent {

  override val persistentTagService: PersistentTagService = mock[PersistentTagService]
  override val persistentTagTypeService: PersistentTagTypeService = mock[PersistentTagTypeService]
  override val tagTypeService: TagTypeService = mock[TagTypeService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val eventBusService: EventBusService = mock[EventBusService]

  def newTag(
    label: String,
    tagId: TagId = idGenerator.nextTagId(),
    questionId: QuestionId,
    operationId: Option[OperationId] = None
  ): Tag = Tag(
    tagId = tagId,
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
    operationId = operationId,
    questionId = Some(questionId)
  )

  Feature("get tag") {
    Scenario("get tag from TagId") {
      Given("a TagId")
      When("i get a tag")
      Then("persistent service is called")
      tagService.getTag(TagId("valid-tag"))

      verify(persistentTagService).get(TagId("valid-tag"))
    }

    Scenario("get tag from a slug") {
      Given("a tag slug")
      When("i get a tag")
      Then("persistent service is called")
      tagService.getTag(TagId("valid-tag-slug"))

      verify(persistentTagService).get(TagId("valid-tag-slug"))
    }
  }

  Feature("create tag") {
    Scenario("creating a tag success") {
      When("i create a tag with label 'new tag'")
      Then("my tag is persisted")

      when(persistentTagService.get(any[TagId]))
        .thenReturn(Future.successful(None))

      val tag = newTag("new tag", tagId = TagId("new-tag"), questionId = QuestionId("new-tag"))

      when(persistentTagService.persist(any[Tag]))
        .thenReturn(Future.successful(tag))

      val futureNewTag: Future[Tag] = tagService.createTag(
        label = "new tag",
        tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
        question = Question(
          questionId = QuestionId("new-question"),
          slug = "new-question",
          operationId = None,
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "new question",
          shortTitle = None
        ),
        display = TagDisplay.Inherit
      )

      whenReady(futureNewTag, Timeout(3.seconds)) { _ =>
        verify(persistentTagService)
          .persist(
            refEq[Tag](
              Tag(
                tagId = TagId(""),
                label = "new tag",
                display = TagDisplay.Inherit,
                weight = 0f,
                tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
                operationId = None,
                questionId = Some(QuestionId("new-question"))
              ),
              "tagId"
            )
          )
      }

    }
  }

  Feature("find tags") {

    Scenario("find all tags") {
      Given("a list of registered tags 'find tag1', 'find tag2'")
      When("i find all tags")
      Then("persistent service findAll is called")
      when(persistentTagService.findAll())
        .thenReturn(Future.successful(Seq.empty))
      val futureFindAll: Future[Seq[Tag]] = tagService.findAll()

      whenReady(futureFindAll, Timeout(3.seconds)) { _ =>
        verify(persistentTagService).findAll()
      }
    }

    Scenario("find tags with ids 'find-tag1' and 'find-tag2'") {
      Given("a list of registered tags 'find tag1', 'find tag2'")
      When("i find tags with ids 'find-tag1' and 'find-tag2'")
      Then("persistent service findByTagIds is called")
      And("i get tags 'find tag1' and 'find tag2'")

      reset(persistentTagService)
      when(persistentTagService.findAllFromIds(any[Seq[TagId]]))
        .thenReturn(
          Future.successful(
            Seq(
              newTag("find tag1", questionId = QuestionId("tag-1")),
              newTag("find tag2", questionId = QuestionId("tag-1"))
            )
          )
        )

      val futureTags: Future[Seq[Tag]] = tagService.findByTagIds(Seq(TagId("find-tag1"), TagId("find-tag2")))

      whenReady(futureTags, Timeout(3.seconds)) { tags =>
        tags.size shouldBe 2
        tags.map(_.label).contains("find tag1") shouldBe true
        tags.map(_.label).contains("find tag2") shouldBe true
      }
    }

    Scenario("find tags by operation") {
      Given("a list of registered tags 'op tag1', 'op tag2'")
      When("i find tags by operation")

      val questionId = QuestionId("question-id")
      val opId = OperationId("operation-id")

      reset(persistentTagService)
      when(persistentTagService.findByQuestion(eqTo(questionId)))
        .thenReturn(
          Future
            .successful(
              Seq(
                newTag("op tag1", operationId = Some(opId), questionId = questionId),
                newTag("op tag2", operationId = Some(opId), questionId = questionId)
              )
            )
        )

      val futureTags: Future[Seq[Tag]] = tagService.findByQuestionId(questionId)

      whenReady(futureTags, Timeout(3.seconds)) { tags =>
        Then("i get tags 'op tag1' and 'op tag2'")
        tags.size shouldBe 2
        tags.map(_.label).contains("op tag1") shouldBe true
        tags.map(_.label).contains("op tag2") shouldBe true
        tags.forall(_.questionId.contains(questionId)) shouldBe true
      }
    }

    Scenario("find tags by theme") {
      Given("a list of registered tags 'theme tag1', 'theme tag2'")
      When("i find tags by theme")

      val questionId = QuestionId("question-of-theme")

      val tag1 = newTag("theme tag1", questionId = questionId)
      val tag2 = newTag("theme tag2", questionId = questionId)

      reset(persistentTagService)

      when(persistentTagService.findAllDisplayed())
        .thenReturn(
          Future
            .successful(Seq(tag1, tag2))
        )
      when(persistentTagService.findByQuestion(eqTo(questionId)))
        .thenReturn(Future.successful(Seq(tag1, tag2)))

      val futureTags: Future[Seq[Tag]] = tagService.findByQuestionId(questionId)

      whenReady(futureTags, Timeout(3.seconds)) { tags =>
        Then("i get tags 'theme tag1' and 'theme tag2'")
        tags.size shouldBe 2
        tags.map(_.label).contains("theme tag1") shouldBe true
        tags.map(_.label).contains("theme tag2") shouldBe true
      }
    }

    Scenario("find tags by label") {
      Given("a list of registered tags 'label tag1', 'label tag2'")
      When("i find tags by label")

      reset(persistentTagService)
      when(persistentTagService.findByLabelLike(eqTo("label")))
        .thenReturn(
          Future.successful(
            Seq(
              newTag("label tag1", questionId = QuestionId("tag-1")),
              newTag("label tag2", questionId = QuestionId("tag-2"))
            )
          )
        )

      val futureTags: Future[Seq[Tag]] = tagService.findByLabel("label", like = true)

      whenReady(futureTags, Timeout(3.seconds)) { tags =>
        Then("i get tags 'label tag1' and 'label tag2'")
        tags.size shouldBe 2
        tags.map(_.label).contains("label tag1") shouldBe true
        tags.map(_.label).contains("label tag2") shouldBe true
      }
    }
  }

  Feature("update a tag") {
    Scenario("update an non existent tag ") {
      When("i update a tag from an id that not is registered")
      Then("a get a None value")

      reset(eventBusService)
      when(persistentTagService.get(TagId("non-existent-tag"))).thenReturn(Future.successful(None))

      val futureTag: Future[Option[Tag]] = tagService.updateTag(
        tagId = TagId("non-existent-tag"),
        label = "new non existent tag",
        display = TagDisplay.Inherit,
        tagTypeId = TagTypeId("fake-tagTypeId"),
        weight = 0f,
        question = Question(
          questionId = QuestionId("fake-question"),
          slug = "fake-question",
          operationId = None,
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "Fake Question",
          shortTitle = None
        )
      )

      whenReady(futureTag) { tag =>
        tag shouldBe empty
      }
    }

    Scenario("update a tag success") {
      When("i update a tag 'old tag success' to 'new tag success'")
      Then("a get the updated tag")

      when(persistentTagService.get(TagId("old-tag-success")))
        .thenReturn(
          Future.successful(
            Some(newTag("old tag success", tagId = TagId("old-tag-success"), questionId = QuestionId("old-tag")))
          )
        )
      when(persistentTagService.update(any[Tag]))
        .thenReturn(
          Future.successful(
            Some(newTag("new tag success", tagId = TagId("old-tag-success"), questionId = QuestionId("new-tag")))
          )
        )

      when(tagTypeService.getTagType(any[TagTypeId]))
        .thenReturn(
          Future.successful(
            Some(TagType(TagTypeId("tagTypeId"), "", TagTypeDisplay.Displayed, requiredForEnrichment = false))
          )
        )

      when(
        elasticsearchProposalAPI
          .searchProposals(any[SearchQuery])
      ).thenReturn(Future.successful(ProposalsSearchResult(0, Seq.empty)))

      val futureTag: Future[Option[Tag]] = tagService.updateTag(
        tagId = TagId("old-tag-success"),
        label = "new tag success",
        display = TagDisplay.Inherit,
        tagTypeId = TagTypeId("fake-tagTypeId"),
        weight = 0f,
        question = Question(
          questionId = QuestionId("fake-question"),
          slug = "fake-question",
          operationId = None,
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "Fake Question",
          shortTitle = None
        )
      )

      whenReady(futureTag, Timeout(3.seconds)) { tag =>
        tag.map(_.label) shouldEqual Some("new tag success")
      }
    }

  }

  Feature("retrieve indexed tags") {
    Scenario("indexed tags") {
      val tagTypes: Seq[TagType] = Seq(
        TagType(TagTypeId("stake"), label = "stake", display = TagTypeDisplay.Displayed, requiredForEnrichment = true)
      )
      val tags: Seq[Tag] = Seq(
        Tag(
          tagId = TagId("tag-stake"),
          label = "tag with stake tagType",
          display = TagDisplay.Inherit,
          tagTypeId = TagTypeId("stake"),
          weight = 0,
          operationId = None,
          questionId = None
        ),
        Tag(
          tagId = TagId("tag-no-stake"),
          label = "tag without tagType",
          display = TagDisplay.Inherit,
          tagTypeId = TagTypeId("other"),
          weight = 0,
          operationId = None,
          questionId = None
        ),
        Tag(
          tagId = TagId("tag-display-not-inherited"),
          label = "tag display not inherited",
          display = TagDisplay.Displayed,
          tagTypeId = TagTypeId("other"),
          weight = 0,
          operationId = None,
          questionId = None
        )
      )

      val indexedTags: Seq[IndexedTag] = tagService.retrieveIndexedTags(tags, tagTypes)
      indexedTags.size shouldBe 3
      indexedTags.exists(tagStake   => tagStake.tagId == TagId("tag-stake") && tagStake.display)
      indexedTags.exists(tagNoStake => tagNoStake.tagId == TagId("tag-no-stake") && !tagNoStake.display)
      indexedTags.exists(tagDisplay => tagDisplay.tagId == TagId("tag-display-not-inherited") && tagDisplay.display)
    }
  }
}
