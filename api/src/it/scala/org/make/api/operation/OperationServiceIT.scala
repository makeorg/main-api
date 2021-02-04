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

package org.make.api.operation

import akka.actor.ActorSystem
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.api.{ActorSystemComponent, DatabaseTest, TestUtilsIT}
import org.make.core.DateHelper
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.operation.OperationActionType._
import org.make.core.operation._
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagDisplay, TagType}
import org.make.core.user.{Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class OperationServiceIT
    extends DatabaseTest
    with DefaultOperationServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultIdGeneratorComponent
    with DefaultPersistentQuestionServiceComponent
    with OperationOfQuestionServiceComponent
    with ActorSystemComponent {

  override protected val cockroachExposedPort: Int = 40007

  override val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]

  when(operationOfQuestionService.indexById(any[QuestionId]))
    .thenReturn(Future.successful(Some(IndexationStatus.Completed)))

  val userId: UserId = UserId(UUID.randomUUID().toString)

  val johnDoe: User = TestUtilsIT.user(
    id = userId,
    email = "doeOpeService@example.com",
    firstName = Some("John"),
    lastName = Some("Doe Ope Service"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    lastConnection = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]"),
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")),
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    profile = None
  )
  val sequenceId: SequenceId = SequenceId(UUID.randomUUID().toString)
  val operationId: OperationId = OperationId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()
  val targaryen: Tag = Tag(
    tagId = idGenerator.nextTagId(),
    label = "Targaryen",
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagType.LEGACY.tagTypeId,
    operationId = None,
    questionId = None
  )

  val simpleOperation: SimpleOperation = SimpleOperation(
    operationId = operationId,
    createdAt = None,
    updatedAt = None,
    status = OperationStatus.Pending,
    slug = "hello-operation",
    operationKind = OperationKind.BusinessConsultation
  )

  Feature("An operation can be created") {
    Scenario("Create an operation and get the operation") {
      Given("""
               |an operation with
               |status = Pending
               |slug = "hello-operation"
               |""".stripMargin)
      When("""I persist it""")
      And("I update operation")
      And("I get the created operation")

      val futureMaybeOperation: Future[Option[Operation]] = for {
        _ <- persistentUserService.persist(johnDoe)
        operationId <- operationService.create(
          userId = userId,
          slug = simpleOperation.slug,
          operationKind = simpleOperation.operationKind
        )
        _ <- operationService.update(
          operationId = operationId,
          slug = Some("hello-updated-operation"),
          userId = userId,
          operationKind = Some(OperationKind.GreatCause)
        )
        operation <- operationService.findOne(operationId)
      } yield operation

      whenReady(futureMaybeOperation, Timeout(6.seconds)) { maybeOperation =>
        val operation: Operation = maybeOperation.get
        Then("operations should be an instance of Seq[Operation]")
        operation.slug shouldBe "hello-updated-operation"
        operation.operationKind shouldBe OperationKind.GreatCause
        And("operations events should contain a create event")
        operation.events
          .filter(_.actionType == OperationCreateAction.value)
          .head
          .arguments
          .get("operation")
          .toString should be(s"""Some({
            |  "operationId" : "${operation.operationId.value}",
            |  "status" : "Pending"
            |})""".stripMargin)
        And("operations events should contain an update event")
        operation.events
          .filter(_.actionType == OperationUpdateAction.value)
          .head
          .arguments
          .get("operation")
          .toString should be(s"""Some({
            |  "operationId" : "${operation.operationId.value}",
            |  "status" : "Pending"
            |})""".stripMargin)
      }
    }
  }
}
