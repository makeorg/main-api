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

import java.time.ZonedDateTime
import java.util.UUID

import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.api.{DatabaseTest, TestUtilsIT}
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagDisplay, TagType}
import org.make.core.user.{Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class OperationServiceIT
    extends DatabaseTest
    with DefaultOperationServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultIdGeneratorComponent
    with DefaultPersistentQuestionServiceComponent {

  override protected val cockroachExposedPort: Int = 40007

  val userId: UserId = UserId(UUID.randomUUID().toString)
  private val languageFr: Language = Language("fr")

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
    country = Country("FR"),
    language = languageFr,
    questionId = None
  )

  val simpleOperation = SimpleOperation(
    operationId = operationId,
    createdAt = None,
    updatedAt = None,
    status = OperationStatus.Pending,
    slug = "hello-operation",
    defaultLanguage = languageFr,
    allowedSources = Seq.empty,
    operationKind = OperationKind.PublicConsultation
  )

  Feature("An operation can be created") {
    Scenario("Create an operation and get the operation") {
      Given("""
               |an operation with
               |status = Pending
               |slug = "hello-operation"
               |defaultLanguage = fr
               |""".stripMargin)
      When("""I persist it""")
      And("I update operation")
      And("I get the created operation")

      val futureMaybeOperation: Future[Option[Operation]] = for {
        _ <- persistentUserService.persist(johnDoe)
        operationId <- operationService.create(
          userId = userId,
          slug = simpleOperation.slug,
          defaultLanguage = simpleOperation.defaultLanguage,
          allowedSources = simpleOperation.allowedSources,
          operationKind = simpleOperation.operationKind
        )
        _ <- operationService.update(
          operationId = operationId,
          slug = Some("hello-updated-operation"),
          defaultLanguage = Some(Language("pt")),
          userId = userId,
          operationKind = Some(OperationKind.GreatCause)
        )
        operation <- operationService.findOne(operationId)
      } yield operation

      whenReady(futureMaybeOperation, Timeout(6.seconds)) { maybeOperation =>
        val operation: Operation = maybeOperation.get
        Then("operations should be an instance of Seq[Operation]")
        operation shouldBe a[Operation]
        operation.slug shouldBe "hello-updated-operation"
        operation.operationKind shouldBe OperationKind.GreatCause
        And("operations events should contain a create event")
        operation.events
          .filter(_.actionType == OperationCreateAction.name)
          .head
          .arguments
          .get("operation")
          .toString should be(s"""Some({
            |  "operationId" : "${operation.operationId.value}",
            |  "status" : "Pending",
            |  "defaultLanguage" : "fr"
            |})""".stripMargin)
        And("operations events should contain an update event")
        operation.events
          .filter(_.actionType == OperationUpdateAction.name)
          .head
          .arguments
          .get("operation")
          .toString should be(s"""Some({
            |  "operationId" : "${operation.operationId.value}",
            |  "status" : "Pending",
            |  "defaultLanguage" : "pt"
            |})""".stripMargin)
      }
    }
  }
}
