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

import cats.data.NonEmptyList
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.api.{DatabaseTest, TestUtilsIT}
import org.make.core.{DateHelper, Order}
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagDisplay, TagType}
import org.make.core.user.{Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.make.core.technical.Pagination.{End, Start}

class PersistentOperationServiceIT
    extends DatabaseTest
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentTagServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentOperationOfQuestionServiceComponent
    with DefaultIdGeneratorComponent
    with DefaultOperationServiceComponent {

  override protected val cockroachExposedPort: Int = 40008

  val userId: UserId = idGenerator.nextUserId()
  val johnDoe: User = TestUtilsIT.user(
    id = userId,
    email = "doe@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    lastConnection = ZonedDateTime.parse("2017-06-01T12:30:40Z"),
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(ZonedDateTime.parse("2017-06-01T12:30:40Z")),
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    profile = None
  )

  def newTag(label: String): Tag = Tag(
    tagId = idGenerator.nextTagId(),
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagType.LEGACY.tagTypeId,
    operationId = None,
    questionId = None
  )

  val stark: Tag = newTag("Stark")
  val targaryen: Tag = newTag("Targaryen")
  val bolton: Tag = newTag("Bolton")
  val greyjoy: Tag = newTag("Greyjoy")
  val now: ZonedDateTime = DateHelper.now()
  val sequenceIdFR: SequenceId = idGenerator.nextSequenceId()
  val sequenceIdGB: SequenceId = idGenerator.nextSequenceId()
  val operationId: OperationId = idGenerator.nextOperationId()

  val fullOperation = Operation(
    operationId = operationId,
    createdAt = None,
    updatedAt = None,
    status = OperationStatus.Pending,
    slug = "hello-operation",
    operationKind = OperationKind.BusinessConsultation,
    events = List(
      OperationAction(
        date = now,
        makeUserId = userId,
        actionType = OperationActionType.OperationCreateAction.value,
        arguments = Map("arg1" -> "valueArg1")
      )
    ),
    questions = Seq(
      QuestionWithDetails(
        question = Question(
          questionId = QuestionId("some-question"),
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          slug = "hello-fr",
          question = "ça va ?",
          shortTitle = None,
          operationId = Some(operationId)
        ),
        details = OperationOfQuestion(
          questionId = QuestionId("some-question"),
          operationId = operationId,
          startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
          endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
          operationTitle = "bonjour operation",
          landingSequenceId = sequenceIdFR,
          canPropose = true,
          sequenceCardsConfiguration = SequenceCardsConfiguration(
            introCard = IntroCard(enabled = true, title = None, description = None),
            pushProposalCard = PushProposalCard(enabled = true),
            signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
            finalCard = FinalCard(
              enabled = true,
              sharingEnabled = false,
              title = None,
              shareDescription = None,
              learnMoreTitle = None,
              learnMoreTextButton = None,
              linkUrl = None
            )
          ),
          aboutUrl = None,
          metas = Metas(title = None, description = None, picture = None),
          theme = QuestionTheme.default,
          description = OperationOfQuestion.defaultDescription,
          consultationImage = None,
          consultationImageAlt = None,
          descriptionImage = None,
          descriptionImageAlt = None,
          resultsLink = None,
          proposalsCount = 42,
          participantsCount = 84,
          actions = None,
          featured = true
        )
      ),
      QuestionWithDetails(
        question = Question(
          questionId = QuestionId("some-question-gb"),
          countries = NonEmptyList.of(Country("GB")),
          language = Language("en"),
          slug = "hello-gb",
          question = "how are you ?",
          shortTitle = None,
          operationId = Some(operationId)
        ),
        details = OperationOfQuestion(
          questionId = QuestionId("some-question-gb"),
          operationId = operationId,
          startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
          endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
          operationTitle = "hello operation",
          landingSequenceId = sequenceIdGB,
          canPropose = true,
          sequenceCardsConfiguration = SequenceCardsConfiguration(
            introCard = IntroCard(enabled = true, title = None, description = None),
            pushProposalCard = PushProposalCard(enabled = true),
            signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
            finalCard = FinalCard(
              enabled = true,
              sharingEnabled = false,
              title = None,
              shareDescription = None,
              learnMoreTitle = None,
              learnMoreTextButton = None,
              linkUrl = None
            )
          ),
          aboutUrl = None,
          metas = Metas(title = None, description = None, picture = None),
          theme = QuestionTheme.default,
          description = OperationOfQuestion.defaultDescription,
          consultationImage = None,
          consultationImageAlt = None,
          descriptionImage = None,
          descriptionImageAlt = None,
          resultsLink = Some(ResultsLink.Internal.TopIdeas),
          proposalsCount = 42,
          participantsCount = 84,
          actions = None,
          featured = true
        )
      )
    )
  )

  def createQuestions(operationId: OperationId): Future[Unit] = {
    Future
      .traverse(fullOperation.questions) { question =>
        val newQuestionId = idGenerator.nextQuestionId()

        for {
          _ <- persistentQuestionService
            .persist(question.question.copy(questionId = newQuestionId, operationId = Some(operationId)))
          _ <- persistentOperationOfQuestionService.persist(
            question.details
              .copy(operationId = operationId, questionId = newQuestionId)
          )
        } yield {}
      }
      .map(_ => ())
  }

  Feature("An operation can be persisted") {
    Scenario("Persist an operation and get the persisted operation") {
      Given("""
           |an operation with
           |status = Pending
           |slug = "hello-operation"
           |kind = "BUSINESS_CONSULTATION"
           |""".stripMargin)
      When("""I persist it""")
      And("I get the persisted operation")

      val simpleOperation = SimpleOperation(
        operationId = fullOperation.operationId,
        status = fullOperation.status,
        slug = fullOperation.slug,
        operationKind = OperationKind.BusinessConsultation,
        createdAt = None,
        updatedAt = None
      )

      val futureOperations: Future[Seq[Operation]] = for {
        _         <- persistentUserService.persist(johnDoe)
        operation <- persistentOperationService.persist(simpleOperation)
        _ <- persistentOperationService.addActionToOperation(
          operationId = simpleOperation.operationId,
          action = OperationAction(
            date = now,
            makeUserId = johnDoe.userId,
            actionType = "create",
            arguments = Map("arg1" -> "valueArg1")
          )
        )
        _      <- createQuestions(operation.operationId)
        result <- persistentOperationService.find(slug = Some(simpleOperation.slug))
      } yield result

      whenReady(futureOperations, Timeout(3.seconds)) { operations =>
        Then("operations should be an instance of Seq[Operation]")
        operations shouldBe a[Seq[_]]
        And(s"operations should contain operation with operationId ${operationId.value}")
        val operation: Operation = operations.filter(_.slug == simpleOperation.slug).head
        And("operation should be an instance of Operation")
        operation shouldBe a[Operation]
        And("""operation status should be Pending""")
        operation.status.value should be("Pending")
        And("""operation slug should be "hello-operation" """)
        operation.slug should be("hello-operation")
        And("""operation kind should be "business" """)
        operation.operationKind should be(OperationKind.BusinessConsultation)
        And("""operation should have 2 questions""")
        operation.questions.size should be(2)
        And(s"""operation landing sequence id for FR configuration should be "${sequenceIdFR.value}" """)
        operation.questions
          .find(_.question.countries.toList.contains(Country("FR")))
          .map(_.details.landingSequenceId) should be(Some(sequenceIdFR))
        And(s"""operation landing sequence id for GB configuration should be "${sequenceIdGB.value}" """)
        operation.questions
          .find(_.question.countries.toList.contains(Country("GB")))
          .map(_.details.landingSequenceId) should be(Some(sequenceIdGB))
        And("operation events should contain a create event")
        val createEvent: OperationAction = operation.events.filter(_.actionType == "create").head
        createEvent.date.toEpochSecond should be(now.toEpochSecond)
        createEvent.makeUserId should be(userId)
        createEvent.actionType should be("create")
        createEvent.arguments should be(Map("arg1" -> "valueArg1"))
      }
    }

    Scenario("get a persisted operation by id") {

      val operationIdForGetById: OperationId = idGenerator.nextOperationId()

      val operationForGetById: SimpleOperation = SimpleOperation(
        operationId = operationIdForGetById,
        slug = "get-by-id-operation",
        status = OperationStatus.Active,
        operationKind = OperationKind.BusinessConsultation,
        createdAt = None,
        updatedAt = None
      )

      Given(s""" a persisted operation with id ${operationIdForGetById.value}""")
      When("i get the persisted operation by id")
      Then(" the call success")

      val futureMaybeOperation: Future[Option[Operation]] =
        persistentOperationService.persist(operation = operationForGetById).flatMap { operation =>
          persistentOperationService.getById(operation.operationId)
        }

      whenReady(futureMaybeOperation, Timeout(3.seconds)) { maybeOperation =>
        maybeOperation should not be None
        maybeOperation.get shouldBe a[Operation]
      }
    }

    Scenario("get a persisted operation by slug") {

      val operationIdForGetBySlug: OperationId = idGenerator.nextOperationId()
      val operationForGetBySlug: SimpleOperation =
        SimpleOperation(
          operationId = operationIdForGetBySlug,
          slug = "get-by-slug-operation",
          status = OperationStatus.Active,
          operationKind = OperationKind.BusinessConsultation,
          createdAt = None,
          updatedAt = None
        )

      Given(s""" a persisted operation ${operationIdForGetBySlug.value} """)
      When("i get the persisted operation by slug")
      Then(" the call success")

      val futureMaybeOperation: Future[Option[Operation]] =
        persistentOperationService.persist(operation = operationForGetBySlug).flatMap { operation =>
          persistentOperationService.getBySlug(operation.slug)
        }

      whenReady(futureMaybeOperation, Timeout(3.seconds)) { maybeOperation =>
        maybeOperation should not be None
        maybeOperation.get shouldBe a[Operation]
        maybeOperation.get.slug shouldBe "get-by-slug-operation"
      }
    }
  }

  Feature("get simple operation") {
    Scenario("simple operation from full operation") {

      val operationId = OperationId("simple-operation")
      val simpleOperation = SimpleOperation(
        operationId = operationId,
        status = OperationStatus.Active,
        slug = "simple-operation",
        operationKind = OperationKind.BusinessConsultation,
        createdAt = None,
        updatedAt = None
      )

      val futureSimpleOperation: Future[Option[SimpleOperation]] = for {
        _      <- persistentOperationService.persist(simpleOperation)
        result <- persistentOperationService.getSimpleById(operationId)
      } yield result

      whenReady(futureSimpleOperation, Timeout(3.seconds)) { simpleOperation =>
        simpleOperation.isDefined shouldBe true
        simpleOperation.map(_.operationId) shouldBe Some(operationId)
      }
    }

    Scenario("sorted simple operations") {

      def simpleOperation(operationId: OperationId) = SimpleOperation(
        operationId = operationId,
        status = OperationStatus.Active,
        slug = s"${operationId.value}-sorted-slug",
        operationKind = OperationKind.BusinessConsultation,
        createdAt = None,
        updatedAt = None
      )

//    The pipe "|" symbol is used here because it has a high ascii code thus allowing us to be determinist in the order.
      val futureSortedSimpleOperations: Future[Seq[SimpleOperation]] = for {
        _ <- persistentOperationService.persist(simpleOperation(OperationId("|BBB operation")))
        _ <- persistentOperationService.persist(simpleOperation(OperationId("|AAA operation")))
        _ <- persistentOperationService.persist(simpleOperation(OperationId("|CCC operation")))
        results <- persistentOperationService.findSimple(
          start = Start(1),
          end = Some(End(3)),
          sort = Some("uuid"),
          order = Some(Order.desc),
          operationKinds = Some(Seq(OperationKind.BusinessConsultation))
        )
      } yield results

      whenReady(futureSortedSimpleOperations, Timeout(3.seconds)) { sortedSimpleOperations =>
        sortedSimpleOperations.size shouldBe 2
        sortedSimpleOperations.map(_.operationId) shouldBe Seq(
          OperationId("|BBB operation"),
          OperationId("|AAA operation")
        )
      }
    }
  }
}
