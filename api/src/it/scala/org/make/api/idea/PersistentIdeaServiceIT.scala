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

import org.make.api.DatabaseTest
import org.make.core.DateHelper
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.operation.OperationId
import org.make.core.reference.{Country, Language}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentIdeaServiceIT extends DatabaseTest with DefaultPersistentIdeaServiceComponent {

  override protected val cockroachExposedPort: Int = 40005

  val simpleIdea: Idea =
    Idea(IdeaId("foo-idea"), "fooIdea", createdAt = Some(DateHelper.now()), updatedAt = Some(DateHelper.now()))
  val completeIdea: Idea = Idea(
    ideaId = IdeaId("bar-idea"),
    name = "barIdea",
    language = Some(Language("fr")),
    country = Some(Country("FR")),
    operationId = Some(OperationId("operation")),
    question = Some("question"),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now())
  )

  Feature("Can persist and retrieve ideas") {
    Scenario("Persist a simple idea") {
      Given(s"""an idea with the id "${simpleIdea.ideaId.value}"""")
      When(s"""I persist the idea "${simpleIdea.name}"""")
      val futureIdea: Future[Idea] = persistentIdeaService.persist(simpleIdea)

      whenReady(futureIdea, Timeout(3.seconds)) { idea =>
        Then("result should be an instance of idea")
        idea shouldBe a[Idea]

        And("the idea name must be fooIdea")
        idea.name shouldBe "fooIdea"

        And("the idea id must be foo-idea")
        idea.ideaId.value shouldBe "foo-idea"
      }
    }

    Scenario("Persist a complete idea") {
      Given(s"""an idea with the id "${completeIdea.ideaId.value}"""")
      When(s"""I persist the idea "${completeIdea.name}"""")
      val futureIdea: Future[Idea] = persistentIdeaService.persist(completeIdea)

      whenReady(futureIdea, Timeout(3.seconds)) { idea =>
        Then("result should be an instance of idea")
        idea shouldBe a[Idea]

        And("the idea name must be barIdea")
        idea.name shouldBe "barIdea"

        And("the idea id must be foo-idea")
        idea.ideaId.value shouldBe "bar-idea"
      }
    }

    Scenario("Get a list of all ideas") {
      Given("I ve persisted two idea")
      When("I retrieve the idea list")
      val futureIdeasLists: Future[Seq[Idea]] = persistentIdeaService.findAll(IdeaFiltersRequest.empty)

      whenReady(futureIdeasLists, Timeout(3.seconds)) { ideas =>
        Then("result size should be two")
        ideas.size should be(2)
      }
    }
  }
}
