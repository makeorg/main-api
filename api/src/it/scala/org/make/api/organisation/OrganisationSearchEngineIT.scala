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

package org.make.api.organisation

import akka.actor.ActorSystem
import org.make.api.{ActorSystemComponent, ItMakeTest}
import org.make.api.docker.SearchEngineIT
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.core.{CirceFormatters, Order}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.indexed.{IndexedOrganisation, ProposalsAndVotesCountsByQuestion}
import org.make.core.user._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class OrganisationSearchEngineIT
    extends ItMakeTest
    with CirceFormatters
    with SearchEngineIT[UserId, IndexedOrganisation]
    with DefaultOrganisationSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with DefaultElasticsearchClientComponent
    with ActorSystemComponent {

  override val actorSystem: ActorSystem = ActorSystem(getClass.getSimpleName)
  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val eSIndexName: String = "organisation-it-test"
  override val eSDocType: String = "organisation"
  override def docs: Seq[IndexedOrganisation] = organisations

  override val elasticsearchExposedPort: Int = 30003

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  when(elasticsearchConfiguration.organisationAliasName).thenReturn(eSIndexName)
  when(elasticsearchConfiguration.indexName).thenReturn(eSIndexName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch(_.organisationId)
  }

  val organisations: immutable.Seq[IndexedOrganisation] = immutable.Seq(
    IndexedOrganisation(
      organisationId = UserId("orga-a"),
      organisationName = Some("corp A"),
      slug = Some("corp-a"),
      avatarUrl = Some("http://image-corp-a.net"),
      description = Some("long text for corp A"),
      publicProfile = true,
      proposalsCount = 42,
      votesCount = 70,
      language = Language("fr"),
      country = Country("FR"),
      website = Some("http://example.com"),
      countsByQuestion = Seq(ProposalsAndVotesCountsByQuestion(QuestionId("question-id-1"), 42, 70))
    ),
    IndexedOrganisation(
      organisationId = UserId("orga-b"),
      organisationName = Some("corp B"),
      slug = Some("corp-b"),
      avatarUrl = Some("http://image-corp-b.net"),
      description = Some("long text for corp B"),
      publicProfile = true,
      proposalsCount = 0,
      votesCount = 0,
      language = Language("fr"),
      country = Country("FR"),
      website = Some("http://example.com"),
      countsByQuestion = Seq.empty
    ),
    IndexedOrganisation(
      organisationId = UserId("orga-c"),
      organisationName = Some("corp C"),
      slug = Some("corp-c"),
      avatarUrl = Some("http://image-corp-c.net"),
      description = Some("long text for corp C"),
      publicProfile = true,
      proposalsCount = 4321,
      votesCount = 420123,
      language = Language("fr"),
      country = Country("FR"),
      website = Some("http://example.com"),
      countsByQuestion = Seq(
        ProposalsAndVotesCountsByQuestion(QuestionId("question-id-1"), 121, 123),
        ProposalsAndVotesCountsByQuestion(QuestionId("question-id-2"), 4200, 420000)
      )
    ),
    IndexedOrganisation(
      organisationId = UserId("orga-accent"),
      organisationName = Some("corp aînés français"),
      slug = Some("corp-french"),
      avatarUrl = Some("http://image-corp-french.net"),
      description = Some("long text for corp french"),
      publicProfile = true,
      proposalsCount = 228,
      votesCount = 1000,
      language = Language("fr"),
      country = Country("FR"),
      website = Some("http://example.com"),
      countsByQuestion = Seq(
        ProposalsAndVotesCountsByQuestion(QuestionId("question-id-1"), 100, 500),
        ProposalsAndVotesCountsByQuestion(QuestionId("question-id-2"), 100, 499),
        ProposalsAndVotesCountsByQuestion(QuestionId("question-id-3"), 28, 1)
      )
    )
  )

  Feature("get organisation list") {
    Scenario("get organisation list ordered by name") {
      Given("""a list of organisation named "corp A", "corp B" and "corp C" """)
      When("I get organisation list ordered by name with an order desc")
      Then("""The result should be "corp A", "corp B" and "corp C" """)
      val organisationSearchQuery: OrganisationSearchQuery =
        OrganisationSearchQuery(
          limit = Some(3),
          skip = Some(0),
          order = Some(Order.asc),
          sort = Some("organisationName")
        )
      whenReady(elasticsearchOrganisationAPI.searchOrganisations(organisationSearchQuery), Timeout(5.seconds)) {
        result =>
          result.total shouldBe 4
          result.results.head.organisationName shouldBe Some("corp A")
          result.results(1).organisationName shouldBe Some("corp B")
          result.results(2).organisationName shouldBe Some("corp C")
      }
    }

    Scenario("search by name with accent") {
      val organisationSearchQuery: OrganisationSearchQuery =
        OrganisationSearchQuery(filters =
          Some(OrganisationSearchFilters(organisationName = Some(OrganisationNameSearchFilter("aines"))))
        )
      whenReady(elasticsearchOrganisationAPI.searchOrganisations(organisationSearchQuery), Timeout(5.seconds)) {
        result =>
          result.total should be > 0L
          result.results.exists(_.slug.contains("corp-french")) shouldBe true
      }
    }
  }

  Feature("find organisation by slug") {
    Scenario("find one result") {
      whenReady(elasticsearchOrganisationAPI.findOrganisationBySlug("corp-b"), Timeout(5.seconds)) { result =>
        result.isDefined shouldBe true
        result.get.organisationName shouldBe Some("corp B")
      }
    }

    Scenario("fnid zero result") {
      whenReady(elasticsearchOrganisationAPI.findOrganisationBySlug("fake"), Timeout(5.seconds)) { result =>
        result.isDefined shouldBe false
      }
    }
  }

  Feature("sort organisation with sortAlgorithm") {
    Scenario("participation algorithm") {
      whenReady(
        elasticsearchOrganisationAPI.searchOrganisations(
          OrganisationSearchQuery(sortAlgorithm = Some(ParticipationAlgorithm(QuestionId("question-id-1"))))
        ),
        Timeout(5.seconds)
      ) { result =>
        result.total shouldBe organisations.size.toLong - 1
        result.results.head.organisationId shouldBe UserId("orga-c")
        result.results(1).organisationId shouldBe UserId("orga-accent")
        result.results.exists(_.organisationId.value == "orga-b") shouldBe false
      }
    }
  }
}
