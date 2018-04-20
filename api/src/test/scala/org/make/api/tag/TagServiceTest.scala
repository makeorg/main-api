package org.make.api.tag

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import org.make.api.MakeUnitTest
import org.make.api.proposal.ProposalCoordinatorService
import org.make.api.sequence.SequenceCoordinatorService
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.technical.{DefaultIdGeneratorComponent, EventBusService, EventBusServiceComponent}
import org.make.api.userhistory.UserEvent.UserUpdatedTagEvent
import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.user.UserId
import org.mockito.{ArgumentMatcher, ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TagServiceTest
    extends MakeUnitTest
    with DefaultTagServiceComponent
    with PersistentTagServiceComponent
    with EventBusServiceComponent
    with DefaultIdGeneratorComponent {

  override val persistentTagService: PersistentTagService = mock[PersistentTagService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val actorSystem: ActorSystem = ActorSystem()
  override val sequenceCoordinatorService: SequenceCoordinatorService = mock[SequenceCoordinatorService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val readJournal: MakeReadJournal = mock[MakeReadJournal]

  def newTag(label: String, tagId: TagId = idGenerator.nextTagId()): Tag = Tag(
    tagId = tagId,
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
    operationId = None,
    themeId = None,
    country = "FR",
    language = "fr"
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
      tagService.getTag("valid-tag-slug")

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

      val tag = newTag("new tag", tagId = TagId("new-tag"))

      Mockito
        .when(persistentTagService.persist(ArgumentMatchers.any[Tag]))
        .thenReturn(Future.successful(tag))

      val futureNewTag: Future[Tag] = tagService.createLegacyTag("new tag")

      whenReady(futureNewTag, Timeout(3.seconds)) { _ =>
        Mockito.verify(persistentTagService).persist(ArgumentMatchers.any[Tag])
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
      Given("a list of registered tags 'find tag1', 'find tag2', 'find tag3'")
      When("i find tags with ids 'find-tag1' and 'find-tag2'")
      Then("persistent service findAll is called")
      And("i get tags 'find tag1' and 'find tag2'")

      Mockito.reset(persistentTagService)
      Mockito
        .when(persistentTagService.findAllFromIds(ArgumentMatchers.any[Seq[TagId]]))
        .thenReturn(Future.successful(Seq(newTag("find tag1"), newTag("find tag2"))))

      val futureTags: Future[Seq[Tag]] = tagService.findByTagIds(Seq(TagId("find-tag1"), TagId("find-tag2")))

      whenReady(futureTags, Timeout(3.seconds)) { tags =>
        tags.size shouldBe 2
        tags.map(_.label).contains("find tag1") shouldBe true
        tags.map(_.label).contains("find tag2") shouldBe true
      }
    }
  }

  feature("update a tag") {
    scenario("event user should be published") {
      When("i update a tag")
      Then("a UserUpdatedTagEvent should be published")

      Mockito.when(persistentTagService.get(TagId("tag-that-generate-user-event"))).thenReturn(Future.successful(None))

      tagService.updateTag(
        slug = TagId("tag-that-generate-user-event"),
        newTagLabel = "new tag that generate user event",
        connectedUserId = Some(UserId("bob")),
        requestContext = RequestContext.empty
      )

      class MatchUserUpdatedTagEvent(connectedUserId: Option[UserId], oldTag: String, newTag: String)
          extends ArgumentMatcher[AnyRef] {
        override def matches(argument: AnyRef): Boolean = {
          val userUpdatedTagEvent: UserUpdatedTagEvent = argument.asInstanceOf[UserUpdatedTagEvent]
          userUpdatedTagEvent.connectedUserId == connectedUserId &&
          userUpdatedTagEvent.oldTag == oldTag &&
          userUpdatedTagEvent.newTag == newTag
        }
      }

      Mockito
        .verify(eventBusService)
        .publish(
          ArgumentMatchers
            .argThat(
              new MatchUserUpdatedTagEvent(
                Some(UserId("bob")),
                "tag-that-generate-user-event",
                "new tag that generate user event"
              )
            )
        )
    }

    scenario("update an non existent tag ") {
      When("i update a tag from an id that not is registered")
      Then("a get a None value")

      Mockito.reset(eventBusService)
      Mockito.when(persistentTagService.get(TagId("non-existent-tag"))).thenReturn(Future.successful(None))

      val futureTag: Future[Option[Tag]] = tagService.updateTag(
        slug = TagId("non-existent-tag"),
        newTagLabel = "new non existent tag",
        connectedUserId = Some(UserId("bob")),
        requestContext = RequestContext.empty
      )

      whenReady(futureTag) { tag =>
        tag shouldBe empty
      }
    }

    scenario("update a tag success") {
      When("i update a tag 'old tag success' to 'new tag success'")
      Then("a get a None value")

      Mockito.reset(eventBusService)
      Mockito
        .when(persistentTagService.get(TagId("old-tag-success")))
        .thenReturn(Future.successful(Some(newTag("old tag success", tagId = TagId("old-tag-success")))))
      Mockito
        .when(persistentTagService.persist(ArgumentMatchers.any[Tag]))
        .thenReturn(Future.successful(newTag("new tag success", tagId = TagId("new-tag-success"))))
      Mockito.when(readJournal.currentPersistenceIds()).thenReturn(Source(List("id1", "id2", "id3")))
      Mockito.when(persistentTagService.remove(TagId("old-tag-success"))).thenReturn(Future.successful(1))

      val futureTag: Future[Option[Tag]] = tagService.updateTag(
        slug = TagId("old-tag-success"),
        newTagLabel = "new tag success",
        connectedUserId = Some(UserId("bob")),
        requestContext = RequestContext.empty
      )

      whenReady(futureTag, Timeout(3.seconds)) { tag =>
        Mockito
          .verify(proposalCoordinatorService)
          .updateProposalTag(ProposalId("id1"), TagId("old-tag-success"), TagId("new-tag-success"))
        Mockito
          .verify(proposalCoordinatorService)
          .updateProposalTag(ProposalId("id2"), TagId("old-tag-success"), TagId("new-tag-success"))
        Mockito
          .verify(proposalCoordinatorService)
          .updateProposalTag(ProposalId("id3"), TagId("old-tag-success"), TagId("new-tag-success"))

        Mockito
          .verify(sequenceCoordinatorService)
          .updateSequenceTag(SequenceId("id1"), TagId("old-tag-success"), TagId("new-tag-success"))
        Mockito
          .verify(sequenceCoordinatorService)
          .updateSequenceTag(SequenceId("id2"), TagId("old-tag-success"), TagId("new-tag-success"))
        Mockito
          .verify(sequenceCoordinatorService)
          .updateSequenceTag(SequenceId("id3"), TagId("old-tag-success"), TagId("new-tag-success"))

        tag.map(_.label) shouldEqual Some("new tag success")
      }
    }

  }

}
