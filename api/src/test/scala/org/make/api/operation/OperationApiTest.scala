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

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.technical.CountryHeader
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId
import org.mockito.Mockito._

import scala.concurrent.Future

class OperationApiTest extends MakeApiTestBase with OperationApi with OperationServiceComponent {

  override val operationService: OperationService = mock[OperationService]

  val routes: Route = sealRoute(operationRoutes)
  val userId: UserId = UserId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()

  val firstOperation: Operation = Operation(
    status = OperationStatus.Pending,
    operationId = OperationId("firstOperation"),
    slug = "first-operation",
    translations = Seq(
      OperationTranslation(title = "première operation", language = Language("fr")),
      OperationTranslation(title = "first operation", language = Language("en"))
    ),
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
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    countriesConfiguration = Seq(
      OperationCountryConfiguration(
        countryCode = Country("BR"),
        tagIds = Seq.empty,
        landingSequenceId = SequenceId("first-sequence-id-BR"),
        startDate = None,
        endDate = None,
        questionId = None
      ),
      OperationCountryConfiguration(
        countryCode = Country("GB"),
        tagIds = Seq.empty,
        landingSequenceId = SequenceId("first-sequence-id-GB"),
        startDate = None,
        endDate = None,
        questionId = None
      )
    )
  )

  val secondOperation: Operation = Operation(
    status = OperationStatus.Pending,
    operationId = OperationId("secondOperation"),
    slug = "second-operation",
    translations = Seq(
      OperationTranslation(title = "secondo operazione", language = Language("it")),
      OperationTranslation(title = "second operation", language = Language("en"))
    ),
    defaultLanguage = Language("it"),
    allowedSources = Seq("core"),
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
    countriesConfiguration = Seq(
      OperationCountryConfiguration(
        countryCode = Country("IT"),
        tagIds = Seq.empty,
        landingSequenceId = SequenceId("second-sequence-id"),
        startDate = None,
        endDate = None,
        questionId = None
      )
    )
  )

  when(operationService.findOne(OperationId("firstOperation"))).thenReturn(Future.successful(Some(firstOperation)))
  when(operationService.findOne(OperationId("fakeid"))).thenReturn(Future.successful(None))
  when(operationService.find(slug = None, country = None, maybeSource = None, openAt = None))
    .thenReturn(Future.successful(Seq(firstOperation, secondOperation)))
  when(operationService.find(slug = Some("second-operation"), country = None, maybeSource = None, openAt = None))
    .thenReturn(Future.successful(Seq(secondOperation)))
  when(operationService.find(slug = Some("first-operation"), country = None, maybeSource = None, openAt = None))
    .thenReturn(Future.successful(Seq(firstOperation)))

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
        operationResponseList.head.translations.filter(_.language == Language("it")).head.title should be(
          "secondo operazione"
        )
        operationResponseList.head.translations.filter(_.language == Language("en")).head.title should be(
          "second operation"
        )
        operationResponseList.head.defaultLanguage should be(Language("it"))
        operationResponseList.head.sequenceLandingId.value should be(
          secondOperation.countriesConfiguration.head.landingSequenceId.value
        )
        operationResponseList.head.createdAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponseList.head.updatedAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponseList.head.countriesConfiguration.length should be(1)
        operationResponseList.head.countriesConfiguration.head.countryCode should be(Country("IT"))
        operationResponseList.head.countriesConfiguration.head.tagIds should be(Seq.empty)
      }
    }

    scenario("get an operation by id") {
      Given("2 registered operations and one of them with an id 'firstOperation' ")
      When("I get a proposal from id 'firstOperation'")
      Then("I get 1 operation")
      And("the Operation is the firstOperation")
      And("the landing sequence id is the head of country configuration")
      Get("/operations/firstOperation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val operationResponse: OperationResponse = entityAs[OperationResponse]
        operationResponse.slug should be(firstOperation.slug)
        operationResponse.operationId.value should be(firstOperation.operationId.value)
        operationResponse.translations.filter(_.language == Language("fr")).head.title should be("première operation")
        operationResponse.translations.filter(_.language == Language("en")).head.title should be("first operation")
        operationResponse.defaultLanguage should be(Language("fr"))
        operationResponse.sequenceLandingId.value should be(
          firstOperation.countriesConfiguration.head.landingSequenceId.value
        )
        operationResponse.createdAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponse.updatedAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponse.countriesConfiguration.length should be(2)
        operationResponse.countriesConfiguration.head.countryCode should be(Country("BR"))
        operationResponse.countriesConfiguration.head.tagIds should be(Seq.empty)
      }
    }

    scenario("get an operation by id and country") {
      Given("2 registered operations and one of them with an id 'firstOperation' ")
      When("I get a proposal from id 'firstOperation' with country header set as 'GB'")
      Then("I get 1 operation")
      And("the Operation is the firstOperation")
      And("the landing sequence id is the one defined by the users country")
      Get("/operations/firstOperation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(CountryHeader("GB")) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val operationResponse: OperationResponse = entityAs[OperationResponse]
        operationResponse.slug should be(firstOperation.slug)
        operationResponse.operationId.value should be(firstOperation.operationId.value)
        operationResponse.translations.filter(_.language == Language("fr")).head.title should be("première operation")
        operationResponse.translations.filter(_.language == Language("en")).head.title should be("first operation")
        operationResponse.defaultLanguage should be(Language("fr"))
        operationResponse.sequenceLandingId.value should be("first-sequence-id-GB")
        operationResponse.createdAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponse.updatedAt.get.toEpochSecond should be(now.toEpochSecond)
        operationResponse.countriesConfiguration.length should be(2)
        operationResponse.countriesConfiguration.head.countryCode should be(Country("BR"))
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
