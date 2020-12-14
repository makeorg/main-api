/*
 *  Make.org Core API
 *  Copyright (C) 2018-2019 Make.org
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

package org.make.api.crmTemplates

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{`X-Total-Count`, IdGeneratorComponent, IdResponse, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.crmTemplate.{CrmQuestionTemplate, CrmQuestionTemplateId, CrmTemplateKind, TemplateId}
import org.make.core.question.QuestionId
import org.make.core.{HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import javax.ws.rs.Path
import scala.annotation.meta.field

@Api(value = "Admin Crm Templates - Questions")
@Path(value = "/admin/crm-templates/questions")
trait AdminCrmQuestionTemplatesApi extends Directives {

  @ApiOperation(
    value = "list-crm-question-templates",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string")))
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[CrmQuestionTemplate]]))
  )
  @Path(value = "/")
  def adminListCrmQuestionTemplates: Route

  @ApiOperation(
    value = "create-crm-question-template",
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
        value = "body",
        paramType = "body",
        dataType = "org.make.api.crmTemplates.CreateCrmQuestionTemplate"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CrmQuestionTemplate]))
  )
  @Path(value = "/")
  def adminCreateCrmQuestionTemplates: Route

  @Path(value = "/{crmQuestionTemplateId}")
  @ApiOperation(
    value = "get-crm-question-template",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CrmQuestionTemplate]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "crmQuestionTemplateId", paramType = "path", dataType = "string"))
  )
  def adminGetCrmQuestionTemplates: Route

  @ApiOperation(
    value = "update-crm-question-template",
    httpMethod = "PUT",
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
      new ApiImplicitParam(name = "crmQuestionTemplateId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.core.crmTemplate.CrmQuestionTemplate"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CrmQuestionTemplate]))
  )
  @Path(value = "/{crmQuestionTemplateId}")
  def adminUpdateCrmQuestionTemplates: Route

  @ApiOperation(
    value = "delete-crm-question-template",
    httpMethod = "DELETE",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "crmQuestionTemplateId", paramType = "path", dataType = "string"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[IdResponse])))
  @Path(value = "/{crmQuestionTemplateId}")
  def adminDeleteCrmQuestionTemplates: Route

  def routes: Route =
    adminListCrmQuestionTemplates ~ adminCreateCrmQuestionTemplates ~ adminGetCrmQuestionTemplates ~ adminUpdateCrmQuestionTemplates ~ adminDeleteCrmQuestionTemplates
}

trait AdminCrmQuestionTemplatesApiComponent {
  def adminCrmQuestionTemplatesApi: AdminCrmQuestionTemplatesApi
}

trait DefaultAdminCrmQuestionTemplatesApiComponent
    extends AdminCrmQuestionTemplatesApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: CrmTemplatesServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with QuestionServiceComponent =>

  val crmQuestionTemplateId: PathMatcher1[CrmQuestionTemplateId] = Segment.map(CrmQuestionTemplateId.apply)

  override lazy val adminCrmQuestionTemplatesApi: AdminCrmQuestionTemplatesApi = new DefaultAdminCrmQuestionTemplatesApi

  class DefaultAdminCrmQuestionTemplatesApi extends AdminCrmQuestionTemplatesApi {

    override def adminListCrmQuestionTemplates: Route = {
      get {
        path("admin" / "crm-templates" / "questions") {
          makeOperation("AdminListCrmQuestionTemplates") { _ =>
            parameters("questionId".as[QuestionId]) { (questionId: QuestionId) =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireAdminRole(userAuth.user) {
                  provideAsync(crmTemplatesService.list(questionId)) { templates =>
                    complete((StatusCodes.OK, List(`X-Total-Count`(templates.size.toString)), templates))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminCreateCrmQuestionTemplates: Route = post {
      path("admin" / "crm-templates" / "questions") {
        makeOperation("AdminCreateCrmQuestionTemplates") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[CreateCrmQuestionTemplate]) { request: CreateCrmQuestionTemplate =>
                  provideAsync(questionService.getQuestion(request.questionId)) { maybeQuestion =>
                    provideAsync(crmTemplatesService.list(request.questionId)) { allQuestionTemplates =>
                      Validation.validate(
                        Validation.validateField(
                          field = "questionId",
                          key = "invalid_value",
                          condition = maybeQuestion.isDefined,
                          message = s"Question ${request.questionId} does not exist."
                        ),
                        Validation.validateField(
                          field = "templateKind",
                          key = "invalid_value",
                          condition = !allQuestionTemplates.exists(_.kind == request.kind),
                          message = "CRM templates already exist for this question and templateKind."
                        )
                      )
                      provideAsync(
                        crmTemplatesService
                          .create(request.toCrmQuestionTemplate(idGenerator.nextCrmQuestionTemplateId()))
                      ) { template =>
                        complete(StatusCodes.Created -> template)
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

    override def adminGetCrmQuestionTemplates: Route = {
      get {
        path("admin" / "crm-templates" / "questions" / crmQuestionTemplateId) { crmQuestionTemplateId =>
          makeOperation("AdminGetCrmQuestionTemplates") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                provideAsyncOrNotFound(crmTemplatesService.get(crmQuestionTemplateId)) { crmTemplates =>
                  complete(crmTemplates)
                }
              }
            }
          }
        }
      }
    }

    override def adminUpdateCrmQuestionTemplates: Route = put {
      path("admin" / "crm-templates" / "questions" / crmQuestionTemplateId) { crmQuestionTemplateId =>
        makeOperation("AdminUpdateCrmQuestionTemplates") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[CrmQuestionTemplate]) { request: CrmQuestionTemplate =>
                  provideAsyncOrNotFound(crmTemplatesService.get(crmQuestionTemplateId)) { _ =>
                    provideAsync(crmTemplatesService.list(request.questionId)) { templates =>
                      provideAsync(questionService.getQuestion(request.questionId)) { maybeQuestion =>
                        Validation.validate(
                          Validation.validateField(
                            field = "questionId",
                            key = "invalid_value",
                            condition = maybeQuestion.isDefined,
                            message = s"Question ${request.questionId} does not exist."
                          ),
                          Validation.validateField(
                            field = "kind",
                            key = "invalid_value",
                            condition = !templates.exists(t => t.id != request.id && t.kind == request.kind),
                            message = s"Kind ${request.kind} already exists for this template but with a different id."
                          )
                        )
                        provideAsync(crmTemplatesService.update(request)) { template =>
                          complete(template)
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

    override def adminDeleteCrmQuestionTemplates: Route = {
      delete {
        path("admin" / "crm-templates" / "questions" / crmQuestionTemplateId) { crmQuestionTemplateId =>
          makeOperation("AdminGetCrmQuestionTemplates") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                provideAsync(crmTemplatesService.delete(crmQuestionTemplateId)) { _ =>
                  complete(IdResponse(crmQuestionTemplateId))
                }
              }
            }
          }
        }
      }
    }
  }
}

final case class CreateCrmQuestionTemplate(
  @(ApiModelProperty @field)(dataType = "string", example = "Welcome") kind: CrmTemplateKind,
  @(ApiModelProperty @field)(dataType = "string", example = "0320b63d-8475-491e-9d4f-47e9fa62a0e8") questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") template: TemplateId
) {
  def toCrmQuestionTemplate(id: CrmQuestionTemplateId): CrmQuestionTemplate =
    CrmQuestionTemplate(id = id, kind = kind, questionId = questionId, template = template)
}

object CreateCrmQuestionTemplate {
  implicit val encoder: Encoder[CreateCrmQuestionTemplate] = deriveEncoder[CreateCrmQuestionTemplate]
  implicit val decoder: Decoder[CreateCrmQuestionTemplate] = deriveDecoder[CreateCrmQuestionTemplate]
}
