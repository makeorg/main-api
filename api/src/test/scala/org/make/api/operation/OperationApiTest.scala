package org.make.api.operation

import java.time.ZonedDateTime
import java.util.UUID

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId
import org.mockito.Mockito._

import scala.concurrent.Future
import scala.concurrent.duration.Duration

class OperationApiTest
    extends MakeApiTestUtils
    with OperationApi
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with OperationServiceComponent
    with MakeSettingsComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val operationService: OperationService = mock[OperationService]
  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  val routes: Route = sealRoute(operationRoutes)
  val userId: UserId = UserId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()

  val firstOperation: Operation = Operation(
    status = OperationStatus.Pending,
    operationId = OperationId("firstOperation"),
    slug = "first-operation",
    translations = Seq(
      OperationTranslation(title = "première operation", language = "fr"),
      OperationTranslation(title = "first operation", language = "en")
    ),
    defaultLanguage = "fr",
    sequenceLandingId = SequenceId("first-sequence-id"),
    events = List(
      OperationAction(
        date = now,
        makeUserId = userId,
        actionType = OperationCreateAction.name,
        arguments = Map("arg1" -> "valueArg1")
      )
    ),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    countriesConfiguration = Seq(OperationCountryConfiguration(countryCode = "BR", tagIds = Seq.empty))
  )

  val secondOperation: Operation = Operation(
    status = OperationStatus.Pending,
    operationId = OperationId("secondOperation"),
    slug = "second-operation",
    translations = Seq(
      OperationTranslation(title = "secondo operazione", language = "it"),
      OperationTranslation(title = "second operation", language = "en")
    ),
    defaultLanguage = "it",
    sequenceLandingId = SequenceId("second-sequence-id"),
    events = List(
      OperationAction(
        date = now,
        makeUserId = userId,
        actionType = OperationCreateAction.name,
        arguments = Map("arg1" -> "valueArg1")
      )
    ),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    countriesConfiguration = Seq(OperationCountryConfiguration(countryCode = "IT", tagIds = Seq.empty))
  )

  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(idGenerator.nextId()).thenReturn("next-id")

  when(operationService.findOne(OperationId("firstOperation"))).thenReturn(Future.successful(Some(firstOperation)))
  when(operationService.findOne(OperationId("fakeid"))).thenReturn(Future.successful(None))
  when(operationService.find(None)).thenReturn(Future.successful(Seq(firstOperation, secondOperation)))
  when(operationService.find(Some("second-operation"))).thenReturn(Future.successful(Seq(secondOperation)))
  when(operationService.find(Some("first-operation"))).thenReturn(Future.successful(Seq(firstOperation)))

  feature("get operations") {
    scenario("get all operations") {
      Given("2 registered operations")
      When("I get all proposals")
      Then("I get a list of 2 operations")
      Get("/operations").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val operationResponseList: Seq[OperationResponse] = entityAs[Seq[OperationResponse]]
        operationResponseList.length should be(2)
      }
    }

    scenario("get an operation by slug") {
      Given("2 registered operations and one of them with a slug 'second-operation' ")
      When("I get a proposal from slug 'second-operation' ")
      Then("I get 1 operation")
      And("the Operation is the second-operation")
      Get("/operations?slug=second-operation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val operationResponseList: Seq[OperationResponse] = entityAs[Seq[OperationResponse]]
        operationResponseList.length should be(1)
        operationResponseList.head.slug should be(secondOperation.slug)
        operationResponseList.head.operationId.value should be(secondOperation.operationId.value)
        operationResponseList.head.translations.filter(_.language == "it").head.title should be("secondo operazione")
        operationResponseList.head.translations.filter(_.language == "en").head.title should be("second operation")
        operationResponseList.head.defaultLanguage should be("it")
        operationResponseList.head.sequenceLandingId.value should be(secondOperation.sequenceLandingId.value)
        operationResponseList.head.createdAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponseList.head.updatedAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponseList.head.countriesConfiguration.length should be(1)
        operationResponseList.head.countriesConfiguration.head.countryCode should be("IT")
        operationResponseList.head.countriesConfiguration.head.tagIds should be(Seq.empty)
      }
    }

    scenario("get an operation by id") {
      Given("2 registered operations and one of them with an id 'firstOperation' ")
      When("I get a proposal from id 'firstOperation' ")
      Then("I get 1 operation")
      And("the Operation is the firstOperation")
      Get("/operations/firstOperation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val operationResponse: OperationResponse = entityAs[OperationResponse]
        operationResponse.slug should be(firstOperation.slug)
        operationResponse.operationId.value should be(firstOperation.operationId.value)
        operationResponse.translations.filter(_.language == "fr").head.title should be("première operation")
        operationResponse.translations.filter(_.language == "en").head.title should be("first operation")
        operationResponse.defaultLanguage should be("fr")
        operationResponse.sequenceLandingId.value should be(firstOperation.sequenceLandingId.value)
        operationResponse.createdAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponse.updatedAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponse.countriesConfiguration.length should be(1)
        operationResponse.countriesConfiguration.head.countryCode should be("BR")
        operationResponse.countriesConfiguration.head.tagIds should be(Seq.empty)
      }
    }

    scenario("get an non existent operation by id") {
      Given("2 registered operations")
      When("I get a proposal from a non existent id 'fakeid' ")
      Then("I get a not found status")
      Get("/operations/fakeid")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
