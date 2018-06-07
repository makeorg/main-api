package org.make.api.operation

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import org.make.api.DatabaseTest
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.profile.{Gender, Profile}
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
    with DefaultIdGeneratorComponent {

  override protected val cockroachExposedPort: Int = 40008

  val profile = Profile(
    dateOfBirth = Some(LocalDate.parse("2000-01-01")),
    avatarUrl = Some("https://www.example.com"),
    profession = Some("profession"),
    phoneNumber = Some("010101"),
    twitterId = Some("@twitterid"),
    facebookId = Some("facebookid"),
    googleId = Some("googleId"),
    gender = Some(Gender.Male),
    genderName = Some("other"),
    postalCode = Some("93"),
    karmaLevel = Some(2),
    locale = Some("FR_FR")
  )
  val userId: UserId = UserId(UUID.randomUUID().toString)
  val johnDoe = User(
    userId = userId,
    email = "doe@example.com",
    firstName = Some("John"),
    lastName = Some("Doe"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    emailVerified = true,
    lastConnection = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]"),
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    country = "FR",
    language = "fr",
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
    country = "FR",
    language = "fr"
  )

  val stark: Tag = newTag("Stark")
  val targaryen: Tag = newTag("Targaryen")
  val bolton: Tag = newTag("Bolton")
  val greyjoy: Tag = newTag("Greyjoy")
  val now: ZonedDateTime = DateHelper.now()
  val sequenceIdFR: SequenceId = SequenceId(UUID.randomUUID().toString)
  val sequenceIdGB: SequenceId = SequenceId(UUID.randomUUID().toString)
  val operationId: OperationId = OperationId(UUID.randomUUID().toString)

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
    events = List(
      OperationAction(
        date = now,
        makeUserId = userId,
        actionType = OperationCreateAction.name,
        arguments = Map("arg1" -> "valueArg1")
      )
    ),
    countriesConfiguration = Seq(
      OperationCountryConfiguration(
        countryCode = "FR",
        tagIds = Seq(stark.tagId, targaryen.tagId),
        landingSequenceId = sequenceIdFR,
        startDate = None,
        endDate = None
      ),
      OperationCountryConfiguration(
        countryCode = "GB",
        tagIds = Seq(bolton.tagId, greyjoy.tagId),
        landingSequenceId = sequenceIdGB,
        startDate = None,
        endDate = None
      )
    )
  )

  feature("An operation can be persisted") {
    scenario("Persist an operation and get the persisted operation") {
      Given(s"""
           |an operation "${simpleOperation.translations.head.title}" with
           |titles =
           |  fr -> "bonjour operation"
           |  en -> "hello operation"
           |status = Pending
           |slug = "hello-operation"
           |defaultLanguage = fr
           |countriesConfiguration =
           |  FR -> ( tagIds -> [${stark.tagId.value}, ${targaryen.tagId.value}], landingSequenceId -> ${sequenceIdFR.value} ),
           |  GB -> ( tagIds -> [${bolton.tagId.value}, ${greyjoy.tagId.value}], landingSequenceId -> ${sequenceIdGB.value} ),
           |events =
           |  OperationAction(date = ${now.toString}, makeUserId = ${userId.value}, actionType = create, arguments = (arg1 -> valueArg1))
           |""".stripMargin)
      When(s"""I persist "${simpleOperation.translations.head.title}"""")
      And("I get the persisted operation")

      val futureOperations: Future[Seq[Operation]] = for {
        _ <- persistentTagService.persist(stark)
        _ <- persistentTagService.persist(targaryen)
        _ <- persistentUserService.persist(johnDoe)
        operation <- persistentOperationService
          .persist(operation = simpleOperation)
          .flatMap(_ => persistentOperationService.find())(readExecutionContext)
      } yield operation

      whenReady(futureOperations, Timeout(3.seconds)) { operations =>
        Then("operations should be an instance of Seq[Operation]")
        operations shouldBe a[Seq[_]]
        And(s"operations should contain operation with operationId ${operationId.value}")
        val operation: Operation = operations.filter(_.operationId.value == operationId.value).head
        And("operation should be an instance of Operation")
        operation shouldBe a[Operation]
        And("""operation should contain title translations "bonjour operation" and "hello operation" """)
        operation.translations.filter(_.language == "fr").head.title should be("bonjour operation")
        operation.translations.filter(_.language == "en").head.title should be("hello operation")
        And("""operation status should be Pending""")
        operation.status.shortName should be("Pending")
        And("""operation slug should be "hello-operation" """)
        operation.slug should be("hello-operation")
        And("""operation default translation should be "fr" """)
        operation.defaultLanguage should be("fr")
        And(s"""operation landing sequence id for FR configuration should be "${sequenceIdFR.value}" """)
        operation.countriesConfiguration.find(_.countryCode == "FR").map(_.landingSequenceId.value) should be(
          Some(sequenceIdFR.value)
        )
        And(s"""operation landing sequence id for GB configuration should be "${sequenceIdGB.value}" """)
        operation.countriesConfiguration.find(_.countryCode == "GB").map(_.landingSequenceId.value) should be(
          Some(sequenceIdGB.value)
        )
        And(s"""
             |operation countries configuration should be
             |  FR -> ( tagIds -> ${stark.tagId.value}, ${targaryen.tagId.value} )
             |  GB -> ( tagIds -> ${bolton.tagId.value}, ${greyjoy.tagId.value} )
             |""".stripMargin)
        operation.countriesConfiguration.filter(_.countryCode == "FR").head.tagIds should contain(stark.tagId)
        operation.countriesConfiguration.filter(_.countryCode == "FR").head.tagIds should contain(targaryen.tagId)
        operation.countriesConfiguration.filter(_.countryCode == "GB").head.tagIds should contain(bolton.tagId)
        operation.countriesConfiguration.filter(_.countryCode == "GB").head.tagIds should contain(greyjoy.tagId)
        And("operation events should contain a create event")
        val createEvent: OperationAction = operation.events.filter(_.actionType == "create").head
        createEvent.date.toEpochSecond should be(now.toEpochSecond)
        createEvent.makeUserId should be(userId)
        createEvent.actionType should be("create")
        createEvent.arguments should be(Map("arg1" -> "valueArg1"))
      }
    }

    scenario("get a persisted operation by id") {

      val operationIdForGetById: OperationId = OperationId(UUID.randomUUID().toString)
      val operationForGetById: Operation = simpleOperation.copy(
        operationId = operationIdForGetById,
        slug = "get-by-id-operation",
        translations = Seq(
          OperationTranslation(title = "get by id operation", language = "fr"),
          OperationTranslation(title = "get by id operation", language = "en")
        ),
        countriesConfiguration = Seq.empty
      )
      Given(s""" a persisted operation "${operationForGetById.translations.head.title}" """)
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

      val operationIdForGetBySlug: OperationId = OperationId(UUID.randomUUID().toString)
      val operationForGetBySlug: Operation = simpleOperation.copy(
        operationId = operationIdForGetBySlug,
        slug = "get-by-slug-operation",
        translations = Seq(
          OperationTranslation(title = "get by slug operation", language = "fr"),
          OperationTranslation(title = "get by slug operation", language = "en")
        ),
        countriesConfiguration = Seq.empty
      )
      Given(s""" a persisted operation "${operationForGetBySlug.translations.head.title}" """)
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

    scenario("modify a persisted operation") {

      val operationIdForModify: OperationId = OperationId(UUID.randomUUID().toString)
      val operationForModify: Operation = simpleOperation.copy(
        operationId = operationIdForModify,
        slug = "modify-operation",
        countriesConfiguration = Seq.empty
      )
      Given(s""" a persisted operation "${operationForModify.translations.head.title}" """)
      When("i get the modify operation")
      Then("the modification success")

      val futurePersistedOperation: Future[Operation] =
        persistentOperationService.persist(operation = operationForModify)

      whenReady(futurePersistedOperation, Timeout(3.seconds)) { initialOperation =>
        val waitingTime = 1000
        Thread.sleep(waitingTime) // needed to test updatedAt
        val futureMaybeOperation: Future[Option[Operation]] =
          persistentOperationService
            .modify(
              initialOperation.copy(
                status = OperationStatus.Active,
                defaultLanguage = "br",
                slug = "newSlug",
                translations = Seq(
                  OperationTranslation(title = "modify operation", language = "en"),
                  OperationTranslation(title = "modify operation br", language = "br"),
                  OperationTranslation(title = "modify operation it", language = "it")
                ),
                events = List(
                  OperationAction(
                    date = initialOperation.events.head.date,
                    makeUserId = userId,
                    actionType = OperationCreateAction.name,
                    arguments = Map("arg1" -> "valueArg1")
                  ),
                  OperationAction(
                    date = now,
                    makeUserId = userId,
                    actionType = OperationUpdateAction.name,
                    arguments = Map("arg2" -> "valueArg2")
                  )
                ),
                countriesConfiguration = Seq(
                  OperationCountryConfiguration(
                    countryCode = "BR",
                    tagIds = Seq.empty,
                    landingSequenceId = SequenceId("updatedSequenceId"),
                    startDate = None,
                    endDate = None
                  )
                )
              )
            )
            .flatMap { operation =>
              persistentOperationService.getById(operation.operationId)
            }

        whenReady(futureMaybeOperation, Timeout(3.seconds)) { maybeOperation =>
          val operation: Operation = maybeOperation.get
          operation should not be None
          operation shouldBe a[Operation]
          operation.operationId.value should be(operationForModify.operationId.value)
          operation.translations.length should be(3)
          operation.translations.filter(translation => translation.language == "en").head.title should be(
            "modify operation"
          )
          operation.translations.filter(translation => translation.language == "it").head.title should be(
            "modify operation it"
          )
          operation.translations.filter(translation => translation.language == "br").head.title should be(
            "modify operation br"
          )
          operation.events.length should be(2)
          operation.events.filter(event => event.actionType == OperationUpdateAction.name).head.arguments should be(
            Map("arg2" -> "valueArg2")
          )
          operation.slug should be("newSlug")
          operation.countriesConfiguration.find(_.countryCode == "BR").map(_.landingSequenceId.value) should be(
            Some("updatedSequenceId")
          )
          operation.defaultLanguage should be("br")
          initialOperation.createdAt.get.toEpochSecond should be(operation.createdAt.get.toEpochSecond)
          initialOperation.updatedAt.get.toEpochSecond should be < operation.updatedAt.get.toEpochSecond
          operation.countriesConfiguration.size should be(1)
          operation.countriesConfiguration.head.tagIds.size should be(0)
          operation.status.shortName should be(OperationStatus.Active.shortName)
        }
      }
    }
  }
}
