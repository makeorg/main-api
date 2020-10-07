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
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.operation.{
  CreateOperationOfQuestion,
  OperationOfQuestionServiceComponent,
  OperationServiceComponent
}
import org.make.api.proposal.{
  ProposalServiceComponent,
  RefuseProposalRequest,
  UpdateQualificationRequest,
  UpdateVoteRequest
}
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.ExecutorServiceHelper._
import org.make.api.technical.generator.EntitiesGen
import org.make.api.user.UserServiceComponent
import org.make.core.operation.{OperationId, SimpleOperation}
import org.make.core.proposal.{ProposalId, ProposalStatus}
import org.make.core.question.{Question, QuestionId}
import org.make.core.tag.TagId
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.scalacheck.Gen
import org.scalacheck.Gen.Parameters
import org.scalacheck.rng.Seed

import scala.concurrent.{ExecutionContext, Future}

trait FixturesService {
  def generate(
    maybeOperationId: Option[OperationId],
    maybeQuestionId: Option[QuestionId],
    proposalFillMode: Option[FillMode]
  ): Future[Map[String, Int]]
}

trait FixturesServiceComponent {
  def fixturesService: FixturesService
}

trait DefaultFixturesServiceComponent extends FixturesServiceComponent with StrictLogging {
  this: OperationServiceComponent
    with UserServiceComponent
    with QuestionServiceComponent
    with OperationOfQuestionServiceComponent
    with ProposalServiceComponent
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

    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    def generateProposals(
      question: Question,
      mode: Option[FillMode],
      users: Seq[User],
      tagsIds: Seq[TagId],
      adminUserId: UserId
    ): Future[Seq[ProposalId]] = {
      val parameters: Parameters = mode match {
        case None                => Parameters.default.withSize(0)
        case Some(FillMode.Tiny) => Parameters.default.withSize(20)
        case Some(FillMode.Big)  => Parameters.default.withSize(2000)
      }
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

    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    override def generate(
      maybeOperationId: Option[OperationId],
      maybeQuestionId: Option[QuestionId],
      proposalFillMode: Option[FillMode]
    ): Future[Map[String, Int]] = {
      val futureAdmin: Future[User] = userService.getUserByEmail("admin@make.org").flatMap {
        case Some(user) => Future.successful(user)
        case None       => Future.failed(new IllegalStateException())
      }
      for {
        admin       <- futureAdmin
        operationId <- generateOperation(maybeOperationId, admin.userId)
        questionId  <- generateQuestion(maybeQuestionId, operationId)
        users       <- generateUsers(questionId)
        question    <- questionService.getQuestion(questionId).map(_.get)
        proposals   <- generateProposals(question, proposalFillMode, users, Seq.empty, admin.userId)
      } yield Map("users" -> users.size, "proposals" -> proposals.size)
    }
  }
}
