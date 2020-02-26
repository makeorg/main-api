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

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import org.make.api.operation.{
  CreateOperationOfQuestion,
  OperationOfQuestionServiceComponent,
  OperationServiceComponent
}
import org.make.api.technical.generator.EntitiesGen
import org.make.api.user.UserServiceComponent
import org.make.core.operation.{OperationId, SimpleOperation}
import org.make.core.question.QuestionId
import org.make.core.user.{User, UserId}

import scala.concurrent.{ExecutionContext, Future}
import fixtures._
import org.make.api.ActorSystemComponent
import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.scalacheck.Gen

trait FixturesService {
  def generate(maybeOperationId: Option[OperationId],
               maybeQuestionId: Option[QuestionId],
               proposalFillMode: FillMode): Future[Map[String, Int]]
}

trait FixturesServiceComponent {
  def fixturesService: FixturesService
}

trait DefaultFixturesServiceComponent extends FixturesServiceComponent {
  this: OperationServiceComponent
    with UserServiceComponent
    with OperationOfQuestionServiceComponent
    with ActorSystemComponent =>
  override lazy val fixturesService: FixturesService = new DefaultFixturesService

  class DefaultFixturesService extends FixturesService {

    private implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

    private val httpThreads = 32
    implicit private val executionContext: ExecutionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(httpThreads))

    def generateOperation(maybeOperationId: Option[OperationId], adminCreatorId: UserId): Future[OperationId] = {
      maybeOperationId match {
        case Some(operationId) => Future.successful(operationId)
        case None =>
          val operation: SimpleOperation = EntitiesGen.genSimpleOperation.value
          operationService.create(
            userId = adminCreatorId,
            slug = operation.slug,
            defaultLanguage = operation.defaultLanguage,
            allowedSources = operation.allowedSources,
            operationKind = operation.operationKind
          )
      }
    }

    def generateQuestion(maybeQuestionId: Option[QuestionId], operationId: OperationId): Future[QuestionId] = {
      maybeQuestionId match {
        case Some(questionId) => Future.successful(questionId)
        case None =>
          val parameters: CreateOperationOfQuestion = EntitiesGen.genCreateOperationOfQuestion(operationId).value
          operationOfQuestionService.create(parameters).map(_.questionId)
      }
    }

    def generateUsers(questionId: QuestionId): Future[Seq[UserId]] = {
      val userDatas = Gen.listOf(EntitiesGen.genUserRegisterData(Some(questionId))).value
      Source(userDatas.distinctBy(_.email))
        .mapAsync(5) { data =>
          userService.register(data, RequestContext.empty)
        }
        .map(_.userId)
        .runWith(Sink.seq)
    }

    def generateProposals(id: QuestionId, mode: FillMode, usersIds: Seq[UserId]): Future[Seq[ProposalId]] = ???

    override def generate(maybeOperationId: Option[OperationId],
                          maybeQuestionId: Option[QuestionId],
                          proposalFillMode: FillMode): Future[Map[String, Int]] = {
      val futureAdmin: Future[User] = userService.getUserByEmail("admin@make.org").flatMap {
        case Some(user) => Future.successful(user)
        case None       => Future.failed(new IllegalStateException())
      }
      for {
        admin       <- futureAdmin
        operationId <- generateOperation(maybeOperationId, admin.userId)
        questionId  <- generateQuestion(maybeQuestionId, operationId)
        usersIds    <- generateUsers(questionId)
//        _           <- generateProposals(questionId, proposalFillMode, usersIds)
      } yield Map("users" -> usersIds.size, "proposals" -> 0)
    }
  }
}
