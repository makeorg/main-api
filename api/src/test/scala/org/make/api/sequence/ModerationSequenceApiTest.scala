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

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.testkit.TestDuration
import org.make.api.MakeApiTestBase
import org.make.core.auth.UserRights
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future
import scala.concurrent.duration._

class ModerationSequenceApiTest
    extends MakeApiTestBase
    with DefaultModerationSequenceApiComponent
    with SequenceServiceComponent
    with SequenceConfigurationComponent {

  override val sequenceService: SequenceService = mock[SequenceService]
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(3.seconds.dilated)

  val validAccessToken = "my-valid-access-token"
  val adminToken = "my-admin-access-token"
  val moderatorToken = "my-moderator-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(moderatorToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))

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

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = UserId("the-mother-of-dragons"),
              roles = Seq(RoleAdmin),
              availableQuestions = Seq.empty
            ),
            None,
            None,
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(userId = UserId("the-dwarf"), roles = Seq(RoleModerator), availableQuestions = Seq.empty),
            None,
            None,
            None
          )
        )
      )
    )

  val setSequenceConfigurationPayload: String = """{
                                                  |  "maxAvailableProposals": 1000,
                                                  |  "newProposalsRatio": 0.5,
                                                  |  "newProposalsVoteThreshold": 100,
                                                  |  "testedProposalsEngagementThreshold": 0.8,
                                                  |  "testedProposalsScoreThreshold": 0,
                                                  |  "testedProposalsControversyThreshold": 0,
                                                  |  "testedProposalsMaxVotesThreshold": 1500,
                                                  |  "banditEnabled": true,
                                                  |  "banditMinCount": 1,
                                                  |  "banditProposalsRatio": 0,
                                                  |  "ideaCompetitionEnabled": false,
                                                  |  "ideaCompetitionTargetCount": 50,
                                                  |  "ideaCompetitionControversialRatio": 0,
                                                  |  "ideaCompetitionControversialCount": 0,
                                                  |  "maxTestedProposalCount": 1000,
                                                  |  "sequenceSize": 12,
                                                  |  "maxVotes": 1500
                                                  |}""".stripMargin

  val routes: Route = sealRoute(moderationSequenceApi.routes)

  when(sequenceConfigurationService.getPersistentSequenceConfiguration(matches(SequenceId("unknownSequence"))))
    .thenReturn(Future.successful(None))

  when(sequenceConfigurationService.getPersistentSequenceConfiguration(matches(SequenceId("mySequence")))).thenReturn(
    Future.successful(
      Some(
        SequenceConfiguration(
          sequenceId = SequenceId("mySequence"),
          questionId = QuestionId("myQuestion"),
          newProposalsRatio = 0.5,
          newProposalsVoteThreshold = 100,
          testedProposalsEngagementThreshold = 0.8,
          testedProposalsScoreThreshold = 0.0,
          testedProposalsControversyThreshold = 0.0,
          banditEnabled = true,
          banditMinCount = 3,
          banditProposalsRatio = .3
        )
      )
    )
  )

  when(sequenceConfigurationService.setSequenceConfiguration(any[SequenceConfiguration]))
    .thenReturn(Future.successful(true))

  feature("get sequence configuration") {
    scenario("set sequence config as user") {
      Get("/moderation/sequences/mySequence/configuration")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get sequence config as moderator") {
      Get("/moderation/sequences/mySequence/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get sequence config as admin") {
      Get("/moderation/sequences/mySequence/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("get unknown sequence config as admin") {
      Get("/moderation/sequences/unknownSequence/unknownQuestion/configuration")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  feature("set sequence configuration") {
    scenario("set sequence config as user") {
      Put("/moderation/sequences/mySequence/myQuestion/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("set sequence config as moderator") {
      Put("/moderation/sequences/mySequence/myQuestion/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("set sequence config as admin") {
      Put("/moderation/sequences/mySequence/myQuestion/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }
}