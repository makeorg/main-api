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

import java.time.{LocalDate, ZonedDateTime}

import org.make.api.DatabaseTest
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.{Tag, TagDisplay, TagType}
import org.make.core.user.{Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

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

  val profile = Profile(
    dateOfBirth = Some(LocalDate.parse("2000-01-01")),
    avatarUrl = Some("https://www.example.com"),
    profession = Some("profession"),
    phoneNumber = Some("010101"),
    description = Some("Resume of who I am"),
    twitterId = Some("@twitterid"),
    facebookId = Some("facebookid"),
    googleId = Some("googleId"),
    gender = Some(Gender.Male),
    genderName = Some("other"),
    postalCode = Some("93"),
    karmaLevel = Some(2),
    locale = Some("FR_FR"),
    socioProfessionalCategory = Some(SocioProfessionalCategory.Employee)
  )
  val userId: UserId = idGenerator.nextUserId()
  val johnDoe = User(
    userId = userId,
    email = "doe@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    emailVerified = true,
    lastConnection = ZonedDateTime.parse("2017-06-01T12:30:40Z"),
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(ZonedDateTime.parse("2017-06-01T12:30:40Z")),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = Some(profile)
  )

  def newTag(label: String): Tag = Tag(
    tagId = idGenerator.nextTagId(),
    label = label,
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagType.LEGACY.tagTypeId,
    operationId = None,
    themeId = None,
    country = Country("FR"),
    language = Language("fr"),
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
    defaultLanguage = Language("fr"),
    allowedSources = Seq("core"),
    events = List(
      OperationAction(
        date = now,
        makeUserId = userId,
        actionType = OperationCreateAction.name,
        arguments = Map("arg1" -> "valueArg1")
      )
    ),
    questions = Seq(
      QuestionWithDetails(
        question = Question(
          questionId = QuestionId("some-question"),
          country = Country("FR"),
          language = Language("fr"),
          slug = "hello-fr",
          question = "ça va ?",
          operationId = Some(operationId),
          themeId = None
        ),
        details = OperationOfQuestion(
          questionId = QuestionId("some-question"),
          operationId = operationId,
          startDate = None,
          endDate = None,
          operationTitle = "bonjour operation",
          landingSequenceId = sequenceIdFR
        )
      ),
      QuestionWithDetails(
        question = Question(
          questionId = QuestionId("some-question-gb"),
          country = Country("GB"),
          language = Language("en"),
          slug = "hello-gb",
          question = "how are you ?",
          operationId = Some(operationId),
          themeId = None
        ),
        details = OperationOfQuestion(
          questionId = QuestionId("some-question-gb"),
          operationId = operationId,
          startDate = None,
          endDate = None,
          operationTitle = "hello operation",
          landingSequenceId = sequenceIdGB
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

  feature("An operation can be persisted") {
    scenario("Persist an operation and get the persisted operation") {
      Given("""
           |an operation with
           |status = Pending
           |slug = "hello-operation"
           |defaultLanguage = fr
           |""".stripMargin)
      When("""I persist it""")
      And("I get the persisted operation")

      val simpleOperation = SimpleOperation(
        operationId = fullOperation.operationId,
        status = fullOperation.status,
        slug = fullOperation.slug,
        allowedSources = fullOperation.allowedSources,
        defaultLanguage = fullOperation.defaultLanguage,
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
        operation.status.shortName should be("Pending")
        And("""operation slug should be "hello-operation" """)
        operation.slug should be("hello-operation")
        And("""operation default translation should be "fr" """)
        operation.defaultLanguage should be(Language("fr"))
        And("""operation should have 2 questions""")
        operation.questions.size should be(2)
        And(s"""operation landing sequence id for FR configuration should be "${sequenceIdFR.value}" """)
        operation.questions
          .find(_.question.country == Country("FR"))
          .map(_.details.landingSequenceId) should be(Some(sequenceIdFR))
        And(s"""operation landing sequence id for GB configuration should be "${sequenceIdGB.value}" """)
        operation.questions.find(_.question.country == Country("GB")).map(_.details.landingSequenceId) should be(
          Some(sequenceIdGB)
        )
        And("operation events should contain a create event")
        val createEvent: OperationAction = operation.events.filter(_.actionType == "create").head
        createEvent.date.toEpochSecond should be(now.toEpochSecond)
        createEvent.makeUserId should be(userId)
        createEvent.actionType should be("create")
        createEvent.arguments should be(Map("arg1" -> "valueArg1"))
        And("the allowedSources should contain 'core'")
        operation.allowedSources.head should be("core")
      }
    }

    scenario("get a persisted operation by id") {

      val operationIdForGetById: OperationId = idGenerator.nextOperationId()

      val operationForGetById: SimpleOperation = SimpleOperation(
        operationId = operationIdForGetById,
        slug = "get-by-id-operation",
        status = OperationStatus.Active,
        allowedSources = Seq.empty,
        defaultLanguage = Language("fr"),
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

    scenario("get a persisted operation by slug") {

      val operationIdForGetBySlug: OperationId = idGenerator.nextOperationId()
      val operationForGetBySlug: SimpleOperation =
        SimpleOperation(
          operationId = operationIdForGetBySlug,
          slug = "get-by-slug-operation",
          status = OperationStatus.Active,
          allowedSources = Seq.empty,
          defaultLanguage = Language("fr"),
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

//    scenario("modify a persisted operation") {
//
//      val operationIdForModify: OperationId = idGenerator.nextOperationId()
//      val operationForModify: SimpleOperation =
//        SimpleOperation(
//          operationId = operationIdForModify,
//          slug = "modify-operation",
//          status = OperationStatus.Active,
//          allowedSources = Seq.empty,
//          defaultLanguage = Language("fr"),
//          createdAt = None,
//          updatedAt = None
//        )
//      Given(s""" a persisted operation ${operationForModify.operationId.value}""")
//      When("i get the modify operation")
//      Then("the modification success")
//
//      val futurePersistedOperation: Future[Operation] =
//        for {
//          simple    <- persistentOperationService.persist(operation = operationForModify)
//          _         <- createQuestions(simple.operationId)
//          operation <- persistentOperationService.getById(simple.operationId)
//        } yield operation.get
//
//      whenReady(futurePersistedOperation, Timeout(3.seconds)) { initialOperation =>
//        val waitingTime = 1000
//        Thread.sleep(waitingTime) // needed to test updatedAt
//        val futureMaybeOperation: Future[Option[Operation]] =
//          persistentOperationService
//            .modify(
//              initialOperation.copy(
//                status = OperationStatus.Active,
//                defaultLanguage = Language("br"),
//                slug = "newSlug",
//                translations = Seq(
//                  OperationTranslation(title = "modify operation", language = Language("en")),
//                  OperationTranslation(title = "modify operation br", language = Language("br")),
//                  OperationTranslation(title = "modify operation it", language = Language("it"))
//                ),
//                events = List(
//                  OperationAction(
//                    date = initialOperation.events.head.date,
//                    makeUserId = userId,
//                    actionType = OperationCreateAction.name,
//                    arguments = Map("arg1" -> "valueArg1")
//                  ),
//                  OperationAction(
//                    date = now,
//                    makeUserId = userId,
//                    actionType = OperationUpdateAction.name,
//                    arguments = Map("arg2" -> "valueArg2")
//                  )
//                ),
//                countriesConfiguration = Seq(
//                  OperationCountryConfiguration(
//                    countryCode = Country("BR"),
//                    tagIds = Seq.empty,
//                    landingSequenceId = SequenceId("updatedSequenceId"),
//                    startDate = None,
//                    endDate = None,
//                    questionId = None
//                  )
//                )
//              )
//            )
//            .flatMap { operation =>
//              persistentOperationService.getById(operation.operationId)
//            }
//
//        whenReady(futureMaybeOperation, Timeout(3.seconds)) { maybeOperation =>
//          val operation: Operation = maybeOperation.get
//          operation should not be None
//          operation shouldBe a[Operation]
//          operation.operationId.value should be(operationForModify.operationId.value)
//          operation.translations.length should be(3)
//          operation.translations.filter(translation => translation.language == Language("en")).head.title should be(
//            "modify operation"
//          )
//          operation.translations.filter(translation => translation.language == Language("it")).head.title should be(
//            "modify operation it"
//          )
//          operation.translations.filter(translation => translation.language == Language("br")).head.title should be(
//            "modify operation br"
//          )
//          operation.events.length should be(2)
//          operation.events.filter(event => event.actionType == OperationUpdateAction.name).head.arguments should be(
//            Map("arg2" -> "valueArg2")
//          )
//          operation.slug should be("newSlug")
//          operation.questions
//            .find(_.question.country == Country("BR"))
//            .map(_.details.landingSequenceId.value) should be(Some("updatedSequenceId"))
//          operation.defaultLanguage should be(Language("br"))
//          initialOperation.createdAt.get.toEpochSecond should be(operation.createdAt.get.toEpochSecond)
//          initialOperation.updatedAt.get.toEpochSecond should be < operation.updatedAt.get.toEpochSecond
//          operation.countriesConfiguration.size should be(1)
//          operation.countriesConfiguration.head.tagIds.size should be(0)
//          operation.status.shortName should be(OperationStatus.Active.shortName)
//        }
//      }
//    }
  }
}
