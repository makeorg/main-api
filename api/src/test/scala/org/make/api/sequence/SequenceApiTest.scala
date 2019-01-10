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

package org.make.api.sequence

import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.testkit.TestDuration
import org.make.api.MakeApiTestBase
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.proposal.ProposalId
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future
import scala.concurrent.duration._

class SequenceApiTest extends MakeApiTestBase with DefaultSequenceApiComponent with SequenceServiceComponent {

  override val sequenceService: SequenceService = mock[SequenceService]

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(3.seconds.dilated)

  val validAccessToken = "my-valid-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(userId = UserId("my-user-id"), roles = Seq(RoleCitizen), availableQuestions = Seq.empty),
            None,
            Some("user"),
            None
          )
        )
      )
    )

  val routes: Route = sealRoute(sequenceApi.routes)

  when(
    sequenceService.startNewSequence(
      any[Option[UserId]],
      ArgumentMatchers.eq(SequenceId("start-sequence-by-id")),
      any[Seq[ProposalId]],
      any[Option[Seq[TagId]]],
      any[RequestContext]
    )
  ).thenReturn(
    Future.successful(
      Some(
        SequenceResult(
          id = SequenceId("start-sequence-by-id"),
          title = "sequence search",
          slug = "start-sequence-by-id",
          proposals = Seq.empty
        )
      )
    )
  )

  when(
    sequenceService.startNewSequence(
      any[Option[UserId]],
      ArgumentMatchers.eq(SequenceId("non-existing-sequence")),
      any[Seq[ProposalId]],
      any[Option[Seq[TagId]]],
      any[RequestContext]
    )
  ).thenReturn(Future.successful(None))

  feature("sequence start by id") {
    scenario("unauthenticated user") {
      Get("/sequences/start/start-sequence-by-id") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("valid request") {
      Get("/sequences/start/start-sequence-by-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("non existing sequence") {
      Get("/sequences/start/non-existing-sequence") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
