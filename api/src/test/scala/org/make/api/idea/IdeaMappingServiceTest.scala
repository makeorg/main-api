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
import org.make.api.proposal._
import org.make.api.question.{PersistentQuestionService, PersistentQuestionServiceComponent}
import org.make.api.tag.{PersistentTagService, PersistentTagServiceComponent, TagService, TagServiceComponent}
import org.make.api.tagtype.{PersistentTagTypeService, PersistentTagTypeServiceComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag._
import org.make.core.user.{UserId, UserType}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
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
    with ProposalServiceComponent
    with ProposalSearchEngineComponent
    with PersistentQuestionServiceComponent
    with PersistentTagServiceComponent
    with PersistentTagTypeServiceComponent
    with IdGeneratorComponent {

  override val persistentIdeaMappingService: PersistentIdeaMappingService = mock[PersistentIdeaMappingService]
  override val persistentIdeaService: PersistentIdeaService = mock[PersistentIdeaService]
  override val tagService: TagService = mock[TagService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]
  override val persistentTagService: PersistentTagService = mock[PersistentTagService]
  override val persistentTagTypeService: PersistentTagTypeService = mock[PersistentTagTypeService]
  override val idGenerator: IdGenerator = mock[IdGenerator]

  def createTag(tagId: TagId, label: String): Tag = Tag(
    tagId = tagId,
    label = label,
    display = TagDisplay.Inherit,
    tagTypeId = TagTypeId("some-id"),
    weight = 0.0F,
    operationId = None,
    questionId = Some(QuestionId("my-question")),
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
          .changeIdea(
            adminId = UserId("admin-id"),
            ideaMappingId = IdeaMappingId("unknown"),
            newIdea = IdeaId("some-id"),
            migrateProposals = false
          ),
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
          .changeIdea(
            adminId = UserId("admin-id"),
            ideaMappingId = IdeaMappingId("changeIdea"),
            newIdea = IdeaId("new-id"),
            migrateProposals = false
          ),
        Timeout(5.seconds)
      ) { maybeMapping =>
        maybeMapping.map(_.ideaId) should be(Some(IdeaId("new-id")))

      }
    }

    scenario("migrating proposal") {

      def proposal(proposalId: String, ideaId: String, tags: Seq[String]): IndexedProposal =
        IndexedProposal(
          id = ProposalId(proposalId),
          userId = UserId("random-user-id"),
          content = "random content",
          slug = "random-slug",
          status = ProposalStatus.Accepted,
          createdAt = DateHelper.now(),
          updatedAt = Some(DateHelper.now()),
          votes = Seq.empty,
          votesCount = 0,
          votesVerifiedCount = 0,
          votesSequenceCount = 0,
          votesSegmentCount = 0,
          toEnrich = false,
          scores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
          segmentScores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
          context = None,
          trending = None,
          labels = Seq.empty,
          author = IndexedAuthor(
            firstName = None,
            displayName = None,
            organisationName = None,
            organisationSlug = None,
            postalCode = None,
            age = None,
            avatarUrl = None,
            anonymousParticipation = false,
            userType = UserType.UserTypeUser
          ),
          organisations = Seq.empty,
          country = Country("FR"),
          language = Language("fr"),
          question = None,
          tags = tags.map(tagId => IndexedTag(TagId(tagId), tagId, display = true)),
          selectedStakeTag = None,
          ideaId = Some(IdeaId(ideaId)),
          operationId = None,
          sequencePool = SequencePool.New,
          sequenceSegmentPool = SequencePool.New,
          initialProposal = false,
          refusalReason = None,
          operationKind = None,
          segment = None
        )

      def tag(tagId: String, tagTypeId: String, weight: Float): Tag =
        Tag(
          tagId = TagId(tagId),
          label = tagId,
          display = TagDisplay.Displayed,
          tagTypeId = TagTypeId(tagTypeId),
          weight = weight,
          operationId = None,
          questionId = None,
          country = Country("Fr"),
          language = Language("fr")
        )

      when(persistentIdeaMappingService.get(IdeaMappingId("changeIdea")))
        .thenReturn(
          Future.successful(
            Some(
              IdeaMapping(
                IdeaMappingId("changeIdea"),
                QuestionId("question"),
                Some(TagId("stake-id")),
                Some(TagId("solution-id")),
                IdeaId("original-idea")
              )
            )
          )
        )

      when(persistentTagTypeService.findAll())
        .thenReturn(
          Future.successful(
            Seq(
              TagType(TagTypeId("stake-type-id"), "Stake", TagTypeDisplay.Displayed, 70),
              TagType(TagTypeId("solution-type-id"), "Solution type", TagTypeDisplay.Displayed, 50),
              TagType(TagTypeId("other-type-id"), "Other", TagTypeDisplay.Displayed, 30)
            )
          )
        )

      when(
        elasticsearchProposalAPI.searchProposals(
          SearchQuery(
            filters = Some(
              SearchFilters(
                idea = Some(IdeaSearchFilter(Seq(IdeaId("original-idea")))),
                tags = Some(TagsSearchFilter(Seq(TagId("stake-id"), TagId("solution-id"))))
              )
            )
          )
        )
      ).thenReturn(
        Future.successful(
          ProposalsSearchResult(
            total = 4,
            results = Seq(
              proposal("proposal1", "original-idea", Seq("stake-id", "solution-id", "other-tag")),
              proposal(
                "proposal2",
                "original-idea",
                Seq("stake-id", "solution-id", "solution-id-heavier", "other-tag")
              ),
              proposal("proposal4", "original-idea", Seq("stake-id", "solution-id", "stake-id-heavier", "other-tag"))
            )
          )
        )
      )

      when(
        persistentTagService
          .findAllFromIds(any[Seq[TagId]])
      ).thenReturn(
        Future.successful(
          Seq(
            tag("stake-id", "stake-type-id", 100),
            tag("solution-id", "solution-type-id", 100),
            tag("solution-id-heavier", "solution-type-id", 200),
            tag("stake-id-heavier", "stake-type-id", 200),
            tag("other-tag", "other-type-id", 100)
          )
        )
      )

      when(
        proposalService.patchProposal(
          ProposalId("proposal1"),
          UserId("admin-id"),
          RequestContext.empty,
          PatchProposalRequest(ideaId = Some(IdeaId("new-id")))
        )
      ).thenReturn(Future.successful(None))

      whenReady(
        ideaMappingService
          .changeIdea(
            adminId = UserId("admin-id"),
            ideaMappingId = IdeaMappingId("changeIdea"),
            newIdea = IdeaId("new-id"),
            migrateProposals = true
          ),
        Timeout(5.seconds)
      ) { maybeMapping =>
        maybeMapping.map(_.ideaId) should be(Some(IdeaId("new-id")))
      }

      Mockito
        .verify(proposalService)
        .patchProposal(
          ProposalId("proposal1"),
          UserId("admin-id"),
          RequestContext.empty,
          PatchProposalRequest(ideaId = Some(IdeaId("new-id")))
        )

    }
  }

  feature("getOrCreateMapping") {
    scenario("existing mapping") {
      when(
        persistentIdeaMappingService
          .find(
            start = 0,
            end = None,
            sort = None,
            order = None,
            questionId = Some(QuestionId("my-question")),
            stakeTagId = Some(Right(TagId("tag-1"))),
            solutionTypeTagId = Some(Right(TagId("tag-2"))),
            ideaId = None
          )
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
          .find(
            start = 0,
            end = None,
            sort = None,
            order = None,
            questionId = Some(QuestionId("my-question")),
            stakeTagId = Some(Right(TagId("tag-3"))),
            solutionTypeTagId = Some(Right(TagId("tag-4"))),
            ideaId = None
          )
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
          .find(
            start = 0,
            end = None,
            sort = None,
            order = None,
            questionId = Some(QuestionId("my-question")),
            stakeTagId = Some(Right(TagId("tag-5"))),
            solutionTypeTagId = Some(Right(TagId("tag-6"))),
            ideaId = None
          )
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
              operationId = None
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
          .find(
            start = 0,
            end = None,
            sort = None,
            order = None,
            questionId = Some(QuestionId("my-question")),
            stakeTagId = Some(Left(None)),
            solutionTypeTagId = Some(Left(None)),
            ideaId = None
          )
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
              operationId = None
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

  feature("count mappings") {
    scenario("count mappings") {
      when(
        persistentIdeaMappingService
          .count(Some(QuestionId("my-question")), Some(Right(TagId("tag-1"))), Some(Right(TagId("tag-2"))), None)
      ).thenReturn(Future.successful(42))

      val countMapping =
        ideaMappingService.count(
          Some(QuestionId("my-question")),
          Some(Right(TagId("tag-1"))),
          Some(Right(TagId("tag-2"))),
          None
        )

      whenReady(countMapping, Timeout(5.seconds)) {
        _ shouldBe 42
      }

    }
  }

}
