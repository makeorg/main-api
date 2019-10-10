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

import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.operation.PersistentOperationOfQuestionService
import org.make.api.proposal._
import org.make.api.question.{PersistentQuestionService, SearchQuestionRequest}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{ActorSystemComponent, MakeApiTestBase}
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.operation.{
  FinalCard,
  IntroCard,
  Metas,
  OperationId,
  OperationOfQuestion,
  PushProposalCard,
  QuestionTheme,
  SequenceCardsConfiguration,
  SignUpCard
}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class WidgetApiTest
    extends MakeApiTestBase
    with DefaultWidgetApiComponent
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
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]
  override val persistentOperationOfQuestionService: PersistentOperationOfQuestionService =
    mock[PersistentOperationOfQuestionService]

  val routes: Route = sealRoute(widgetApi.routes)
  val validAccessToken = "my-valid-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.eq(accessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = UserId("my-user-id"),
              roles = Seq(RoleCitizen),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            Some("user"),
            None
          )
        )
      )
    )

  when(
    widgetService.startNewWidgetSequence(
      ArgumentMatchers.any[Option[UserId]],
      ArgumentMatchers.any[SequenceId],
      ArgumentMatchers.any[Option[Seq[TagId]]],
      ArgumentMatchers.any[Option[Int]],
      ArgumentMatchers.any[RequestContext]
    )
  ).thenReturn(Future.successful(ProposalsResultSeededResponse(total = 0, results = Seq.empty, seed = None)))

  feature("start sequence by question slug") {
    val baseQuestion = Question(QuestionId("question-id"), "slug", Country("FR"), Language("fr"), "Slug ?", None, None)
    val baseOperationOfQuestion = OperationOfQuestion(
      QuestionId("question-id"),
      OperationId("foo-operation-id"),
      None,
      None,
      "Foo operation",
      SequenceId("sequence-id"),
      canPropose = true,
      sequenceCardsConfiguration = SequenceCardsConfiguration(
        introCard = IntroCard(enabled = true, title = None, description = None),
        pushProposalCard = PushProposalCard(enabled = true),
        signUpCard = SignUpCard(enabled = true, title = None, nextCtaText = None),
        finalCard = FinalCard(
          enabled = true,
          sharingEnabled = false,
          title = None,
          shareDescription = None,
          learnMoreTitle = None,
          learnMoreTextButton = None,
          linkUrl = None
        )
      ),
      aboutUrl = None,
      metas = Metas(title = None, description = None, picture = None),
      theme = QuestionTheme.default,
      description = OperationOfQuestion.defaultDescription,
      imageUrl = None,
      displayResults = false
    )

    scenario("valid question") {
      when(persistentQuestionService.find(ArgumentMatchers.any[SearchQuestionRequest]))
        .thenReturn(Future.successful(Seq(baseQuestion)))
      when(persistentOperationOfQuestionService.getById(ArgumentMatchers.any[QuestionId]))
        .thenReturn(Future.successful(Some(baseOperationOfQuestion)))

      Get("/widget/questions/slug/start-sequence") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("invalid question") {
      when(persistentQuestionService.find(ArgumentMatchers.any[SearchQuestionRequest]))
        .thenReturn(Future.successful(Seq()))

      Get("/widget/questions/slug/start-sequence") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
