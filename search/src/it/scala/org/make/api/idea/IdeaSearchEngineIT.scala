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

package org.make.api.idea

import org.make.api.MakeUnitTest
import org.make.api.docker.SearchEngineIT
import org.make.api.technical.elasticsearch.{
  DefaultElasticsearchClientComponent,
  ElasticsearchConfiguration,
  ElasticsearchConfigurationComponent
}
import org.make.core
import org.make.core.idea.indexed._
import org.make.core.idea.{IdeaId, IdeaSearchQuery, IdeaStatus}
import org.make.core.question.QuestionId
import org.make.core.{CirceFormatters, DateHelper, Order}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.collection.immutable.Seq
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import org.make.api.technical.ActorSystemComponent

class IdeaSearchEngineIT
    extends MakeUnitTest
    with CirceFormatters
    with SearchEngineIT[IdeaId, IndexedIdea]
    with DefaultIdeaSearchEngineComponent
    with DefaultElasticsearchClientComponent
    with ActorSystemComponent
    with ElasticsearchConfigurationComponent {

  override val StartContainersTimeout: FiniteDuration = 5.minutes

  override val eSIndexName: String = "ideaittest"
  override val eSDocType: String = "idea"
  override def docs: Seq[IndexedIdea] = ideas

  override val elasticsearchExposedPort: Int = 30001

  override val elasticsearchConfiguration: ElasticsearchConfiguration =
    mock[ElasticsearchConfiguration]
  when(elasticsearchConfiguration.connectionString).thenReturn(s"localhost:$elasticsearchExposedPort")
  when(elasticsearchConfiguration.ideaAliasName).thenReturn(eSIndexName)
  when(elasticsearchConfiguration.indexName).thenReturn(eSIndexName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    initializeElasticsearch(_.ideaId)
  }

  val ideasActivated: Seq[IndexedIdea] = Seq(
    IndexedIdea(
      ideaId = IdeaId("01"),
      name = "c-idea01",
      operationId = None,
      questionId = Some(QuestionId("question01")),
      question = Some("question01"),
      status = IdeaStatus.Activated,
      createdAt = DateHelper.now(),
      updatedAt = Some(DateHelper.now()),
      proposalsCount = 0
    ),
    IndexedIdea(
      ideaId = IdeaId("02"),
      name = "a-idea02",
      operationId = None,
      questionId = Some(QuestionId("question02")),
      question = Some("question02"),
      status = IdeaStatus.Activated,
      createdAt = DateHelper.now(),
      updatedAt = Some(DateHelper.now()),
      proposalsCount = 42
    ),
    IndexedIdea(
      ideaId = IdeaId("03"),
      name = "b-idea03",
      operationId = None,
      questionId = Some(QuestionId("question03")),
      question = Some("question03"),
      status = IdeaStatus.Activated,
      createdAt = DateHelper.now(),
      updatedAt = Some(DateHelper.now()),
      proposalsCount = 0
    )
  )

  private def ideas: Seq[IndexedIdea] = ideasActivated

  Feature("get idea list") {
    Scenario("get idea list ordered by name") {
      Given("""a list of idea named "a_idea02", "b_idea03" and "c_idea01" """)
      When("I get idea list ordered by name with an order desc")
      Then("""The result should be "a_idea02", "b_idea03" and "c_idea01" """)
      val ideaSearchQuery: IdeaSearchQuery = IdeaFiltersRequest.empty
        .copy(limit = Some(3), skip = Some(0), order = Some(Order.asc), sort = Some("name"))
        .toSearchQuery(core.RequestContext.empty)
      whenReady(elasticsearchIdeaAPI.searchIdeas(ideaSearchQuery), Timeout(5.seconds)) { result =>
        result.total should be(3)
        result.results(0).name should be("a-idea02")
        result.results(0).proposalsCount should be(42)
        result.results(1).name should be("b-idea03")
        result.results(2).name should be("c-idea01")
      }
    }
  }

}
