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

import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller.CsvSeq
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal._
import org.make.api.question.{PersistentQuestionServiceComponent, SearchQuestionRequest}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.tag.TagId
import org.make.core.{HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global

trait WidgetApiComponent {
  def widgetApi: WidgetApi
}

@Api(value = "Widget")
@Path(value = "/widget")
trait WidgetApi extends Directives {

  @Path(value = "/questions/{questionSlug}/start-sequence")
  @ApiOperation(value = "get-widget-sequence-by-question", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultSeededResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionSlug", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        name = "tagsIds",
        paramType = "query",
        dataType = "string",
        example = "ad0065ec-8c80-4f88-b298-bc0956dbc495,4caac845-f219-4455-ae78-8dd2fd5515ce"
      ),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string", example = "FR"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer", example = "12")
    )
  )
  def startSequenceByQuestionSlug: Route

  def routes: Route = startSequenceByQuestionSlug
}

trait DefaultWidgetApiComponent
    extends WidgetApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors
    with PersistentQuestionServiceComponent {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with WidgetServiceComponent
    with SessionHistoryCoordinatorServiceComponent =>

  override lazy val widgetApi: WidgetApi = new DefaultWidgetApi

  class DefaultWidgetApi extends WidgetApi {

    private val questionSlug: PathMatcher1[String] = Segment

    override def startSequenceByQuestionSlug: Route = get {
      path("widget" / "questions" / questionSlug / "start-sequence") { questionSlug =>
        makeOperation("GetWidgetSequenceByQuestionSlug") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters("tagsIds".as[immutable.Seq[TagId]].?, "limit".as[Int].?) {
              (tagsIds: Option[Seq[TagId]], limit: Option[Int]) =>
                provideAsyncOrNotFound(
                  persistentQuestionService
                    .find(SearchQuestionRequest(maybeSlug = Some(questionSlug)))
                    .map(_.headOption)
                ) { question =>
                  provideAsync(
                    widgetService.startNewWidgetSequence(
                      maybeUserId = userAuth.map(_.user.userId),
                      questionId = question.questionId,
                      tagsIds = tagsIds,
                      limit = limit,
                      requestContext = requestContext
                    )
                  ) { proposalsResultSeededResponse: ProposalsResultSeededResponse =>
                    complete(proposalsResultSeededResponse)
                  }
                }
            }
          }
        }
      }
    }
  }
}
