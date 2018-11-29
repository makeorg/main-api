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

package org.make.api.widget

import java.time.ZonedDateTime
import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.proposal._
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{ActorSystemComponent, MakeApiTestBase}
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.UserId
import org.mockito.Mockito.when
import org.mockito.{ArgumentMatchers, Mockito}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class WidgetApiTest
    extends MakeApiTestBase
    with WidgetApi
    with WidgetServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ProposalServiceComponent
    with ActorSystemComponent
    with UserServiceComponent
    with IdeaServiceComponent {

  override val proposalService: ProposalService = mock[ProposalService]
  override val userService: UserService = mock[UserService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val widgetService: WidgetService = mock[WidgetService]

  val routes: Route = sealRoute(widgetRoutes)
  val validAccessToken = "my-valid-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.eq(accessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(userId = UserId("my-user-id"), roles = Seq(RoleCitizen)), None, Some("user"), None))
      )
    )

  feature("get widget sequence of an operation") {
    scenario("Get proposal of an operation successfully") {
      Mockito
        .when(
          widgetService.startNewWidgetSequence(
            ArgumentMatchers.any[Option[UserId]],
            ArgumentMatchers.eq(OperationId("foo-operation-id")),
            ArgumentMatchers.any[Option[Seq[TagId]]],
            ArgumentMatchers.any[Option[Country]],
            ArgumentMatchers.any[Option[Int]],
            ArgumentMatchers.any[RequestContext]
          )
        )
        .thenReturn(Future.successful(ProposalsResultSeededResponse(total = 0, results = Seq.empty, seed = None)))

      Get("/widget/operations/foo-operation-id/start-sequence")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsSearchResult]
        result.total should be(0)
        result.results should be(Seq.empty)
      }
    }

    scenario("Get proposal of an operation filtred by tag successfully") {
      Mockito
        .when(
          widgetService.startNewWidgetSequence(
            ArgumentMatchers.eq(Some(UserId("my-user-id"))),
            ArgumentMatchers.eq(OperationId("foo-operation-id")),
            ArgumentMatchers.eq(Some(Seq(TagId("foo-tag-id"), TagId("bar-tag-id")))),
            ArgumentMatchers.any[Option[Country]],
            ArgumentMatchers.any[Option[Int]],
            ArgumentMatchers.any[RequestContext]
          )
        )
        .thenReturn(
          Future.successful(
            ProposalsResultSeededResponse(
              total = 1,
              results = Seq(
                ProposalResult(
                  indexedProposal = IndexedProposal(
                    id = ProposalId("foo-proposal-id"),
                    userId = UserId("foo-user-id"),
                    content = "il faut proposer sur le widget",
                    slug = "il-faut-proposer-sur-le-widget",
                    status = ProposalStatus.Accepted,
                    createdAt = ZonedDateTime.now,
                    updatedAt = Some(ZonedDateTime.now),
                    votesCount = 0,
                    toEnrich = false,
                    votes = Seq.empty,
                    scores = IndexedScores.empty,
                    context = None,
                    trending = None,
                    labels = Seq.empty,
                    author = Author(
                      firstName = Some("Foo Foo"),
                      organisationName = None,
                      organisationSlug = None,
                      postalCode = None,
                      age = None,
                      avatarUrl = None
                    ),
                    organisations = Seq.empty,
                    themeId = None,
                    questionId = None,
                    tags = Seq.empty,
                    ideaId = None,
                    operationId = None,
                    country = Country("FR"),
                    language = Language("fr"),
                    sequencePool = SequencePool.New
                  ),
                  myProposal = false,
                  voteAndQualifications = None
                )
              ),
              seed = None
            )
          )
        )

      Get("/widget/operations/foo-operation-id/start-sequence?tagsIds=foo-tag-id,bar-tag-id")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsResultSeededResponse]
        result.total should be(1)
        result.results.head shouldBe a[ProposalResult]
      }
    }
  }
}
