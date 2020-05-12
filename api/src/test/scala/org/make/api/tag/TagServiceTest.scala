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
import org.mockito.{ArgumentMatchers, Mockito}
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
    country = Country("FR"),
    language = Language("fr"),
    questionId = Some(questionId)
  )

  feature("get tag") {
    scenario("get tag from TagId") {
      Given("a TagId")
      When("i get a tag")
      Then("persistent service is called")
      tagService.getTag(TagId("valid-tag"))

      Mockito.verify(persistentTagService).get(TagId("valid-tag"))
    }

    scenario("get tag from a slug") {
      Given("a tag slug")
      When("i get a tag")
      Then("persistent service is called")
      tagService.getTag(TagId("valid-tag-slug"))

      Mockito.verify(persistentTagService).get(TagId("valid-tag-slug"))
    }
  }

  feature("create tag") {
    scenario("creating a tag success") {
      When("i create a tag with label 'new tag'")
      Then("my tag is persisted")

      Mockito
        .when(persistentTagService.get(ArgumentMatchers.any[TagId]))
        .thenReturn(Future.successful(None))

      val tag = newTag("new tag", tagId = TagId("new-tag"), questionId = QuestionId("new-tag"))

      Mockito
        .when(persistentTagService.persist(ArgumentMatchers.any[Tag]))
        .thenReturn(Future.successful(tag))

      val futureNewTag: Future[Tag] = tagService.createTag(
        label = "new tag",
        tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
        question = Question(
          questionId = QuestionId("new-question"),
          slug = "new-question",
          operationId = None,
          country = Country("FR"),
          language = Language("fr"),
          question = "new question",
          shortTitle = None
        ),
        display = TagDisplay.Inherit
      )

      whenReady(futureNewTag, Timeout(3.seconds)) { _ =>
        Mockito
          .verify(persistentTagService)
          .persist(
            ArgumentMatchers.refEq[Tag](
              Tag(
                tagId = TagId(""),
                label = "new tag",
                display = TagDisplay.Inherit,
                weight = 0f,
                tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
                operationId = None,
                country = Country("FR"),
                language = Language("fr"),
                questionId = Some(QuestionId("new-question"))
              ),
              "tagId"
            )
          )
      }

    }
  }

  feature("find tags") {

    scenario("find all tags") {
      Given("a list of registered tags 'find tag1', 'find tag2'")
      When("i find all tags")
      Then("persistent service findAll is called")
      Mockito
        .when(persistentTagService.findAll())
        .thenReturn(Future.successful(Seq.empty))
      val futureFindAll: Future[Seq[Tag]] = tagService.findAll()

      whenReady(futureFindAll, Timeout(3.seconds)) { _ =>
        Mockito.verify(persistentTagService).findAll()
      }
    }

    scenario("find tags with ids 'find-tag1' and 'find-tag2'") {
      Given("a list of registered tags 'find tag1', 'find tag2'")
      When("i find tags with ids 'find-tag1' and 'find-tag2'")
      Then("persistent service findByTagIds is called")
      And("i get tags 'find tag1' and 'find tag2'")

      Mockito.reset(persistentTagService)
      Mockito
        .when(persistentTagService.findAllFromIds(ArgumentMatchers.any[Seq[TagId]]))
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

    scenario("find tags by operation") {
      Given("a list of registered tags 'op tag1', 'op tag2'")
      When("i find tags by operation")

      val questionId = QuestionId("question-id")
      val opId = OperationId("operation-id")

      Mockito.reset(persistentTagService)
      Mockito
        .when(persistentTagService.findByQuestion(ArgumentMatchers.eq(questionId)))
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

    scenario("find tags by theme") {
      Given("a list of registered tags 'theme tag1', 'theme tag2'")
      When("i find tags by theme")

      val questionId = QuestionId("question-of-theme")

      val tag1 = newTag("theme tag1", questionId = questionId)
      val tag2 = newTag("theme tag2", questionId = questionId)

      Mockito.reset(persistentTagService)

      Mockito
        .when(persistentTagService.findAllDisplayed())
        .thenReturn(
          Future
            .successful(Seq(tag1, tag2))
        )
      Mockito
        .when(persistentTagService.findByQuestion(ArgumentMatchers.eq(questionId)))
        .thenReturn(Future.successful(Seq(tag1, tag2)))

      val futureTags: Future[Seq[Tag]] = tagService.findByQuestionId(questionId)

      whenReady(futureTags, Timeout(3.seconds)) { tags =>
        Then("i get tags 'theme tag1' and 'theme tag2'")
        tags.size shouldBe 2
        tags.map(_.label).contains("theme tag1") shouldBe true
        tags.map(_.label).contains("theme tag2") shouldBe true
      }
    }

    scenario("find tags by label") {
      Given("a list of registered tags 'label tag1', 'label tag2'")
      When("i find tags by label")

      Mockito.reset(persistentTagService)
      Mockito
        .when(persistentTagService.findByLabelLike(ArgumentMatchers.eq("label")))
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

  feature("update a tag") {
    scenario("update an non existent tag ") {
      When("i update a tag from an id that not is registered")
      Then("a get a None value")

      Mockito.reset(eventBusService)
      Mockito.when(persistentTagService.get(TagId("non-existent-tag"))).thenReturn(Future.successful(None))

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
          country = Country("FR"),
          language = Language("fr"),
          question = "Fake Question",
          shortTitle = None
        )
      )

      whenReady(futureTag) { tag =>
        tag shouldBe empty
      }
    }

    scenario("update a tag success") {
      When("i update a tag 'old tag success' to 'new tag success'")
      Then("a get the updated tag")

      Mockito
        .when(persistentTagService.get(TagId("old-tag-success")))
        .thenReturn(
          Future.successful(
            Some(newTag("old tag success", tagId = TagId("old-tag-success"), questionId = QuestionId("old-tag")))
          )
        )
      Mockito
        .when(persistentTagService.update(ArgumentMatchers.any[Tag]))
        .thenReturn(
          Future.successful(
            Some(newTag("new tag success", tagId = TagId("old-tag-success"), questionId = QuestionId("new-tag")))
          )
        )

      Mockito
        .when(tagTypeService.getTagType(ArgumentMatchers.any[TagTypeId]))
        .thenReturn(
          Future.successful(
            Some(TagType(TagTypeId("tagTypeId"), "", TagTypeDisplay.Displayed, requiredForEnrichment = false))
          )
        )

      Mockito
        .when(
          elasticsearchProposalAPI
            .searchProposals(ArgumentMatchers.any[SearchQuery])
        )
        .thenReturn(Future.successful(ProposalsSearchResult(0, Seq.empty)))

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
          country = Country("FR"),
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

  feature("retrieve indexed tags") {
    scenario("indexed tags") {
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
          questionId = None,
          country = Country("FR"),
          language = Language("fr")
        ),
        Tag(
          tagId = TagId("tag-no-stake"),
          label = "tag without tagType",
          display = TagDisplay.Inherit,
          tagTypeId = TagTypeId("other"),
          weight = 0,
          operationId = None,
          questionId = None,
          country = Country("FR"),
          language = Language("fr")
        ),
        Tag(
          tagId = TagId("tag-display-not-inherited"),
          label = "tag display not inherited",
          display = TagDisplay.Displayed,
          tagTypeId = TagTypeId("other"),
          weight = 0,
          operationId = None,
          questionId = None,
          country = Country("FR"),
          language = Language("fr")
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
