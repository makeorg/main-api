package org.make.api.idea

import org.make.api.DatabaseTest
import org.make.core.DateHelper
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.operation.OperationId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentIdeaServiceIT extends DatabaseTest with DefaultPersistentIdeaServiceComponent {

  val simpleIdea: Idea = Idea(
    IdeaId("foo-idea"),
    "fooIdea",
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now())
  )
  val completeIdea: Idea = Idea(
    ideaId = IdeaId("bar-idea"),
    name = "barIdea",
    language = Some("fr"),
    country = Some("FR"),
    operationId = Some(OperationId("operation")),
    question = Some("question"),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now())
  )

  feature("Can persist and retrieve ideas") {
    scenario("Persist a simple idea") {
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

    scenario("Persist a complete idea") {
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

    scenario("Get a list of all ideas") {
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
