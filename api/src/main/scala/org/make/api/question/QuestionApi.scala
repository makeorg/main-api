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

package org.make.api.question

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.PersistentOperationOfQuestionServiceComponent
import org.make.api.sequence.SequenceServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.sequence.indexed.IndexedStartSequence
import org.make.core.{HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global

trait QuestionApiComponent {
  def questionApi: QuestionApi
}

@Api(value = "Questions")
@Path(value = "/questions")
trait QuestionApi extends Directives {

  @ApiOperation(value = "list-questions", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "slug", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "themeId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[QuestionResponse]]))
  )
  @Path(value = "/")
  def listQuestions: Route

  @ApiOperation(value = "start-sequence-by-question", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.question.StartSequenceByQuestionIdRequest"
      )
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Option[IndexedStartSequence]]))
  )
  @Path(value = "/questions/{questionId}/start-sequence")
  def startSequenceByQuestionId: Route

  def routes: Route = listQuestions ~ startSequenceByQuestionId

}

trait DefaultQuestionApiComponent
    extends QuestionApiComponent
    with SequenceServiceComponent
    with PersistentOperationOfQuestionServiceComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: QuestionServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  override lazy val questionApi: QuestionApi = new QuestionApi {

    private val questionId: PathMatcher1[QuestionId] = Segment.map(id => QuestionId(id))

    override def listQuestions: Route = get {
      path("moderation" / "questions") {
        makeOperation("ModerationSearchQuestion") { _ =>
          parameters(
            (
              'slug.?,
              'operationId.as[OperationId].?,
              'themeId.as[ThemeId].?,
              'country.as[Country].?,
              'language.as[Language].?,
              '_start.as[Int].?,
              '_end.as[Int].?,
              '_sort.?,
              '_order.?
            )
          ) { (maybeSlug, operationId, themeId, country, language, start, end, sort, order) =>
            val first = start.getOrElse(0)
            val request = SearchQuestionRequest(
              maybeThemeId = themeId,
              maybeOperationId = operationId,
              country = country,
              language = language,
              maybeSlug = maybeSlug,
              skip = start,
              limit = end.map(offset => offset - first),
              sort = sort,
              order = order
            )
            val searchResults =
              questionService.countQuestion(request).flatMap { count =>
                questionService.searchQuestion(request).map(results => count -> results)
              }

            onSuccess(searchResults) {
              case (count, results) =>
                complete(
                  (
                    StatusCodes.OK,
                    scala.collection.immutable.Seq(TotalCountHeader(count.toString)),
                    results.map(QuestionResponse.apply)
                  )
                )
            }
          }
        }
      }
    }

    override def startSequenceByQuestionId: Route = post {
      path("questions" / questionId / "start-sequence") { questionId =>
        makeOperation("StartSequenceById") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            decodeRequest {
              entity(as[StartSequenceByQuestionIdRequest]) { request: StartSequenceByQuestionIdRequest =>
                provideAsyncOrNotFound(persistentOperationOfQuestionService.getById(questionId)) {
                  operationOfquestion =>
                    provideAsyncOrNotFound(
                      sequenceService
                        .startNewSequence(
                          maybeUserId = userAuth.map(_.user.userId),
                          sequenceId = operationOfquestion.landingSequenceId,
                          includedProposals = request.include.getOrElse(Seq.empty),
                          tagsIds = None,
                          requestContext = requestContext
                        )
                    ) { sequences =>
                      complete(sequences)
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

final case class StartSequenceByQuestionIdRequest(include: Option[Seq[ProposalId]] = None)

object StartSequenceByQuestionIdRequest {
  implicit val decoder: Decoder[StartSequenceByQuestionIdRequest] = deriveDecoder[StartSequenceByQuestionIdRequest]
}
