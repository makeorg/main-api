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
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.Validation.{requireValidSlug, validate, validateOptionalUserInput, validateUserInput}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.Role.RoleAdmin
import org.make.core.{HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field
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
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[ModerationQuestionResponse]]))
  )
  @Path(value = "/")
  def listQuestions: Route

  @ApiOperation(
    value = "moderation-create-initial-proposal",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.question.CreateInitialProposalRequest"
      ),
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalIdResponse]))
  )
  @Path(value = "/{questionId}/initial-proposals")
  def addInitialProposal: Route

  @ApiOperation(
    value = "get-question",
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationQuestionResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string")))
  @Path(value = "/{questionId}")
  def getQuestion: Route

  @ApiOperation(
    value = "post-question",
    httpMethod = "POST",
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.question.CreateQuestionRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationQuestionResponse]))
  )
  @Path(value = "/")
  def createQuestion: Route

  def routes: Route = listQuestions ~ getQuestion ~ createQuestion ~ addInitialProposal

}

trait ModerationQuestionComponent {
  def moderationQuestionApi: ModerationQuestionApi
}

final case class ProposalIdResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "927074a0-a51f-4183-8e7a-bebc705c081b")
  proposalId: ProposalId
)

object ProposalIdResponse {
  implicit val encoder: Encoder[ProposalIdResponse] = deriveEncoder[ProposalIdResponse]
}

final case class AuthorRequest(@(ApiModelProperty @field)(dataType = "integer", example = "23")
                               age: Option[Int],
                               firstName: Option[String],
                               lastName: Option[String],
                               postalCode: Option[String],
                               profession: Option[String]) {
  validate(
    validateOptionalUserInput("firstName", firstName, None),
    validateOptionalUserInput("lastName", lastName, None),
    validateOptionalUserInput("postalCode", postalCode, None),
    validateOptionalUserInput("profession", profession, None)
  )
}
object AuthorRequest {
  implicit val decoder: Decoder[AuthorRequest] = deriveDecoder[AuthorRequest]
}

final case class CreateInitialProposalRequest(content: String,
                                              author: AuthorRequest,
                                              @(ApiModelProperty @field)(dataType = "list[string]") tags: Seq[TagId]) {
  validate(validateUserInput("content", content, None))
}

object CreateInitialProposalRequest {
  implicit val decoder: Decoder[CreateInitialProposalRequest] = deriveDecoder[CreateInitialProposalRequest]
}

trait DefaultModerationQuestionComponent
    extends ModerationQuestionComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: QuestionServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ProposalServiceComponent =>

  override lazy val moderationQuestionApi: ModerationQuestionApi = new ModerationQuestionApi {

    lazy val questionId: PathMatcher1[QuestionId] = Segment.map(QuestionId.apply)

    override def addInitialProposal: Route = post {
      path("moderation" / "questions" / questionId / "initial-proposals") { questionId =>
        makeOperation("ModerationAddInitialProposalToQuestion") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[CreateInitialProposalRequest]) { request =>
                  provideAsyncOrNotFound(questionService.getQuestion(questionId)) { question =>
                    provideAsync(
                      proposalService.createInitialProposal(
                        request.content,
                        question,
                        request.tags,
                        request.author,
                        userAuth.user.userId,
                        requestContext
                      )
                    ) { proposalId =>
                      complete(StatusCodes.Created -> ProposalIdResponse(proposalId))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

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
                val questionIds: Option[Seq[QuestionId]] = if (userAuth.user.roles.contains(RoleAdmin)) {
                  None
                } else {
                  Some(userAuth.user.availableQuestions)
                }
                val first: Int = start.getOrElse(0)
                val request: SearchQuestionRequest = SearchQuestionRequest(
                  maybeQuestionIds = questionIds,
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
              requireAdminRole(userAuth.user) {
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

final case class CreateQuestionRequest(@(ApiModelProperty @field)(dataType = "string", example = "FR")
                                       country: Country,
                                       @(ApiModelProperty @field)(dataType = "string", example = "fr")
                                       language: Language,
                                       question: String,
                                       slug: String) {
  validate(validateUserInput("question", question, None), requireValidSlug("slug", Some(slug), None))
}

object CreateQuestionRequest {
  implicit val decoder: Decoder[CreateQuestionRequest] = deriveDecoder[CreateQuestionRequest]
}
