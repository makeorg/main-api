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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.proposal._
import org.make.api.question.{PersistentQuestionService, SearchQuestionRequest}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{ActorSystemComponent, MakeApiTestBase}
import org.make.core.RequestContext
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.UserId

import scala.collection.immutable.Seq
import scala.concurrent.Future

class WidgetApiTest
    extends MakeApiTestBase
    with DefaultWidgetApiComponent
    with WidgetServiceComponent
    with ProposalServiceComponent
    with ActorSystemComponent
    with UserServiceComponent
    with IdeaServiceComponent {

  override val proposalService: ProposalService = mock[ProposalService]
  override val userService: UserService = mock[UserService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val widgetService: WidgetService = mock[WidgetService]
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]

  val routes: Route = sealRoute(widgetApi.routes)

  when(
    widgetService.startNewWidgetSequence(
      any[Option[UserId]],
      any[QuestionId],
      any[Option[Seq[TagId]]],
      any[Option[Int]],
      any[RequestContext]
    )
  ).thenReturn(Future.successful(ProposalsResultSeededResponse(total = 0, results = Seq.empty, seed = None)))

  Feature("start sequence by question slug") {
    val baseQuestion =
      Question(
        questionId = QuestionId("question-id"),
        slug = "slug",
        countries = NonEmptyList.of(Country("FR")),
        language = Language("fr"),
        question = "Slug ?",
        shortTitle = None,
        operationId = None
      )

    Scenario("valid question") {
      when(persistentQuestionService.find(any[SearchQuestionRequest]))
        .thenReturn(Future.successful(Seq(baseQuestion)))

      Get("/widget/questions/slug/start-sequence") ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("invalid question") {
      when(persistentQuestionService.find(any[SearchQuestionRequest]))
        .thenReturn(Future.successful(Seq()))

      Get("/widget/questions/slug/start-sequence") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}
