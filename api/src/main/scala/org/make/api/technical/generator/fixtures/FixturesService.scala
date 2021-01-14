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

package org.make.api.technical.generator.fixtures

import java.util.concurrent.Executors

import akka.stream.scaladsl.{Sink, Source}
import grizzled.slf4j.Logging
import org.make.api.ActorSystemComponent
import org.make.api.operation.{
  CreateOperationOfQuestion,
  OperationOfQuestionServiceComponent,
  OperationServiceComponent
}
import org.make.api.organisation.OrganisationServiceComponent
import org.make.api.partner.PartnerServiceComponent
import org.make.api.proposal.{
  ProposalServiceComponent,
  RefuseProposalRequest,
  UpdateQualificationRequest,
  UpdateVoteRequest
}
import org.make.api.question.QuestionServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.ExecutorServiceHelper._
import org.make.api.technical.generator.EntitiesGen
import org.make.api.technical.security.{SecurityConfigurationComponent, SecurityHelper}
import org.make.api.user.UserServiceComponent
import org.make.core.operation.{OperationId, SimpleOperation}
import org.make.core.partner.Partner
import org.make.core.proposal.{ProposalId, ProposalStatus, VoteKey}
import org.make.core.question.{Question, QuestionId}
import org.make.core.session.SessionId
import org.make.core.tag.{Tag, TagId}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.scalacheck.Gen
import org.scalacheck.Gen.Parameters
import org.scalacheck.rng.Seed

import scala.concurrent.{ExecutionContext, Future}

trait FixturesService {
  def generate(maybeOperationId: Option[OperationId], maybeQuestionId: Option[QuestionId]): Future[FixtureResponse]
}

trait FixturesServiceComponent {
  def fixturesService: FixturesService
}

trait DefaultFixturesServiceComponent extends FixturesServiceComponent with Logging {
  this: OperationServiceComponent
    with UserServiceComponent
    with QuestionServiceComponent
    with OperationOfQuestionServiceComponent
    with ProposalServiceComponent
    with TagServiceComponent
    with OrganisationServiceComponent
    with PartnerServiceComponent
    with SecurityConfigurationComponent
    with SessionHistoryCoordinatorServiceComponent
    with ActorSystemComponent =>
  override lazy val fixturesService: FixturesService = new DefaultFixturesService

  class DefaultFixturesService extends FixturesService {

    private val nThreads = 32
    implicit private val executionContext: ExecutionContext =
      Executors.newFixedThreadPool(nThreads).instrument("fixtures").toExecutionContext

    def generateOperation(maybeOperationId: Option[OperationId], adminUserId: UserId): Future[OperationId] = {
      maybeOperationId match {
        case Some(operationId) => Future.successful(operationId)
        case None =>
          val operation: SimpleOperation = EntitiesGen.genSimpleOperation.value
          operationService.create(userId = adminUserId, slug = operation.slug, operationKind = operation.operationKind)
      }
    }

    def generateQuestion(maybeQuestionId: Option[QuestionId], operationId: OperationId): Future[QuestionId] = {
      maybeQuestionId match {
        case Some(questionId) => Future.successful(questionId)
        case None =>
          val parameters: CreateOperationOfQuestion = EntitiesGen.genCreateOperationOfQuestion(operationId).value
          operationOfQuestionService.create(parameters).map { question =>
            logger.info(s"generated: question ${question.questionId}")
            question.questionId
          }
      }
    }

    def generateUsers(questionId: QuestionId): Future[Seq[User]] = {
      val usersData = Gen.listOf(EntitiesGen.genUserRegisterData(Some(questionId))).value
      logger.info(s"generating: ${usersData.size} users")
      Source(usersData.distinctBy(_.email))
        .mapAsync(16) { data =>
          userService.register(data, RequestContext.empty)
        }
        .runWith(Sink.seq)
    }

    def generateOrganisations: Future[Seq[User]] = {
      val parameters = Parameters.default.withSize(20)
      val orgasData = Gen.listOf(EntitiesGen.genOrganisationRegisterData).pureApply(parameters, Seed.random())
      logger.info(s"generating: ${orgasData.size} organisations")
      Source(orgasData.distinctBy(_.email))
        .mapAsync(16) { data =>
          organisationService.register(data, RequestContext.empty)
        }
        .runWith(Sink.seq)
    }

    def generatePartners(questionId: QuestionId, organisations: Seq[User]): Future[Seq[Partner]] = {
      val parameters = Parameters.default.withSize(20)
      val partnersData =
        Gen.listOf(EntitiesGen.genCreatePartnerRequest(None, questionId)).pureApply(parameters, Seed.random()) ++
          organisations.map(orga => EntitiesGen.genCreatePartnerRequest(Some(orga), questionId).value)
      logger.info(s"generating: ${partnersData.size} partners")
      Source(partnersData)
        .mapAsync(16) { data =>
          partnerService.createPartner(data)
        }
        .runWith(Sink.seq)
    }

    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    def generateProposals(
      question: Question,
      users: Seq[User],
      tagsIds: Seq[TagId],
      adminUserId: UserId
    ): Future[Seq[ProposalId]] = {
      val parameters: Parameters = Parameters.default.withSize(2000)
      val proposalsData =
        Gen.listOf(EntitiesGen.genProposal(question, users, tagsIds)).pureApply(parameters, Seed.random())
      logger.info(s"generating: ${proposalsData.size} proposals")

      Source(proposalsData)
        .mapAsync(16) { proposal =>
          proposalService
            .propose(
              user = users.find(_.userId == proposal.author).get,
              requestContext = RequestContext.empty,
              createdAt = proposal.createdAt.getOrElse(DateHelper.now()),
              content = proposal.content,
              question = question,
              initialProposal = proposal.initialProposal
            )
            .map(proposalId => proposal.copy(proposalId = proposalId))
        }
        .mapAsync(16) { proposal =>
          proposal.status match {
            case ProposalStatus.Pending => Future.successful(proposal)
            case ProposalStatus.Postponed =>
              proposalService
                .postponeProposal(proposal.proposalId, adminUserId, RequestContext.empty)
                .map(_ => proposal)
            case ProposalStatus.Accepted =>
              proposalService
                .validateProposal(
                  proposalId = proposal.proposalId,
                  moderator = adminUserId,
                  requestContext = RequestContext.empty,
                  question = question,
                  newContent = None,
                  sendNotificationEmail = false,
                  idea = proposal.idea,
                  tags = proposal.tags,
                  predictedTags = None,
                  predictedTagsModelName = None
                )
                .map(_ => proposal)
            case ProposalStatus.Refused =>
              proposalService
                .refuseProposal(
                  proposalId = proposal.proposalId,
                  moderator = adminUserId,
                  requestContext = RequestContext.empty,
                  request = RefuseProposalRequest(sendNotificationEmail = false, refusalReason = proposal.refusalReason)
                )
                .map(_ => proposal)
            case ProposalStatus.Archived => Future.successful(proposal)
          }
        }
        .mapAsync(16) { proposal =>
          val votesVerified = proposal.votes.map(
            v =>
              UpdateVoteRequest(
                key = v.key,
                count = Some(v.count),
                countVerified = Some(v.countVerified),
                countSequence = Some(v.countSequence),
                countSegment = Some(v.countSegment),
                qualifications = v.qualifications.map(
                  q =>
                    UpdateQualificationRequest(
                      key = q.key,
                      count = Some(q.count),
                      countVerified = Some(q.countVerified),
                      countSequence = Some(q.countSequence),
                      countSegment = Some(q.countSegment)
                    )
                )
              )
          )
          proposal.status match {
            case ProposalStatus.Accepted =>
              proposalService
                .updateVotes(
                  proposalId = proposal.proposalId,
                  moderator = adminUserId,
                  requestContext = RequestContext.empty,
                  updatedAt = proposal.updatedAt.getOrElse(DateHelper.now()),
                  votesVerified = votesVerified
                )
                .map(_ => proposal.proposalId)
            case _ => Future.successful(proposal.proposalId)
          }
        }
        .runWith(Sink.seq)
    }

    def generateTags(question: Question): Future[Seq[Tag]] = {
      val tagsData: Seq[Tag] = Gen.listOf(EntitiesGen.genTag(question.operationId, Some(question.questionId))).value
      logger.info(s"generating: ${tagsData.size} tags")
      Source(tagsData.distinctBy(_.label))
        .mapAsync(16) { tag =>
          tagService.createTag(
            label = tag.label,
            tagTypeId = tag.tagTypeId,
            question = question,
            display = tag.display,
            weight = tag.weight
          )
        }
        .runWith(Sink.seq)
    }

    def generateOrganisationVotes(orgas: Seq[User], proposals: Seq[ProposalId]): Future[Int] = {
      val sampleSize = if (proposals.size > 50) 50 else proposals.size
      val proposalsSample = Gen.pick(sampleSize, proposals).value
      def proposalKey(proposalId: ProposalId, sessionId: SessionId): String =
        SecurityHelper.generateProposalKeyHash(
          proposalId,
          sessionId,
          Some("fixtures"),
          securityConfiguration.secureVoteSalt
        )
      def requestContext(sessionId: SessionId) =
        RequestContext.empty.copy(sessionId = sessionId, location = Some("fixtures"))

      Source(orgas)
        .mapAsync(16) { orga =>
          val sessionId = SessionId(orga.userId.value)
          sessionHistoryCoordinatorService
            .convertSession(sessionId, orga.userId, requestContext(sessionId))
            .map(_ => orga)
        }
        .map(_ -> Gen.pick(sampleSize / 5, proposalsSample).value)
        .mapAsync(16) {
          case (orga, sample) =>
            val sessionId = SessionId(orga.userId.value)
            Source(sample.toSeq)
              .mapAsync(16) { proposalId =>
                proposalService.voteProposal(
                  proposalId = proposalId,
                  maybeUserId = Some(orga.userId),
                  requestContext = requestContext(sessionId),
                  voteKey = Gen.frequency((6, VoteKey.Agree), (3, VoteKey.Disagree), (1, VoteKey.Neutral)).value,
                  proposalKey = Some(proposalKey(proposalId, sessionId))
                )
              }
              .runWith(Sink.seq)
              .map(_.size)
        }
        .runWith(Sink.seq)
        .map(_.sum)
    }

    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    override def generate(
      maybeOperationId: Option[OperationId],
      maybeQuestionId: Option[QuestionId]
    ): Future[FixtureResponse] = {
      val futureAdmin: Future[User] = userService.getUserByEmail("admin@make.org").flatMap {
        case Some(user) => Future.successful(user)
        case None       => Future.failed(new IllegalStateException())
      }
      for {
        admin          <- futureAdmin
        operationId    <- generateOperation(maybeOperationId, admin.userId)
        questionId     <- generateQuestion(maybeQuestionId, operationId)
        users          <- generateUsers(questionId)
        orgas          <- generateOrganisations
        partners       <- generatePartners(questionId, orgas)
        question       <- questionService.getQuestion(questionId).map(_.get)
        tags           <- generateTags(question)
        proposals      <- generateProposals(question, users ++ orgas, tags.map(_.tagId), admin.userId)
        orgasVoteCount <- generateOrganisationVotes(orgas, proposals)
      } yield FixtureResponse(
        operationId = operationId,
        questionId = questionId,
        userCount = users.size,
        organisationCount = orgas.size,
        partnerCount = partners.size,
        tagCount = tags.size,
        proposalCount = proposals.size,
        organisationsVoteCount = orgasVoteCount
      )
    }
  }
}
