package org.make.api.operation

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import org.make.api.DatabaseTest
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.profile.{Gender, Profile}
import org.make.core.reference.Tag
import org.make.core.sequence.SequenceId
import org.make.core.user.{Role, User, UserId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class OperationServiceIT
    extends DatabaseTest
    with DefaultOperationServiceComponent
    with DefaultPersistentOperationServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentTagServiceComponent {

  val profile = Profile(
    dateOfBirth = Some(LocalDate.parse("2000-01-02")),
    avatarUrl = Some("https://www.example.com"),
    profession = Some("profession"),
    phoneNumber = Some("010101"),
    twitterId = Some("@twitterid"),
    facebookId = Some("facebookid"),
    googleId = Some("googleId"),
    gender = Some(Gender.Male),
    genderName = Some("other"),
    postalCode = Some("75"),
    karmaLevel = Some(2),
    locale = Some("FR_FR")
  )
  val userId: UserId = UserId(UUID.randomUUID().toString)
  val johnDoe = User(
    userId = userId,
    email = "doeOpeService@example.com",
    firstName = Some("John"),
    lastName = Some("Doe Ope Service"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    verified = true,
    lastConnection = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]"),
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    country = "FR",
    language = "fr",
    profile = Some(profile),
    isHardBounce = false,
    lastMailingError = None
  )
  val sequenceId: SequenceId = SequenceId(UUID.randomUUID().toString)
  val operationId: OperationId = OperationId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()
  val targaryen: Tag = Tag("Targaryen")

  val simpleOperation = Operation(
    operationId = operationId,
    createdAt = None,
    updatedAt = None,
    status = OperationStatus.Pending,
    slug = "hello-operation",
    translations = Seq(
      OperationTranslation(title = "bonjour operation", language = "fr"),
      OperationTranslation(title = "hello operation", language = "en")
    ),
    defaultLanguage = "fr",
    events = List.empty,
    countriesConfiguration = Seq.empty
  )

  feature("An operation can be created") {
    scenario("Create an operation and get the operation") {
      Given(s"""
               |an operation "${simpleOperation.translations.head.title}" with
               |titles =
               |  fr -> "bonjour operation"
               |  en -> "hello operation"
               |status = Pending
               |slug = "hello-operation"
               |defaultLanguage = fr
               |countriesConfiguration = Seq.empty
               |events = List.empty
               |""".stripMargin)
      When(s"""I persist "${simpleOperation.translations.head.title}"""")
      And("I update operation")
      And("I get the created operation")

      val futureMaybeOperation: Future[Option[Operation]] = for {
        _ <- persistentUserService.persist(johnDoe)
        operationId <- operationService.create(
          userId = userId,
          slug = simpleOperation.slug,
          translations = simpleOperation.translations,
          defaultLanguage = simpleOperation.defaultLanguage,
          countriesConfiguration = simpleOperation.countriesConfiguration
        )
        _ <- operationService.update(
          operationId = operationId,
          slug = Some("hello-updated-operation"),
          translations = Some(
            Seq(
              OperationTranslation(title = "ola operation", language = "pt"),
              OperationTranslation(title = "hello operation", language = "en")
            )
          ),
          defaultLanguage = Some("pt"),
          userId = userId
        )
        operation <- operationService.findOne(operationId)
      } yield operation

      whenReady(futureMaybeOperation, Timeout(3.seconds)) { maybeOperation =>
        val operation: Operation = maybeOperation.get
        Then("operations should be an instance of Seq[Operation]")
        operation shouldBe a[Operation]
        operation.slug shouldBe "hello-updated-operation"
        And("operations events should contain a create event")
        operation.events
          .filter(_.actionType == OperationCreateAction.name)
          .head
          .arguments
          .get("operation")
          .toString should be(s"""Some({
            |  "translations" : "fr:bonjour operation,en:hello operation",
            |  "countriesConfiguration" : "",
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
            |  "translations" : "pt:ola operation,en:hello operation",
            |  "countriesConfiguration" : "",
            |  "operationId" : "${operation.operationId.value}",
            |  "status" : "Pending",
            |  "defaultLanguage" : "pt"
            |})""".stripMargin)
      }
    }
  }
}
