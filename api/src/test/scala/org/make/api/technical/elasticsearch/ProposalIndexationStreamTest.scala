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
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.QualificationKey.{
  DoNotCare,
  DoNotUnderstand,
  Doable,
  Impossible,
  LikeIt,
  NoOpinion,
  NoWay,
  PlatitudeAgree,
  PlatitudeDisagree
}
import org.make.core.proposal.VoteKey.{Agree, Disagree, Neutral}
import org.make.core.proposal._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{User, UserId}
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

  private val defaultVotes: Seq[Vote] = Seq(
    Vote(
      key = Agree,
      count = 0,
      countVerified = 0,
      countSegment = 0,
      countSequence = 0,
      qualifications = Seq(
        Qualification(key = LikeIt, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = Doable, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = PlatitudeAgree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
      )
    ),
    Vote(
      key = Disagree,
      count = 0,
      countVerified = 0,
      countSegment = 0,
      countSequence = 0,
      qualifications = Seq(
        Qualification(key = NoWay, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = Impossible, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = PlatitudeDisagree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
      )
    ),
    Vote(
      key = Neutral,
      count = 0,
      countVerified = 0,
      countSegment = 0,
      countSequence = 0,
      qualifications = Seq(
        Qualification(key = NoOpinion, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = DoNotUnderstand, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = DoNotCare, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
      )
    ),
  )

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

  def proposal(id: ProposalId,
               votes: Seq[Vote] = defaultVotes,
               author: UserId = UserId("author"),
               tags: Seq[TagId] = Seq.empty,
               organisations: Seq[OrganisationInfo] = Seq.empty,
               questionId: QuestionId = QuestionId("question"),
               operationId: OperationId = OperationId("operation"),
               requestContext: RequestContext = RequestContext.empty): Proposal = {
    Proposal(
      proposalId = id,
      votes = votes,
      content = "Il faut tester l'indexation des propositions",
      slug = "il-faut-tester-l-indexation-des-propositions",
      author = author,
      labels = Seq.empty,
      theme = None,
      status = Accepted,
      refusalReason = None,
      tags = tags,
      organisations = organisations,
      organisationIds = organisations.map(_.organisationId),
      language = Some(Language("fr")),
      country = Some(Country("FR")),
      questionId = Some(questionId),
      creationContext = requestContext,
      idea = None,
      operation = Some(operationId),
      createdAt = Some(ZonedDateTime.parse("2019-10-10T10:10:10.000Z")),
      updatedAt = Some(ZonedDateTime.parse("2019-10-10T15:10:10.000Z")),
      events = Nil
    )
  }

  def user(id: UserId, anonymousParticipation: Boolean = false): User = {
    User(
      userId = id,
      email = "test@make.org",
      firstName = Some("Joe"),
      lastName = Some("Chip"),
      lastIp = None,
      hashedPassword = None,
      enabled = true,
      emailVerified = true,
      lastConnection = ZonedDateTime.parse("1992-08-23T02:02:02.020Z"),
      verificationToken = None,
      verificationTokenExpiresAt = None,
      resetToken = None,
      resetTokenExpiresAt = None,
      roles = Seq(RoleCitizen),
      country = Country("FR"),
      language = Language("fr"),
      profile = None,
      createdAt = None,
      updatedAt = None,
      lastMailingError = None,
      organisationName = None,
      availableQuestions = Seq.empty,
      anonymousParticipation = anonymousParticipation
    )
  }

  Mockito
    .when(userService.getUser(ArgumentMatchers.any[UserId]))
    .thenAnswer(invocation => Future.successful(Some(user(invocation.getArgument[UserId](0)))))

  Mockito
    .when(tagService.retrieveIndexedTags(Seq.empty))
    .thenReturn(Future.successful(Some(Seq.empty)))

  Mockito
    .when(questionService.getQuestion(QuestionId("question")))
    .thenReturn(
      Future.successful(
        Some(
          Question(
            questionId = QuestionId("question"),
            slug = "question",
            question = "question",
            country = Country("FR"),
            language = Language("fr"),
            themeId = None,
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
            theme = QuestionTheme("", "", "", ""),
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
    scenario("proposal without votes") {
      val id = ProposalId("proposal-without-votes")

      Mockito.when(proposalCoordinatorService.getProposal(id)).thenReturn(Future.successful(Some(proposal(id))))

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

      }
    }

    scenario("anonymous participation") {
      val id = ProposalId("anonymous-participation")
      val author = UserId("anonymous-participation-author")

      Mockito
        .when(proposalCoordinatorService.getProposal(id))
        .thenReturn(Future.successful(Some(proposal(id, author = author))))

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
        .thenReturn(Future.successful(Some(proposal(id, requestContext = requestContext))))

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
