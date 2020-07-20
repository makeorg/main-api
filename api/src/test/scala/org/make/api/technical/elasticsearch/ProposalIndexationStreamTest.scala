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

package org.make.api.technical.elasticsearch

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import org.make.api.MakeUnitTest
import org.make.api.operation.{OperationOfQuestionService, OperationService}
import org.make.api.organisation.OrganisationService
import org.make.api.proposal.{ProposalCoordinatorService, ProposalSearchEngine}
import org.make.api.question.QuestionService
import org.make.api.segment.SegmentService
import org.make.api.semantic.SemanticService
import org.make.api.sequence.{SequenceConfiguration, SequenceConfigurationService}
import org.make.api.tag.TagService
import org.make.api.tagtype.TagTypeService
import org.make.api.user.UserService
import org.make.core.RequestContext
import org.make.core.idea.IdeaId
import org.make.core.operation._
import org.make.core.proposal._
import org.make.core.proposal.indexed.IndexedTag
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag._
import org.make.core.user.UserId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProposalIndexationStreamTest extends MakeUnitTest with ProposalIndexationStream {
  override val segmentService: SegmentService = mock[SegmentService]
  override val tagService: TagService = mock[TagService]
  override val tagTypeService: TagTypeService = mock[TagTypeService]
  override val semanticService: SemanticService = mock[SemanticService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val organisationService: OrganisationService = mock[OrganisationService]
  override val operationService: OperationService = mock[OperationService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]
  override val userService: UserService = mock[UserService]
  override val questionService: QuestionService = mock[QuestionService]
  override lazy val actorSystem: ActorSystem = ActorSystem()

  when(userService.getUser(any[UserId])).thenAnswer { userId: UserId =>
    Future.successful(Some(user(userId)))
  }

  when(questionService.getQuestion(QuestionId("question")))
    .thenReturn(
      Future.successful(
        Some(
          Question(
            questionId = QuestionId("question"),
            slug = "question",
            question = "question",
            shortTitle = None,
            country = Country("FR"),
            language = Language("fr"),
            operationId = Some(OperationId("operation"))
          )
        )
      )
    )

  when(operationOfQuestionService.findByQuestionId(QuestionId("question")))
    .thenReturn(
      Future.successful(
        Some(operationOfQuestion(questionId = QuestionId("question"), operationId = OperationId("operation")))
      )
    )

  when(operationService.findOneSimple(any[OperationId])).thenAnswer { id: OperationId =>
    Future.successful(
      Some(
        SimpleOperation(
          operationId = id,
          status = OperationStatus.Active,
          slug = id.value,
          defaultLanguage = Language("fr"),
          allowedSources = Seq.empty,
          operationKind = OperationKind.PublicConsultation,
          createdAt = Some(ZonedDateTime.parse("2019-11-07T14:14:14.014Z")),
          updatedAt = None
        )
      )
    )
  }

  when(segmentService.resolveSegment(any[RequestContext]))
    .thenReturn(Future.successful(None))

  when(sequenceConfigurationService.getSequenceConfigurationByQuestionId(any[QuestionId]))
    .thenReturn(Future.successful(SequenceConfiguration.default))

  Feature("Get proposal") {
    val tagTypeStake =
      TagType(TagTypeId("stake"), label = "stake", display = TagTypeDisplay.Displayed, requiredForEnrichment = true)
    val tagTypes: Seq[TagType] = Seq(
      tagTypeStake,
      TagType(TagTypeId("target"), label = "target", display = TagTypeDisplay.Hidden, requiredForEnrichment = false),
      TagType(TagTypeId("actor"), label = "actor", display = TagTypeDisplay.Displayed, requiredForEnrichment = true)
    )
    val tagStake = Tag(
      tagId = TagId("stake-1"),
      label = "stake tag",
      display = TagDisplay.Inherit,
      tagTypeId = TagTypeId("stake"),
      weight = 0,
      operationId = None,
      questionId = None,
      country = Country("FR"),
      language = Language("fr")
    )
    val tagTarget = Tag(
      tagId = TagId("target-1"),
      label = "target tag",
      display = TagDisplay.Inherit,
      tagTypeId = TagTypeId("target"),
      weight = 0,
      operationId = None,
      questionId = None,
      country = Country("FR"),
      language = Language("fr")
    )
    val tagActor = Tag(
      tagId = TagId("actor-1"),
      label = "actor tag",
      display = TagDisplay.Inherit,
      tagTypeId = TagTypeId("actor"),
      weight = 0,
      operationId = None,
      questionId = None,
      country = Country("FR"),
      language = Language("fr")
    )

    Scenario("proposal without votes and multiple stake tag") {
      val id = ProposalId("proposal-without-votes")
      val tags = Seq(tagStake, tagStake.copy(tagId = TagId("stake-2")), tagActor, tagTarget)

      when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, tags = tags.map(_.tagId), idea = Some(IdeaId("idea-id"))))))

      when(tagService.findByTagIds(tags.map(_.tagId)))
        .thenReturn(Future.successful(tags))

      when(tagTypeService.findAll())
        .thenReturn(Future.successful(tagTypes))

      when(tagService.retrieveIndexedTags(tags, tagTypes))
        .thenReturn(
          Seq(
            IndexedTag(tagId = TagId("stake-1"), label = "stake tag 1", display = true),
            IndexedTag(tagId = TagId("stake-2"), label = "stake tag 2", display = true),
            IndexedTag(tagId = TagId("actor-1"), label = "actor tag", display = true)
          )
        )

      when(tagService.retrieveIndexedTags(tags.filter(_.tagTypeId == TagTypeId("stake")), Seq(tagTypeStake)))
        .thenReturn(
          Seq(
            IndexedTag(tagId = TagId("stake-1"), label = "stake tag 1", display = true),
            IndexedTag(tagId = TagId("stake-2"), label = "stake tag 2", display = true)
          )
        )

      when(
        elasticsearchProposalAPI
          .countProposals(
            SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("stake-1")))))))
          )
      ).thenReturn(Future.successful(42L))

      when(
        elasticsearchProposalAPI
          .countProposals(
            SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("stake-2")))))))
          )
      ).thenReturn(Future.successful(21L))

      whenReady(getIndexedProposal(id), Timeout(2.seconds)) { result =>
        result should be(defined)
        val proposal = result.get

        proposal.id should be(id)
        proposal.segment should be(None)
        proposal.votesCount should be(0)
        proposal.votesVerifiedCount should be(0)
        proposal.votesSequenceCount should be(0)
        proposal.votesSegmentCount should be(0)

        proposal.author.firstName should contain("Joe")
        proposal.author.organisationName should be(empty)
        proposal.author.organisationSlug should be(empty)
        proposal.author.anonymousParticipation should be(false)

        proposal.selectedStakeTag.map(_.tagId) should be(Some(tagStake.tagId))
        proposal.toEnrich shouldBe false
      }
    }

    Scenario("proposal with 1 stake tag") {
      val id = ProposalId("proposal-without-votes")

      when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, tags = Seq(tagStake.tagId), idea = Some(IdeaId("idea-id"))))))

      when(tagService.findByTagIds(Seq(tagStake.tagId)))
        .thenReturn(Future.successful(Seq(tagStake)))

      when(tagTypeService.findAll())
        .thenReturn(Future.successful(tagTypes))

      when(tagService.retrieveIndexedTags(Seq(tagStake), tagTypes))
        .thenReturn(Seq(IndexedTag(tagId = TagId("stake-1"), label = "stake tag", display = true)))

      when(tagService.retrieveIndexedTags(Seq(tagStake), Seq(tagTypeStake)))
        .thenReturn(Seq(IndexedTag(tagId = TagId("stake-1"), label = "stake tag", display = true)))

      whenReady(getIndexedProposal(id), Timeout(2.seconds)) { result =>
        result should be(defined)
        val proposal = result.get

        proposal.id should be(id)
        proposal.segment should be(None)
        proposal.votesCount should be(0)
        proposal.votesVerifiedCount should be(0)
        proposal.votesSequenceCount should be(0)
        proposal.votesSegmentCount should be(0)

        proposal.author.firstName should contain("Joe")
        proposal.author.organisationName should be(empty)
        proposal.author.organisationSlug should be(empty)
        proposal.author.anonymousParticipation should be(false)

        proposal.selectedStakeTag.map(_.tagId) should be(Some(tagStake.tagId))
        proposal.toEnrich shouldBe true
      }
    }

    Scenario("proposal with no stake tag") {
      val id = ProposalId("proposal-without-votes")

      when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, tags = Seq.empty, idea = Some(IdeaId("idea-id"))))))

      when(tagService.findByTagIds(Seq.empty))
        .thenReturn(Future.successful(Seq.empty))

      when(tagTypeService.findAll())
        .thenReturn(Future.successful(tagTypes))

      when(tagService.retrieveIndexedTags(Seq.empty, tagTypes))
        .thenReturn(Seq.empty)

      when(tagService.retrieveIndexedTags(Seq.empty, Seq(tagTypeStake)))
        .thenReturn(Seq.empty)

      whenReady(getIndexedProposal(id), Timeout(2.seconds)) { result =>
        result should be(defined)
        val proposal = result.get

        proposal.id should be(id)
        proposal.segment should be(None)
        proposal.votesCount should be(0)
        proposal.votesVerifiedCount should be(0)
        proposal.votesSequenceCount should be(0)
        proposal.votesSegmentCount should be(0)

        proposal.author.firstName should contain("Joe")
        proposal.author.organisationName should be(empty)
        proposal.author.organisationSlug should be(empty)
        proposal.author.anonymousParticipation should be(false)

        proposal.selectedStakeTag.map(_.tagId) should be(None)
        proposal.toEnrich shouldBe true
      }
    }

    Scenario("proposal with multiple stake tag with same proposals count") {
      val id = ProposalId("proposal-without-votes")

      val tags = Seq(tagStake, tagStake.copy(tagId = TagId("stake-2")), tagActor, tagTarget)
      when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, tags = tags.map(_.tagId), idea = Some(IdeaId("idea-id"))))))

      when(tagService.findByTagIds(tags.map(_.tagId)))
        .thenReturn(Future.successful(tags))

      when(tagTypeService.findAll())
        .thenReturn(Future.successful(tagTypes))

      when(tagService.retrieveIndexedTags(tags, tagTypes))
        .thenReturn(
          Seq(
            IndexedTag(tagId = TagId("stake-1"), label = "stake tag 1", display = true),
            IndexedTag(tagId = TagId("stake-2"), label = "stake tag 2", display = true),
            IndexedTag(tagId = TagId("actor-1"), label = "actor tag", display = true)
          )
        )

      when(tagService.retrieveIndexedTags(tags.filter(_.tagId.value.contains("stake")), Seq(tagTypeStake)))
        .thenReturn(
          Seq(
            IndexedTag(tagId = TagId("stake-1"), label = "stake tag 1", display = true),
            IndexedTag(tagId = TagId("stake-2"), label = "stake tag 2", display = true)
          )
        )

      when(
        elasticsearchProposalAPI
          .countProposals(
            SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("stake-1")))))))
          )
      ).thenReturn(Future.successful(42L))

      when(
        elasticsearchProposalAPI
          .countProposals(
            SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("stake-2")))))))
          )
      ).thenReturn(Future.successful(42L))

      whenReady(getIndexedProposal(id), Timeout(2.seconds)) { result =>
        result should be(defined)
        val proposal = result.get

        proposal.id should be(id)
        proposal.segment should be(None)
        proposal.votesCount should be(0)
        proposal.votesVerifiedCount should be(0)
        proposal.votesSequenceCount should be(0)
        proposal.votesSegmentCount should be(0)

        proposal.author.firstName should contain("Joe")
        proposal.author.organisationName should be(empty)
        proposal.author.organisationSlug should be(empty)
        proposal.author.anonymousParticipation should be(false)

        proposal.selectedStakeTag.map(_.tagId) should be(Some(TagId("stake-1")))
        proposal.toEnrich shouldBe false
      }
    }

    Scenario("proposal without displayed stake tag") {
      val id = ProposalId("proposal-without-votes")
      val hiddenTagStake = tagStake.copy(display = TagDisplay.Hidden)

      when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, tags = Seq(tagStake.tagId), idea = Some(IdeaId("idea-id"))))))

      when(tagService.findByTagIds(Seq(tagStake.tagId)))
        .thenReturn(Future.successful(Seq(hiddenTagStake)))

      when(tagTypeService.findAll())
        .thenReturn(Future.successful(tagTypes))

      when(tagService.retrieveIndexedTags(Seq(hiddenTagStake), tagTypes))
        .thenReturn(Seq(IndexedTag(tagId = TagId("stake-1"), label = "stake tag", display = false)))

      when(tagService.retrieveIndexedTags(Seq(hiddenTagStake), Seq(tagTypeStake)))
        .thenReturn(Seq(IndexedTag(tagId = TagId("stake-1"), label = "stake tag", display = false)))

      whenReady(getIndexedProposal(id), Timeout(2.seconds)) { result =>
        result should be(defined)
        val proposal = result.get

        proposal.id should be(id)
        proposal.segment should be(None)
        proposal.votesCount should be(0)
        proposal.votesVerifiedCount should be(0)
        proposal.votesSequenceCount should be(0)
        proposal.votesSegmentCount should be(0)

        proposal.author.firstName should contain("Joe")
        proposal.author.organisationName should be(empty)
        proposal.author.organisationSlug should be(empty)
        proposal.author.anonymousParticipation should be(false)

        proposal.selectedStakeTag.isDefined should be(false)
        proposal.toEnrich shouldBe true
      }
    }

    Scenario("fails as stake tagType doesn't exist") {
      val id = ProposalId("proposal-without-votes")
      val tagTypesWithoutStake = tagTypes.filterNot(_.tagTypeId.value == "stake")

      when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, tags = Seq.empty, idea = Some(IdeaId("idea-id"))))))

      when(tagService.findByTagIds(Seq.empty))
        .thenReturn(Future.successful(Seq.empty))

      when(tagTypeService.findAll())
        .thenReturn(Future.successful(tagTypesWithoutStake))

      when(tagService.retrieveIndexedTags(Seq.empty, tagTypesWithoutStake))
        .thenReturn(Seq.empty)

      whenReady(getIndexedProposal(id).failed, Timeout(2.seconds)) { exception =>
        exception shouldBe a[IllegalStateException]
        exception.getMessage shouldBe "Unable to find stake tag types"
      }
    }

    Scenario("anonymous participation") {
      val id = ProposalId("anonymous-participation")
      val author = UserId("anonymous-participation-author")
      val tags = Seq(tagStake, tagActor)

      when(proposalCoordinatorService.getProposal(id))
        .thenReturn(
          Future
            .successful(Some(proposal(id, author = author, tags = tags.map(_.tagId), idea = Some(IdeaId("idea-id")))))
        )

      when(tagService.findByTagIds(tags.map(_.tagId)))
        .thenReturn(Future.successful(tags))

      when(tagTypeService.findAll())
        .thenReturn(Future.successful(tagTypes))

      when(userService.getUser(author))
        .thenReturn(Future.successful(Some(user(id = author, anonymousParticipation = true))))

      whenReady(getIndexedProposal(id), Timeout(2.seconds)) { result =>
        result should be(defined)
        val proposal = result.get

        proposal.author.firstName should contain("Joe")
        proposal.author.organisationName should be(empty)
        proposal.author.organisationSlug should be(empty)
        proposal.author.anonymousParticipation should be(true)

        proposal.toEnrich shouldBe false
      }
    }

    Scenario("segmented proposal") {
      val id = ProposalId("segmented proposal")
      val requestContext = RequestContext.empty.copy(customData = Map("segmented" -> "true"))
      val tags = Seq(tagStake, tagActor)

      when(proposalCoordinatorService.getProposal(id))
        .thenReturn(
          Future
            .successful(
              Some(
                proposal(id, requestContext = requestContext, tags = tags.map(_.tagId), idea = Some(IdeaId("idea-id")))
              )
            )
        )

      when(tagService.findByTagIds(tags.map(_.tagId)))
        .thenReturn(Future.successful(tags))

      when(tagTypeService.findAll())
        .thenReturn(Future.successful(tagTypes))

      when(segmentService.resolveSegment(requestContext))
        .thenReturn(Future.successful(Some("segment")))

      whenReady(getIndexedProposal(id), Timeout(2.seconds)) { result =>
        result should be(defined)
        val proposal = result.get
        proposal.segment should contain("segment")
      }

    }

  }
}
