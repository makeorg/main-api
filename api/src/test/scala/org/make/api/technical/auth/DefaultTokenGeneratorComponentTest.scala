package org.make.api.technical.auth

import org.make.api.MakeUnitTest
import org.scalatest.time.{Seconds, Span}
import scala.concurrent.Future

class DefaultTokenGeneratorComponentTest extends MakeUnitTest with DefaultTokenGeneratorComponent {
  feature("Generate a hash from a token") {
    info("As a programmer")
    info("I want to be able to generate a hash from a Token")

    scenario("simple case") {
      Given("a list of tokens \"MYTOKEN\", \"TT\", \"@!\"'PZERzer10\"")
      When("tokenToHash is called for each")
      val tokens = Seq[String]("MYTOKEN", "TT", "@!\"'PZERzer10")
      val hashedToken = tokens.map(tokenGenerator.tokenToHash)
      Then("I should obtain a valid hashed value for each")
      hashedToken shouldBe Seq[String](
        "98FB8A3817F33D845CA98485C795F25528CB4FEB",
        "8C2408452CA428CDC3EE78C1B09AB347350250A8",
        "103064D906F0D0CF50744CF8BCB488FB1C8FF178"
      )
    }
  }

  feature("Generate a token") {
    info("As a programmer")
    info("I want to be able to generate a token and his hash version")

    scenario("simple case") {
      Given("a tokenExists Function")
      def tokenExists: (String) => Future[Boolean] = { _ =>
        Future.successful(false)
      }
      And("a tokenToHash method")
      When("generateToken is called")
      val futureToken: Future[(String, String)] = tokenGenerator.generateToken(tokenExists)
      Then("I should obtain a tuple with a new token and his hashed")
      whenReady(futureToken, timeout(Span(3, Seconds))) { maybeTokens =>
        val (mayBeToken, mayBeHashedToken) = maybeTokens
        (mayBeToken should have).length(36)
        (mayBeHashedToken should have).length(40)
      }
    }
  }

}
