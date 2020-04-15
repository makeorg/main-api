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

import org.make.api.MakeUnitTest
import org.make.api.operation.{OperationOfQuestionService, OperationService}
import org.make.api.organisation.OrganisationService
import org.make.api.proposal.{ProposalCoordinatorService, ProposalSearchEngine}
import org.make.api.question.QuestionService
import org.make.api.segment.SegmentService
import org.make.api.semantic.SemanticService
import org.make.api.sequence.{SequenceConfiguration, SequenceConfigurationService}
import org.make.api.tag.TagService
import org.make.api.user.UserService
import org.make.core.RequestContext
import org.make.core.operation._
import org.make.core.proposal._
import org.make.core.proposal.indexed.IndexedTag
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ProposalIndexationStreamTest extends MakeUnitTest with ProposalIndexationStream {
  override val segmentService: SegmentService = mock[SegmentService]
  override val tagService: TagService = mock[TagService]
  override val semanticService: SemanticService = mock[SemanticService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val organisationService: OrganisationService = mock[OrganisationService]
  override val operationService: OperationService = mock[OperationService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]
  override val userService: UserService = mock[UserService]
  override val questionService: QuestionService = mock[QuestionService]

  private val emptySequenceConfiguration = SequenceCardsConfiguration(
    IntroCard(enabled = false, title = None, description = None),
    PushProposalCard(false),
    SignUpCard(enabled = false, title = None, nextCtaText = None),
    FinalCard(
      enabled = false,
      sharingEnabled = false,
      title = None,
      shareDescription = None,
      learnMoreTitle = None,
      learnMoreTextButton = None,
      linkUrl = None
    )
  )

  Mockito
    .when(userService.getUser(ArgumentMatchers.any[UserId]))
    .thenAnswer(invocation => Future.successful(Some(user(invocation.getArgument[UserId](0)))))

  Mockito
    .when(questionService.getQuestion(QuestionId("question")))
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

  Mockito
    .when(operationOfQuestionService.findByQuestionId(QuestionId("question")))
    .thenReturn(
      Future.successful(
        Some(
          OperationOfQuestion(
            questionId = QuestionId("question"),
            operationId = OperationId("operation"),
            startDate = None,
            endDate = None,
            operationTitle = "operation title",
            landingSequenceId = SequenceId("sequence"),
            canPropose = true,
            sequenceCardsConfiguration = emptySequenceConfiguration,
            aboutUrl = None,
            metas = Metas(None, None, None),
            theme = QuestionTheme("", "", "", "", None, None),
            description = "description",
            consultationImage = None,
            descriptionImage = None,
            displayResults = false
          )
        )
      )
    )

  Mockito
    .when(operationService.findOneSimple(ArgumentMatchers.any[OperationId]))
    .thenAnswer(
      invocation =>
        Future.successful(
          Some(
            SimpleOperation(
              operationId = invocation.getArgument[OperationId](0),
              status = OperationStatus.Active,
              slug = invocation.getArgument[OperationId](0).value,
              defaultLanguage = Language("fr"),
              allowedSources = Seq.empty,
              operationKind = OperationKind.PublicConsultation,
              createdAt = Some(ZonedDateTime.parse("2019-11-07T14:14:14.014Z")),
              updatedAt = None
            )
          )
      )
    )

  Mockito
    .when(segmentService.resolveSegment(ArgumentMatchers.any[RequestContext]))
    .thenReturn(Future.successful(None))

  Mockito
    .when(sequenceConfigurationService.getSequenceConfigurationByQuestionId(ArgumentMatchers.any[QuestionId]))
    .thenReturn(Future.successful(SequenceConfiguration.default))

  feature("Get proposal") {
    scenario("proposal without votes and multiple stake tag") {
      val id = ProposalId("proposal-without-votes")

      Mockito
        .when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, tags = Seq(TagId("tag-1"), TagId("tag-2"))))))

      Mockito
        .when(tagService.retrieveIndexedTags(Seq(TagId("tag-1"), TagId("tag-2"))))
        .thenReturn(
          Future.successful(
            Some(
              Seq(
                IndexedTag(tagId = TagId("tag-1"), label = "tag 1", display = true),
                IndexedTag(tagId = TagId("tag-2"), label = "tag 2", display = true)
              )
            )
          )
        )

      Mockito
        .when(tagService.retrieveIndexedStakeTags(Seq(TagId("tag-1"), TagId("tag-2"))))
        .thenReturn(
          Future.successful(
            Seq(
              IndexedTag(tagId = TagId("tag-1"), label = "tag 1", display = true),
              IndexedTag(tagId = TagId("tag-2"), label = "tag 2", display = true)
            )
          )
        )

      Mockito
        .when(
          elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("tag-1")))))))
            )
        )
        .thenReturn(Future.successful(42))

      Mockito
        .when(
          elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("tag-2")))))))
            )
        )
        .thenReturn(Future.successful(21))

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

        proposal.selectedStakeTag.map(_.tagId) should be(Some(TagId("tag-1")))
      }
    }

    scenario("proposal with 1 stake tag") {
      val id = ProposalId("proposal-without-votes")

      Mockito
        .when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, tags = Seq(TagId("tag-1"))))))

      Mockito
        .when(tagService.retrieveIndexedTags(Seq(TagId("tag-1"))))
        .thenReturn(Future.successful(Some(Seq(IndexedTag(tagId = TagId("tag-1"), label = "tag 1", display = true)))))

      Mockito
        .when(tagService.retrieveIndexedStakeTags(Seq(TagId("tag-1"))))
        .thenReturn(Future.successful(Seq(IndexedTag(tagId = TagId("tag-1"), label = "tag 1", display = true))))

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

        proposal.selectedStakeTag.map(_.tagId) should be(Some(TagId("tag-1")))
      }
    }

    scenario("proposal with no stake tag") {
      val id = ProposalId("proposal-without-votes")

      Mockito
        .when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, tags = Seq.empty))))

      Mockito
        .when(tagService.retrieveIndexedTags(Seq.empty))
        .thenReturn(Future.successful(Some(Seq.empty)))

      Mockito
        .when(tagService.retrieveIndexedStakeTags(Seq.empty))
        .thenReturn(Future.successful(Seq.empty))

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
      }
    }

    scenario("proposal with multiple stake tag with same proposals count") {
      val id = ProposalId("proposal-without-votes")

      Mockito
        .when(proposalCoordinatorService.getProposal(id))
        .thenReturn(
          Future.successful(
            Some(
              proposal(
                id,
                tags =
                  Seq(TagId("Hôpitaux"), TagId("Sécurité"), TagId("Transports"), TagId("déchets"), TagId("solidarité"))
              )
            )
          )
        )

      Mockito
        .when(
          tagService.retrieveIndexedTags(
            Seq(TagId("Hôpitaux"), TagId("Sécurité"), TagId("Transports"), TagId("déchets"), TagId("solidarité"))
          )
        )
        .thenReturn(
          Future.successful(
            Some(
              Seq(
                IndexedTag(tagId = TagId("Hôpitaux"), label = "Hôpitaux", display = true),
                IndexedTag(tagId = TagId("Sécurité"), label = "Sécurité", display = true),
                IndexedTag(tagId = TagId("Transports"), label = "Transports", display = true),
                IndexedTag(tagId = TagId("déchets"), label = "déchets", display = true),
                IndexedTag(tagId = TagId("solidarité"), label = "solidarité", display = true)
              )
            )
          )
        )

      Mockito
        .when(
          tagService.retrieveIndexedStakeTags(
            Seq(TagId("Hôpitaux"), TagId("Sécurité"), TagId("Transports"), TagId("déchets"), TagId("solidarité"))
          )
        )
        .thenReturn(
          Future.successful(
            Seq(
              IndexedTag(tagId = TagId("Hôpitaux"), label = "Hôpitaux", display = true),
              IndexedTag(tagId = TagId("Sécurité"), label = "Sécurité", display = true),
              IndexedTag(tagId = TagId("Transports"), label = "Transports", display = true),
              IndexedTag(tagId = TagId("déchets"), label = "déchets", display = true),
              IndexedTag(tagId = TagId("solidarité"), label = "solidarité", display = true)
            )
          )
        )

      Mockito
        .when(
          elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("Hôpitaux")))))))
            )
        )
        .thenReturn(Future.successful(16))

      Mockito
        .when(
          elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("Sécurité")))))))
            )
        )
        .thenReturn(Future.successful(42))

      Mockito
        .when(
          elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("Transports")))))))
            )
        )
        .thenReturn(Future.successful(21))

      Mockito
        .when(
          elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("déchets")))))))
            )
        )
        .thenReturn(Future.successful(42))

      Mockito
        .when(
          elasticsearchProposalAPI
            .countProposals(
              SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(TagId("solidarité")))))))
            )
        )
        .thenReturn(Future.successful(21))

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

        proposal.selectedStakeTag.map(_.tagId) should be(Some(TagId("Sécurité")))
      }
    }

    scenario("anonymous participation") {
      val id = ProposalId("anonymous-participation")
      val author = UserId("anonymous-participation-author")

      Mockito
        .when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, author = author, tags = Seq(TagId("tag-1"), TagId("tag-2"))))))

      Mockito
        .when(userService.getUser(author))
        .thenReturn(Future.successful(Some(user(id = author, anonymousParticipation = true))))

      whenReady(getIndexedProposal(id), Timeout(2.seconds)) { result =>
        result should be(defined)
        val proposal = result.get

        proposal.author.firstName should contain("Joe")
        proposal.author.organisationName should be(empty)
        proposal.author.organisationSlug should be(empty)
        proposal.author.anonymousParticipation should be(true)

      }
    }

    scenario("segmented proposal") {
      val id = ProposalId("segmented proposal")
      val requestContext = RequestContext.empty.copy(customData = Map("segmented" -> "true"))

      Mockito
        .when(proposalCoordinatorService.getProposal(id))
        .thenReturn(
          Future
            .successful(Some(proposal(id, requestContext = requestContext, tags = Seq(TagId("tag-1"), TagId("tag-2")))))
        )

      Mockito
        .when(segmentService.resolveSegment(requestContext))
        .thenReturn(Future.successful(Some("segment")))

      whenReady(getIndexedProposal(id), Timeout(2.seconds)) { result =>
        result should be(defined)
        val proposal = result.get
        proposal.segment should contain("segment")
      }

    }

  }
}
