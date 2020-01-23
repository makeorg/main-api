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

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.testkit.TestDuration
import org.make.api.MakeApiTestBase
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._

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

  val setSequenceConfigurationPayload: String = """{
                                                  |  "maxAvailableProposals": 1000,
                                                  |  "newProposalsRatio": 0.5,
                                                  |  "newProposalsVoteThreshold": 100,
                                                  |  "testedProposalsEngagementThreshold": 0.8,
                                                  |  "testedProposalsScoreThreshold": 0,
                                                  |  "testedProposalsControversyThreshold": 0,
                                                  |  "testedProposalsMaxVotesThreshold": 1500,
                                                  |  "intraIdeaEnabled": true,
                                                  |  "intraIdeaMinCount": 1,
                                                  |  "intraIdeaProposalsRatio": 0,
                                                  |  "interIdeaCompetitionEnabled": false,
                                                  |  "interIdeaCompetitionTargetCount": 50,
                                                  |  "interIdeaCompetitionControversialRatio": 0,
                                                  |  "interIdeaCompetitionControversialCount": 0,
                                                  |  "maxTestedProposalCount": 1000,
                                                  |  "sequenceSize": 12,
                                                  |  "maxVotes": 1500,
                                                  |  "selectionAlgorithmName": "Bandit",
                                                  |  "nonSequenceVotesWeight": 0.5,
                                                  |  "scoreAdjustementVotesThreshold": 100,
                                                  |  "scoreAdjustementFactor": 1000
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
          testedProposalsEngagementThreshold = Some(0.8),
          testedProposalsScoreThreshold = Some(0.0),
          testedProposalsControversyThreshold = Some(0.0),
          intraIdeaEnabled = true,
          intraIdeaMinCount = 3,
          intraIdeaProposalsRatio = .3
        )
      )
    )
  )

  when(sequenceConfigurationService.setSequenceConfiguration(any[SequenceConfiguration]))
    .thenReturn(Future.successful(true))

  feature("get sequence configuration") {
    scenario("set sequence config as user") {
      Get("/moderation/sequences/mySequence/configuration")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get sequence config as moderator") {
      Get("/moderation/sequences/mySequence/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get sequence config as admin") {
      Get("/moderation/sequences/mySequence/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("get unknown sequence config as admin") {
      Get("/moderation/sequences/unknownSequence/unknownQuestion/configuration")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  feature("set sequence configuration") {
    scenario("set sequence config as user") {
      Put("/moderation/sequences/mySequence/myQuestion/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("set sequence config as moderator") {
      Put("/moderation/sequences/mySequence/myQuestion/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("set sequence config as admin") {
      Put("/moderation/sequences/mySequence/myQuestion/configuration")
        .withEntity(HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("set sequence config as admin with wrong selectionAlgorithmName") {
      Put("/moderation/sequences/mySequence/myQuestion/configuration")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, setSequenceConfigurationPayload.replaceFirst("Bandit", "invalid"))
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }
}
