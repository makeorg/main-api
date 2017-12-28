package org.make.api.idea

import org.make.api.DatabaseTest
import org.make.core.operation.OperationId
import org.make.core.reference.{Idea, IdeaId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentIdeaServiceIT extends DatabaseTest with DefaultPersistentIdeaServiceComponent {

  val simpleIdea: Idea = Idea(IdeaId("foo-idea"), "fooIdea")
  val completeIdea: Idea = Idea(
    ideaId = IdeaId("bar-idea"),
    name = "barIdea",
    language = Some("fr"),
    country = Some("FR"),
    operation = Some(OperationId("operation")),
    question = Some("question"),
    operationId = Some(OperationId("operation"))
  )

  feature("Can persist and retrieve ideas") {
    scenario("Persist a simple idea") {
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

    scenario("Get a list of all enabled ideas") {

      When("""I retrieve the idea list""")
      val futureIdeasLists: Future[Seq[Idea]] = persistentIdeaService.findAll(IdeaFiltersRequest.empty)

      whenReady(futureIdeasLists, Timeout(3.seconds)) { ideas =>
        Then("result should contain a list of ideas of foo and bar.")
        ideas.contains(simpleIdea) should be(true)
        ideas.contains(completeIdea) should be(true)
      }
    }
  }
}
