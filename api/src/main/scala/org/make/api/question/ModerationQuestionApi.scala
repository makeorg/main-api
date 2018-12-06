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
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.question.Question
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.{HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "Moderation questions")
@Path(value = "/moderation/questions")
trait ModerationQuestionApi extends Directives {

  @ApiOperation(
    value = "moderation-list-questions",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
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
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[ModerationQuestionResponse]]))
  )
  @Path(value = "/")
  def listQuestions: Route

  @ApiOperation(value = "get-question", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationQuestionResponse]))
  )
  @Path(value = "/{questionId}")
  def getQuestion: Route

  @ApiOperation(value = "post-question", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.CreateQuestionRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationQuestionResponse]))
  )
  @Path(value = "/")
  def createQuestion: Route

  def routes: Route = listQuestions ~ getQuestion ~ createQuestion

}

trait ModerationQuestionComponent {
  def moderationQuestionApi: ModerationQuestionApi
}

trait DefaultModerationQuestionComponent
    extends ModerationQuestionComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: QuestionServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  private val questionId: PathMatcher1[QuestionId] = Segment.map(id => QuestionId(id))

  override lazy val moderationQuestionApi: ModerationQuestionApi = new ModerationQuestionApi {
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
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireModerationRole(userAuth.user) {
                val first: Int = start.getOrElse(0)
                val request: SearchQuestionRequest = SearchQuestionRequest(
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
                val searchResults: Future[(Int, Seq[Question])] =
                  questionService.countQuestion(request).flatMap { count =>
                    questionService.searchQuestion(request).map(results => count -> results)
                  }

                onSuccess(searchResults) {
                  case (count, results) =>
                    complete(
                      (
                        StatusCodes.OK,
                        scala.collection.immutable.Seq(TotalCountHeader(count.toString)),
                        results.map(ModerationQuestionResponse.apply)
                      )
                    )
                }
              }
            }
          }
        }
      }
    }

    override def getQuestion: Route = get {
      path("moderation" / "questions" / questionId) { questionId =>
        makeOperation("ModerationGetQuestion") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              provideAsyncOrNotFound(questionService.getQuestion(questionId)) { question =>
                complete(ModerationQuestionResponse(question))
              }
            }
          }
        }
      }
    }

    override def createQuestion: Route =
      post {
        path("moderation" / "questions") {
          makeOperation("ModerationCreateQuestion") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireModerationRole(userAuth.user) {
                decodeRequest {
                  entity(as[CreateQuestionRequest]) { request =>
                    provideAsync(
                      questionService
                        .createQuestion(
                          country = request.country,
                          language = request.language,
                          question = request.question,
                          slug = request.slug
                        )
                    ) { question =>
                      complete(ModerationQuestionResponse(question))
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

final case class CreateQuestionRequest(country: Country, language: Language, question: String, slug: String)

object CreateQuestionRequest {
  implicit val decoder: Decoder[CreateQuestionRequest] = deriveDecoder[CreateQuestionRequest]
}
