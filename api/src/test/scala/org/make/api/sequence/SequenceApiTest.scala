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

import java.time.ZonedDateTime
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.testkit.TestDuration
import org.make.api.MakeApiTestBase
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.core.auth.UserRights
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, Theme, ThemeId}
import org.make.core.sequence.indexed.SequencesSearchResult
import org.make.core.sequence.{Sequence, SequenceId, SequenceStatus}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future
import scala.concurrent.duration._

class SequenceApiTest
    extends MakeApiTestBase
    with SequenceApi
    with SequenceServiceComponent
    with ThemeServiceComponent
    with OperationServiceComponent
    with SequenceConfigurationComponent {

  override val sequenceService: SequenceService = mock[SequenceService]
  override val themeService: ThemeService = mock[ThemeService]
  override val operationService: OperationService = mock[OperationService]
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]

  val CREATED_DATE_SECOND_MINUS: Int = 10
  val mainCreatedAt: Option[ZonedDateTime] = Some(DateHelper.now().minusSeconds(CREATED_DATE_SECOND_MINUS))
  val mainUpdatedAt: Option[ZonedDateTime] = Some(DateHelper.now())

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(3.seconds.dilated)

  when(themeService.findAll()).thenReturn(
    Future.successful(
      Seq(
        Theme(
          themeId = ThemeId("123"),
          questionId = Some(QuestionId("123")),
          translations = Seq.empty,
          actionsCount = 0,
          proposalsCount = 0,
          votesCount = 0,
          country = Country("FR"),
          color = "#123123",
          gradient = None,
          tags = Seq.empty
        )
      )
    )
  )
  when(themeService.findByIds(matches(Seq(ThemeId("123"))))).thenReturn(
    Future.successful(
      Seq(
        Theme(
          themeId = ThemeId("123"),
          questionId = Some(QuestionId("123")),
          translations = Seq.empty,
          actionsCount = 0,
          proposalsCount = 0,
          votesCount = 0,
          country = Country("FR"),
          color = "#123123",
          gradient = None,
          tags = Seq.empty
        )
      )
    )
  )
  when(themeService.findByIds(matches(Seq.empty))).thenReturn(Future.successful(Seq.empty))
  when(themeService.findByIds(matches(Seq(ThemeId("badthemeid"))))).thenReturn(Future.successful(Seq.empty))

  val myTag = Tag(
    tagId = TagId("mytag"),
    label = "mytag",
    display = TagDisplay.Inherit,
    weight = 0f,
    tagTypeId = TagTypeId("11111111-1111-1111-1111-11111111111"),
    operationId = None,
    themeId = None,
    country = Country("FR"),
    language = Language("fr"),
    questionId = None
  )

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
        Some(AuthInfo(UserRights(userId = UserId("my-user-id"), roles = Seq(RoleCitizen)), None, Some("user"), None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(userId = UserId("the-mother-of-dragons"), roles = Seq(RoleAdmin)), None, None, None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(userId = UserId("the-dwarf"), roles = Seq(RoleModerator)), None, None, None))
      )
    )

  val defaultSequence = Sequence(
    sequenceId = SequenceId("123"),
    title = "my sequence 1",
    slug = "my-sequence-1",
    proposalIds = Seq.empty,
    themeIds = Seq.empty,
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    status = SequenceStatus.Published,
    creationContext = RequestContext.empty,
    sequenceTranslation = Seq.empty,
    events = Nil,
    searchable = false
  )

  val sequenceModeratorSearchResult = SequencesSearchResult(0, Seq.empty)

  val validCreateJson: String =
    """
      |{
      | "themeIds": [],
      | "title": "my valid sequence",
      | "searchable": true
      |}
    """.stripMargin

  val invalidCreateJson: String =
    """
      |{
      | "themeIds": ["909090"],
      | "title": "my valid sequence",
      | "searchable": true
      |}
    """.stripMargin

  val validModeratorSearchJson: String =
    """
      |{
      | "themeIds": [],
      | "title": "my sequence 1",
      | "slug": "my-sequence-1",
      | "sorts": []
      |}
    """.stripMargin

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

  val routes: Route = sealRoute(sequenceRoutes)

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
        status should be(StatusCodes.OK)
      }
    }

    scenario("get unknown sequence config as moderator") {
      Get("/moderation/sequences/unknownSequence/unknownQuestion/configuration")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
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
        status should be(StatusCodes.OK)
      }
    }
  }
}
