package org.make.api.tag

import java.sql.SQLIntegrityConstraintViolationException

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import org.make.api.MakeUnitTest
import org.make.api.proposal.ProposalCoordinatorService
import org.make.api.sequence.SequenceCoordinatorService
import org.make.api.tag.TagExceptions.TagAlreadyExistsException
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.technical.{EventBusService, EventBusServiceComponent}
import org.make.api.userhistory.UserEvent.UserUpdatedTagEvent
import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagId}
import org.make.core.user.UserId
import org.mockito.{ArgumentMatcher, ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TagServiceTest
    extends MakeUnitTest
    with DefaultTagServiceComponent
    with PersistentTagServiceComponent
    with EventBusServiceComponent {

  override val persistentTagService: PersistentTagService = mock[PersistentTagService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val actorSystem: ActorSystem = ActorSystem()
  override val sequenceCoordinatorService: SequenceCoordinatorService = mock[SequenceCoordinatorService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val readJournal: MakeReadJournal = mock[MakeReadJournal]

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
    scenario("creating an existing tag throws an exception") {
      Given("an existing tag 'existing tag'")
      When("i create a tag 'existing tag'")
      Then("an exception TagAlreadyExistsException is thrown")

      Mockito
        .when(persistentTagService.get(TagId("existing-tag")))
        .thenReturn(Future.successful(Option(Tag("existing-tag"))))

      val futureTag: Future[Tag] = tagService.createTag("existing-tag")
      whenReady(futureTag.failed, Timeout(3.seconds)) { e =>
        e shouldBe a[TagAlreadyExistsException]
      }
    }

    scenario("creating a tag success") {
      When("i create a tag with label 'new tag'")
      Then("my tag is persisted")

      Mockito
        .when(persistentTagService.get(TagId("new-tag")))
        .thenReturn(Future.successful(None))

      val tag = Tag(TagId("new-tag"), "new tag")

      Mockito
        .when(persistentTagService.persist(tag))
        .thenReturn(Future.successful(tag))

      val newTag: Future[Tag] = tagService.createTag("new tag")

      whenReady(newTag, Timeout(3.seconds)) { _ =>
        Mockito.verify(persistentTagService).persist(tag)
      }

    }
  }

  feature("find tags") {
    scenario("find enabled tags with ids 'find-tag1' and 'find-tag2'") {
      Given("a list of registered enabled tags 'find tag1', 'find tag2'")
      When("i find enabled tags")
      Then("persistent service findAllEnabledFromIds is called")
      tagService.findEnabledByTagIds(Seq(TagId("find-tag1"), TagId("find-tag2")))

      Mockito.verify(persistentTagService).findAllEnabledFromIds(Seq(TagId("find-tag1"), TagId("find-tag2")))
    }

    scenario("find all enabled tags") {
      Given("a list of registered enabled tags 'find tag1', 'find tag2'")
      When("i find all enabled tags")
      Then("persistent service findAllEnabled is called")
      tagService.findAllEnabled()

      Mockito.verify(persistentTagService).findAllEnabled()
    }

    scenario("find all tags") {
      Given("a list of registered tags 'find tag1', 'find tag2'")
      When("i find all tags")
      Then("persistent service findAll is called")
      tagService.findAll()

      Mockito.verify(persistentTagService).findAll()
    }

    scenario("find tags with ids 'find-tag1' and 'find-tag2'") {
      Given("a list of registered tags 'find tag1', 'find tag2', 'find tag3'")
      When("i find tags with ids 'find-tag1' and 'find-tag2'")
      Then("persistent service findAll is called")
      And("i get tags 'find tag1' and 'find tag2'")

      Mockito.reset(persistentTagService)
      Mockito
        .when(persistentTagService.findAll())
        .thenReturn(Future.successful(Seq(Tag("find tag1"), Tag("find tag2"), Tag("find tag3"))))

      val futureTags: Future[Seq[Tag]] = tagService.findByTagIds(Seq(TagId("find-tag1"), TagId("find-tag2")))

      whenReady(futureTags, Timeout(3.seconds)) { tags =>
        tags.size shouldBe 2
        tags.contains(Tag("find tag1")) shouldBe true
        tags.contains(Tag("find tag2")) shouldBe true
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

    scenario("update to an existed tag ") {
      When("i update a tag to an existed tag")
      Then("a get a None value")

      Mockito.reset(eventBusService)
      Mockito
        .when(persistentTagService.get(TagId("existed-old-tag")))
        .thenReturn(Future.successful(Some(Tag("existed old tag"))))
      Mockito
        .when(persistentTagService.persist(Tag("existed tag")))
        .thenReturn(Future.failed(new SQLIntegrityConstraintViolationException))

      val futureTag: Future[Option[Tag]] = tagService.updateTag(
        slug = TagId("existed-old-tag"),
        newTagLabel = "existed tag",
        connectedUserId = Some(UserId("bob")),
        requestContext = RequestContext.empty
      )

      whenReady(futureTag.failed, Timeout(3.seconds)) { e =>
        e shouldBe a[SQLIntegrityConstraintViolationException]
      }
    }

    scenario("update a tag success") {
      When("i update a tag 'old tag success' to 'new tag success'")
      Then("a get a None value")

      Mockito.reset(eventBusService)
      Mockito
        .when(persistentTagService.get(TagId("old-tag-success")))
        .thenReturn(Future.successful(Some(Tag("old tag success"))))
      Mockito
        .when(persistentTagService.persist(Tag("new tag success")))
        .thenReturn(Future.successful(Tag("new tag success")))
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

        tag shouldEqual Some(Tag("new tag success"))
      }
    }

  }

}
