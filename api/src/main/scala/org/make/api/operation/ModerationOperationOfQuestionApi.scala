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

package org.make.api.operation

import java.time.LocalDate

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.operation.{OperationId, OperationOfQuestion}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.{CirceFormatters, HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

@Api(value = "Moderation Operation of question")
@Path(value = "/moderation/operations-of-questions")
trait ModerationOperationOfQuestionApi extends Directives {

  @ApiOperation(
    value = "get-operations-of-questions-for-operation",
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
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[OperationOfQuestionResponse]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "openAt", paramType = "query", required = false, dataType = "string")
    )
  )
  @Path(value = "/")
  def listOperationOfQuestions: Route
  @ApiOperation(
    value = "get-operation-of-question",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationOfQuestionResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "questionId", paramType = "path", required = true, dataType = "string"))
  )
  @Path(value = "/{questionId}")
  def getOperationOfQuestion: Route

  @ApiOperation(
    value = "modify-operation-of-question",
    httpMethod = "PUT",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationOfQuestionResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", required = true, dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        required = true,
        dataType = "org.make.api.operation.ModifyOperationOfQuestionRequest"
      )
    )
  )
  @Path(value = "/{questionId}")
  def modifyOperationOfQuestion: Route

  @ApiOperation(
    value = "delete-operation-of-question",
    httpMethod = "DELETE",
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
    value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content", response = classOf[Unit]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "questionId", paramType = "path", required = true, dataType = "string"))
  )
  @Path(value = "/{questionId}")
  def deleteOperationOfQuestionAndQuestion: Route

  @ApiOperation(
    value = "create-operation-of-question",
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationOfQuestionResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        required = true,
        dataType = "org.make.api.operation.CreateOperationOfQuestionRequest"
      )
    )
  )
  @Path(value = "/")
  def createOperationOfQuestionAndQuestion: Route

  def routes: Route =
    listOperationOfQuestions ~
      getOperationOfQuestion ~
      modifyOperationOfQuestion ~
      deleteOperationOfQuestionAndQuestion ~
      createOperationOfQuestionAndQuestion
}

trait ModerationOperationOfQuestionApiComponent {
  def moderationOperationOfQuestionApi: ModerationOperationOfQuestionApi
}

trait DefaultModerationOperationOfQuestionApiComponent
    extends ModerationOperationOfQuestionApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with OperationOfQuestionServiceComponent
    with QuestionServiceComponent =>

  val moderationQuestionId: PathMatcher1[QuestionId] = Segment.map(id   => QuestionId(id))
  val moderationOperationId: PathMatcher1[OperationId] = Segment.map(id => OperationId(id))

  override lazy val moderationOperationOfQuestionApi: ModerationOperationOfQuestionApi =
    new ModerationOperationOfQuestionApi {

      override def listOperationOfQuestions: Route = get {
        path("moderation" / "operations-of-questions") {
          makeOperation("ListOperationsOfQuestions") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                parameters(('questionId.as[QuestionId].?, 'operationId.as[OperationId].?, 'openAt.as[LocalDate].?)) {
                  (questionId, operationId, openAt) =>
                    provideAsync(
                      operationOfQuestionService.search(SearchOperationsOfQuestions(questionId, operationId, openAt))
                    ) { result: Seq[OperationOfQuestion] =>
                      provideAsync(questionService.getQuestions(result.map(_.questionId))) { questions: Seq[Question] =>
                        val questionsAsMap = questions.map(q => q.questionId -> q).toMap
                        complete(
                          StatusCodes.OK -> result
                            .map(
                              operationOfQuestion =>
                                OperationOfQuestionResponse(
                                  operationOfQuestion,
                                  questionsAsMap(operationOfQuestion.questionId)
                              )
                            )
                        )
                      }
                    }
                }
              }
            }
          }
        }
      }

      override def getOperationOfQuestion: Route = get {
        path("moderation" / "operations-of-questions" / moderationQuestionId) { questionId =>
          makeOperation("GetOperationsOfQuestions") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                provideAsyncOrNotFound(questionService.getQuestion(questionId)) { question =>
                  provideAsyncOrNotFound(operationOfQuestionService.findByQuestionId(questionId)) {
                    operationOfQuestion =>
                      complete(OperationOfQuestionResponse(operationOfQuestion, question))
                  }
                }
              }
            }
          }
        }
      }

      override def modifyOperationOfQuestion: Route = put {
        path("moderation" / "operations-of-questions" / moderationQuestionId) { questionId =>
          makeOperation("ModifyOperationsOfQuestions") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                decodeRequest {
                  entity(as[ModifyOperationOfQuestionRequest]) { request =>
                    provideAsyncOrNotFound(questionService.getQuestion(questionId)) { question =>
                      provideAsyncOrNotFound(operationOfQuestionService.findByQuestionId(questionId)) {
                        operationOfQuestion =>
                          onSuccess(
                            operationOfQuestionService.update(
                              operationOfQuestion
                                .copy(
                                  startDate = request.startDate,
                                  endDate = request.endDate,
                                  operationTitle = request.operationTitle
                                )
                            )
                          ) { result =>
                            complete(StatusCodes.OK -> OperationOfQuestionResponse(result, question))
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

      override def deleteOperationOfQuestionAndQuestion: Route = delete {
        path("moderation" / "operations-of-questions" / moderationQuestionId) { questionId =>
          makeOperation("DeleteOperationsOfQuestions") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsync(operationOfQuestionService.delete(questionId)) { _ =>
                  complete(StatusCodes.NoContent)
                }
              }
            }
          }
        }
      }

      override def createOperationOfQuestionAndQuestion: Route = post {
        path("moderation" / "operations-of-questions") {
          makeOperation("CreateOperationsOfQuestions") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                decodeRequest {
                  entity(as[CreateOperationOfQuestionRequest]) { body =>
                    provideAsync(
                      operationOfQuestionService.create(
                        CreateOperationOfQuestion(
                          operationId = body.operationId,
                          startDate = body.startDate,
                          endDate = body.endDate,
                          operationTitle = body.operationTitle,
                          slug = body.questionSlug,
                          country = body.country,
                          language = body.language,
                          question = body.question
                        )
                      )
                    ) { operationOfQuestion =>
                      provideAsyncOrNotFound(questionService.getQuestion(operationOfQuestion.questionId)) { question =>
                        complete(StatusCodes.Created -> OperationOfQuestionResponse(operationOfQuestion, question))
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
}

final case class ModifyOperationOfQuestionRequest(startDate: Option[LocalDate],
                                                  endDate: Option[LocalDate],
                                                  operationTitle: String)
object ModifyOperationOfQuestionRequest extends CirceFormatters {
  implicit val decoder: Decoder[ModifyOperationOfQuestionRequest] = deriveDecoder[ModifyOperationOfQuestionRequest]
  implicit val encoder: Encoder[ModifyOperationOfQuestionRequest] = deriveEncoder[ModifyOperationOfQuestionRequest]
}

final case class CreateOperationOfQuestionRequest(operationId: OperationId,
                                                  startDate: Option[LocalDate],
                                                  endDate: Option[LocalDate],
                                                  operationTitle: String,
                                                  country: Country,
                                                  language: Language,
                                                  question: String,
                                                  questionSlug: String)

object CreateOperationOfQuestionRequest extends CirceFormatters {
  implicit val decoder: Decoder[CreateOperationOfQuestionRequest] = deriveDecoder[CreateOperationOfQuestionRequest]
  implicit val encoder: Encoder[CreateOperationOfQuestionRequest] = deriveEncoder[CreateOperationOfQuestionRequest]
}

final case class OperationOfQuestionResponse(questionId: QuestionId,
                                             operationId: OperationId,
                                             startDate: Option[LocalDate],
                                             endDate: Option[LocalDate],
                                             landingSequenceId: SequenceId,
                                             operationTitle: String,
                                             questionSlug: String,
                                             question: String,
                                             country: Country,
                                             language: Language)

object OperationOfQuestionResponse extends CirceFormatters {
  implicit val encoder: Encoder[OperationOfQuestionResponse] = deriveEncoder[OperationOfQuestionResponse]

  def apply(operationOfQuestion: OperationOfQuestion, question: Question): OperationOfQuestionResponse = {
    OperationOfQuestionResponse(
      questionId = operationOfQuestion.questionId,
      operationId = operationOfQuestion.operationId,
      operationTitle = operationOfQuestion.operationTitle,
      startDate = operationOfQuestion.startDate,
      endDate = operationOfQuestion.endDate,
      landingSequenceId = operationOfQuestion.landingSequenceId,
      questionSlug = question.slug,
      question = question.question,
      country = question.country,
      language = question.language
    )
  }
}
