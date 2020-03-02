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
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
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

  val sequenceConfiguration: SequenceConfiguration = SequenceConfiguration(
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

  when(sequenceConfigurationService.getPersistentSequenceConfiguration(matches(SequenceId("myQuestion"))))
    .thenReturn(Future.successful(None))

  when(sequenceConfigurationService.getPersistentSequenceConfiguration(matches(SequenceId("unknown"))))
    .thenReturn(Future.successful(None))

  when(sequenceConfigurationService.getPersistentSequenceConfiguration(matches(SequenceId("mySequence"))))
    .thenReturn(Future.successful(Some(sequenceConfiguration)))

  when(sequenceConfigurationService.getPersistentSequenceConfigurationByQuestionId(any[QuestionId]))
    .thenReturn(Future.successful(None))

  when(sequenceConfigurationService.getPersistentSequenceConfigurationByQuestionId(matches(QuestionId("myQuestion"))))
    .thenReturn(Future.successful(Some(sequenceConfiguration)))

  when(sequenceConfigurationService.setSequenceConfiguration(any[SequenceConfiguration]))
    .thenReturn(Future.successful(true))

  feature("get sequence configuration") {

    def testSequenceConfigurationReadAccess(
      by: String,
      as: String,
      id: String,
      token: String,
      expected: StatusCode
    ): Unit =
      scenario(s"get sequence config by $by id as $as") {
        Get(s"/moderation/sequences/$id/configuration")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(expected)
        }
      }

    testSequenceConfigurationReadAccess("sequence", "user", "mySequence", tokenCitizen, StatusCodes.Forbidden)
    testSequenceConfigurationReadAccess("question", "user", "myQuestion", tokenCitizen, StatusCodes.Forbidden)
    testSequenceConfigurationReadAccess("sequence", "moderator", "mySequence", tokenModerator, StatusCodes.Forbidden)
    testSequenceConfigurationReadAccess("question", "moderator", "myQuestion", tokenModerator, StatusCodes.Forbidden)
    testSequenceConfigurationReadAccess("sequence", "admin", "mySequence", tokenAdmin, StatusCodes.OK)
    testSequenceConfigurationReadAccess("question", "admin", "myQuestion", tokenAdmin, StatusCodes.OK)
    testSequenceConfigurationReadAccess("unknown", "admin", "unknown", tokenAdmin, StatusCodes.NotFound)

  }

  feature("set sequence configuration") {

    def testSequenceConfigurationWriteAccess(
      by: String,
      as: String,
      id: String,
      token: String,
      expected: StatusCode,
      payload: String = setSequenceConfigurationPayload
    ): Unit =
      scenario(s"set sequence config by $by id as $as") {
        Put(s"/moderation/sequences/$id/configuration")
          .withEntity(HttpEntity(ContentTypes.`application/json`, payload))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(expected)
        }
      }

    // new route using only question id
    testSequenceConfigurationWriteAccess("question", "user", "myQuestion", tokenCitizen, StatusCodes.Forbidden)
    testSequenceConfigurationWriteAccess("question", "moderator", "myQuestion", tokenModerator, StatusCodes.Forbidden)
    testSequenceConfigurationWriteAccess("question", "admin", "myQuestion", tokenAdmin, StatusCodes.OK)
    testSequenceConfigurationWriteAccess(
      "question",
      "admin with wrong selectionAlgorithmName",
      "myQuestion",
      tokenAdmin,
      StatusCodes.BadRequest,
      setSequenceConfigurationPayload.replaceFirst("Bandit", "invalid")
    )

    // deprecated route using sequence and question id
    testSequenceConfigurationWriteAccess(
      "sequence and question",
      "user",
      "mySequence/myQuestion",
      tokenCitizen,
      StatusCodes.Forbidden
    )
    testSequenceConfigurationWriteAccess(
      "sequence and question",
      "moderator",
      "mySequence/myQuestion",
      tokenModerator,
      StatusCodes.Forbidden
    )
    testSequenceConfigurationWriteAccess(
      "sequence and question",
      "admin",
      "mySequence/myQuestion",
      tokenAdmin,
      StatusCodes.OK
    )
    testSequenceConfigurationWriteAccess(
      "sequence and question",
      "admin with wrong selectionAlgorithmName",
      "mySequence/myQuestion",
      tokenAdmin,
      StatusCodes.BadRequest,
      setSequenceConfigurationPayload.replaceFirst("Bandit", "invalid")
    )

  }
}
