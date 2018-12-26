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
import io.circe.ObjectEncoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.PersistentOperationOfQuestionServiceComponent
import org.make.api.proposal._
import org.make.api.question.{PersistentQuestionServiceComponent, SearchQuestionRequest}
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.reference.Country
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

  @Path(value = "/operations/{operationId}/start-sequence")
  @ApiOperation(value = "get-widget-sequence", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultSeededResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "operationId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "tagsIds", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer")
    )
  )
  def getWidgetSequence: Route

  @Path(value = "/questions/{questionSlug}/start-sequence")
  @ApiOperation(value = "get-widget-sequence-by-question", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultSeededResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionSlug", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "tagsIds", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer")
    )
  )
  def startSequenceByQuestionSlug: Route

  def routes: Route = getWidgetSequence ~ startSequenceByQuestionSlug
}

trait DefaultWidgetApiComponent
    extends WidgetApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors
    with PersistentQuestionServiceComponent
    with PersistentOperationOfQuestionServiceComponent {
  this: MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent with WidgetServiceComponent =>

  override lazy val widgetApi: WidgetApi = new WidgetApi {

    private val widgetOperationId: PathMatcher1[OperationId] = Segment.map(id => OperationId(id))
    private val questionSlug: PathMatcher1[String] = Segment

    // @Deprecated
    override def getWidgetSequence: Route = get {
      path("widget" / "operations" / widgetOperationId / "start-sequence") { widgetOperationId =>
        makeOperation("GetWidgetSequence") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters(('tagsIds.as[immutable.Seq[TagId]].?, 'limit.as[Int].?, 'country.as[Country].?)) {
              (tagsIds: Option[Seq[TagId]], limit: Option[Int], country: Option[Country]) =>
                provideAsync(
                  widgetService.startNewWidgetSequence(
                    maybeUserId = userAuth.map(_.user.userId),
                    widgetOperationId = widgetOperationId,
                    tagsIds = tagsIds,
                    country = country,
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

    override def startSequenceByQuestionSlug: Route = get {
      path("widget" / "questions" / questionSlug / "start-sequence") { questionSlug =>
        makeOperation("GetWidgetSequenceByQuestionSlug") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters(('tagsIds.as[immutable.Seq[TagId]].?, 'limit.as[Int].?, 'country.as[Country].?)) {
              (tagsIds: Option[Seq[TagId]], limit: Option[Int], country: Option[Country]) =>
                provideAsyncOrNotFound(
                  persistentQuestionService
                    .find(SearchQuestionRequest(maybeSlug = Some(questionSlug)))
                    .map(_.headOption)
                ) { question =>
                  provideAsyncOrNotFound(persistentOperationOfQuestionService.getById(question.questionId)) {
                    operationOfQuestion =>
                      provideAsync(
                        widgetService.startNewWidgetSequence(
                          maybeUserId = userAuth.map(_.user.userId),
                          widgetOperationId = operationOfQuestion.operationId,
                          tagsIds = tagsIds,
                          country = country,
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
}

final case class WidgetSequence(title: String, slug: String, proposals: Seq[IndexedProposal])

object WidgetSequence {
  implicit val encoder: ObjectEncoder[WidgetSequence] = deriveEncoder[WidgetSequence]
}