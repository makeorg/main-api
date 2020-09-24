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

import java.util.UUID

import org.make.api.technical.security.SecurityHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait TokenGeneratorComponent {
  def tokenGenerator: TokenGenerator
}

trait TokenGenerator {
  val MAX_RETRY = 3

  def tokenToHash(token: String): String
  def generateToken(tokenExistsFunction: String => Future[Boolean], depth: Int = MAX_RETRY): Future[(String, String)]
  def newRandomToken(): (String, String)
}

trait DefaultTokenGeneratorComponent extends TokenGeneratorComponent {

  override lazy val tokenGenerator: TokenGenerator = new DefaultTokenGenerator

  class DefaultTokenGenerator extends TokenGenerator {

    override def tokenToHash(token: String): String =
      SecurityHelper.defaultHash(token).toUpperCase()

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    override def generateToken(
      tokenExistsFunction: String => Future[Boolean],
      depth: Int = MAX_RETRY
    ): Future[(String, String)] = {
      if (depth <= 0) {
        Future.failed(new RuntimeException("Token generation failed due to max retries reached."))
      } else {
        val (token, hashedToken): (String, String) = newRandomToken()
        tokenExistsFunction(hashedToken).flatMap { exists =>
          if (exists) {
            generateToken(tokenExistsFunction, depth - 1)
          } else {
            Future.successful((token, hashedToken))
          }
        }
      }
    }

    override def newRandomToken(): (String, String) = {
      val token = UUID.randomUUID().toString

      (token, tokenToHash(token))
    }
  }
}
