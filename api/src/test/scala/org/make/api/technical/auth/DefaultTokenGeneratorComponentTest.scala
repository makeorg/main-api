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

package org.make.api.technical.auth

import org.make.api.MakeUnitTest
import org.scalatest.time.{Seconds, Span}
import scala.concurrent.Future

class DefaultTokenGeneratorComponentTest extends MakeUnitTest with DefaultTokenGeneratorComponent {
  Feature("Generate a hash from a token") {
    info("As a programmer")
    info("I want to be able to generate a hash from a Token")

    Scenario("simple case") {
      Given("a list of tokens \"MYTOKEN\", \"TT\", \"@!\"'PZERzer10\"")
      When("tokenToHash is called for each")
      val tokens = Seq[String]("MYTOKEN", "TT", "@!\"'PZERzer10")
      val hashedToken = tokens.map(tokenGenerator.tokenToHash)
      Then("I should obtain a valid hashed value for each")
      hashedToken shouldBe Seq[String](
        "3AA87EF2C671503A67BD3A88B2B50A1FFFF408C664ECF997FCB65B456B102FE225B2C0D33DD555AFCFA3C430135D4B7DCD0AC435F68B8859B4037B4F1D690EC9",
        "845835C45B2479301FFA03753204367E91B25E182055B130E96B0AC22637ABDBFA2BD19BFE9580D10F7ABC2D903A748C8F2D2B98379F0AB7ED8C77346CA87936",
        "2BC7502FC906E235020155241D4BE1129D1B3264AF45E115754ED3B803300329A02785F984DA7B3948B9EE7293DF03A107E817C66EF2C5AB3B5431C1EF5E988E"
      )
    }
  }

  Feature("Generate a token") {
    info("As a programmer")
    info("I want to be able to generate a token and his hash version")

    Scenario("simple case") {
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
        (mayBeHashedToken should have).length(128)
      }
    }
  }

}
