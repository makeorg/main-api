package org.make.api.tag

import akka.actor.{ActorRef, ActorSystem}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.proposal._
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.technical.{DefaultIdGeneratorComponent, ReadJournalComponent}
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.api.{ActorSystemComponent, MakeUnitTest}
import org.make.core.RequestContext
import org.make.core.operation.{Operation, OperationCountryConfiguration, OperationId, OperationStatus}
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.reference.{Theme, ThemeId}
import org.make.core.sequence.SequenceId
import org.make.core.tag._
import org.make.core.user.UserId
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.Future

class TagMigrationServiceTest
    extends MakeUnitTest
    with DefaultTagMigrationServiceComponent
    with TagServiceComponent
    with PersistentTagServiceComponent
    with OperationServiceComponent
    with ThemeServiceComponent
    with ProposalCoordinatorServiceComponent
    with ProposalCoordinatorComponent
    with DefaultIdGeneratorComponent
    with ReadJournalComponent
    with ActorSystemComponent {
  override val actorSystem: ActorSystem = ActorSystem()

  override val operationService: OperationService = mock[OperationService]
  override val persistentTagService: PersistentTagService = mock[PersistentTagService]
  override val proposalCoordinator: ActorRef = mock[ActorRef]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val readJournal: MakeReadJournal = mock[MakeReadJournal]
  override val tagService: TagService = mock[TagService]
  override val themeService: ThemeService = mock[ThemeService]

  val mainUserId: UserId = UserId("11111111-1111-1111-1111-111111111111")

  def newProposal(proposalId: ProposalId,
                  tags: Seq[TagId],
                  operation: Option[OperationId] = None,
                  theme: Option[ThemeId] = None): Proposal =
    Proposal(
      proposalId = proposalId,
      slug = "a-proposal-slug",
      content = "a proposal content",
      author = mainUserId,
      labels = Seq.empty,
      theme = theme,
      operation = operation,
      tags = tags,
      votes = Seq.empty,
      events = List.empty,
      creationContext = RequestContext.empty,
      createdAt = None,
      updatedAt = None
    )

  def legacyTag(tagId: TagId, label: String): Tag =
    Tag(
      tagId = tagId,
      label = label,
      display = TagDisplay.Inherit,
      weight = 0f,
      tagTypeId = TagType.LEGACY.tagTypeId,
      operationId = None,
      themeId = None,
      country = "FR",
      language = "fr"
    )

  feature("create PatchProposal command") {
    scenario("proposal with tags in a theme") {
      Given("a proposal with tags and a theme")
      val themeId = ThemeId("theme")
      val oldTag1 = TagId("old-tag-1")
      val oldTag2 = TagId("old-tag-2")
      val newTag1 = TagId("new-tag-1")
      val newTag2 = TagId("new-tag-2")

      val proposal = newProposal(ProposalId("proposal-id"), Seq(oldTag1, oldTag2), theme = Some(themeId))
      And("a relation between old tags and new tags in that theme")
      val tagsJoin = Seq(ThemeJoinTagIds(themeId, newTag1, oldTag1), ThemeJoinTagIds(themeId, newTag2, oldTag2))

      When("we create the PatchProposal command")
      val command: PatchProposalCommand =
        tagMigrationService.createPatchProposalCommand(proposal, tagsJoin, Seq.empty, mainUserId, RequestContext.empty)

      Then("command patches proposal with new tags")
      command.changes.tags.isDefined should be(true)
      val patchedTags = command.changes.tags.get
      patchedTags should be(Seq(newTag1, newTag2))
    }
  }

  feature("create new tags") {
    val ACTOR = TagTypeId("982e6860-eb66-407e-bafb-461c2d927478")

    val aTag = Tag(
      tagId = idGenerator.nextTagId(),
      label = "a tag",
      display = TagDisplay.Inherit,
      weight = 0f,
      tagTypeId = TagType.LEGACY.tagTypeId,
      operationId = None,
      themeId = None,
      country = "GB",
      language = "en"
    )

    val actionTag = Tag(
      tagId = idGenerator.nextTagId(),
      label = "action : tag",
      display = TagDisplay.Inherit,
      weight = 0f,
      tagTypeId = ACTOR,
      operationId = None,
      themeId = None,
      country = "GB",
      language = "en"
    )

    scenario("from theme") {
      Given("a theme with some legacy tags")
      val theme = Theme(
        themeId = ThemeId("a-theme"),
        translations = Seq.empty,
        actionsCount = 0,
        proposalsCount = 0,
        votesCount = 0,
        country = "GB",
        color = "#0ff",
        gradient = None,
        tags = Seq(legacyTag(TagId("a-tag"), aTag.label), legacyTag(TagId("action--tag"), actionTag.label))
      )
      Mockito.when(themeService.findAll()).thenReturn(Future.successful(Seq(theme)))
      Mockito
        .when(
          tagService
            .createTag(
              ArgumentMatchers.eq("a tag"),
              ArgumentMatchers.eq(TagType.LEGACY.tagTypeId),
              ArgumentMatchers.eq(None),
              ArgumentMatchers.eq(Some(theme.themeId)),
              ArgumentMatchers.eq("GB"),
              ArgumentMatchers.eq("en"),
              ArgumentMatchers.eq(TagDisplay.Inherit),
              ArgumentMatchers.eq(0f)
            )
        )
        .thenReturn(Future.successful(aTag.copy(themeId = Some(theme.themeId))))
      Mockito
        .when(
          tagService
            .createTag(
              ArgumentMatchers.eq("action : tag"),
              ArgumentMatchers.eq(ACTOR),
              ArgumentMatchers.eq(None),
              ArgumentMatchers.eq(Some(theme.themeId)),
              ArgumentMatchers.eq("GB"),
              ArgumentMatchers.eq("en"),
              ArgumentMatchers.eq(TagDisplay.Inherit),
              ArgumentMatchers.eq(0f)
            )
        )
        .thenReturn(Future.successful(actionTag.copy(themeId = Some(theme.themeId))))

      When("I create new tags from this theme")
      whenReady(tagMigrationService.createTagsByTheme) { tagsByTheme =>
        Then("I must have the new tag ids with their related old tag ids in this theme")
        tagsByTheme.size should be(2)
        tagsByTheme.forall(_.themeId == theme.themeId) should be(true)
        tagsByTheme.exists(_.slug == TagId("a-tag")) should be(true)
        tagsByTheme.exists(_.slug == TagId("action--tag")) should be(true)
        tagsByTheme.find(_.slug == TagId("a-tag")).get.tagId.value.length should be(36)
        tagsByTheme.find(_.slug == TagId("action--tag")).get.tagId.value.length should be(36)
      }

    }

    scenario("from operation") {
      Given("a operation with some legacy tags")
      val operation = Operation(
        OperationStatus.Active,
        OperationId("a-operation"),
        "a-operation",
        Seq.empty,
        "en",
        List.empty,
        None,
        None,
        Seq(
          OperationCountryConfiguration(
            "GB",
            Seq(TagId("a-tag"), TagId("action--tag")),
            SequenceId("a-sequence"),
            None,
            None
          )
        )
      )
      Mockito
        .when(tagService.findAll())
        .thenReturn(
          Future
            .successful(Seq(legacyTag(TagId("a-tag"), aTag.label), legacyTag(TagId("action--tag"), actionTag.label)))
        )
      Mockito.when(operationService.find()).thenReturn(Future.successful(Seq(operation)))
      Mockito
        .when(
          tagService
            .createTag(
              ArgumentMatchers.eq("a tag"),
              ArgumentMatchers.eq(TagType.LEGACY.tagTypeId),
              ArgumentMatchers.eq(Some(operation.operationId)),
              ArgumentMatchers.eq(None),
              ArgumentMatchers.eq("GB"),
              ArgumentMatchers.eq("en"),
              ArgumentMatchers.eq(TagDisplay.Inherit),
              ArgumentMatchers.eq(0f)
            )
        )
        .thenReturn(Future.successful(aTag.copy(operationId = Some(operation.operationId))))
      Mockito
        .when(
          tagService
            .createTag(
              ArgumentMatchers.eq("action : tag"),
              ArgumentMatchers.eq(ACTOR),
              ArgumentMatchers.eq(Some(operation.operationId)),
              ArgumentMatchers.eq(None),
              ArgumentMatchers.eq("GB"),
              ArgumentMatchers.eq("en"),
              ArgumentMatchers.eq(TagDisplay.Inherit),
              ArgumentMatchers.eq(0f)
            )
        )
        .thenReturn(Future.successful(actionTag.copy(operationId = Some(operation.operationId))))

      When("I create new tags from this operation")
      whenReady(tagMigrationService.createTagsByOperation) { tagsByOperation =>
        Then("I must have the new tag ids with their related old tag ids in this operation")
        tagsByOperation.size should be(2)
        tagsByOperation.forall(_.operationId == operation.operationId) should be(true)
        tagsByOperation.exists(_.slug == TagId("a-tag")) should be(true)
        tagsByOperation.exists(_.slug == TagId("action--tag")) should be(true)
        tagsByOperation.find(_.slug == TagId("a-tag")).get.tagId.value.length should be(36)
        tagsByOperation.find(_.slug == TagId("action--tag")).get.tagId.value.length should be(36)
      }

    }

  }

}
