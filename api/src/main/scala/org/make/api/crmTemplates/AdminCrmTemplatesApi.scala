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
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.auth.UserRights
import org.make.core.crmTemplate.CrmTemplatesId
import org.make.core.question.QuestionId
import org.make.core.{HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.Future
import scala.util.Try

@Api(value = "Crm Template - Admin")
@Path(value = "/admin/crm/templates")
trait AdminCrmTemplateApi extends Directives {

  @ApiOperation(
    value = "create-crm-templates",
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
        dataType = "org.make.api.crmTemplates.CreateTemplatesRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CrmTemplatesIdResponse]))
  )
  @Path(value = "/")
  def adminCreateCrmTemplates: Route

  @ApiOperation(
    value = "update-crm-templates",
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
      new ApiImplicitParam(name = "crmTemplatesId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.crmTemplates.UpdateTemplatesRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CrmTemplatesResponse]))
  )
  @Path(value = "/{crmTemplatesId}")
  def adminUpdateCrmTemplates: Route

  @Path(value = "/{crmTemplatesId}")
  @ApiOperation(
    value = "get-crm-templates",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CrmTemplatesResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "crmTemplatesId", paramType = "path", dataType = "string"))
  )
  def adminGetCrmTemplates: Route

  @ApiOperation(
    value = "list-crm-templates",
    httpMethod = "GET",
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
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "locale", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[CrmTemplatesResponse]]))
  )
  @Path(value = "/")
  def adminListCrmTemplates: Route

  def routes: Route = adminCreateCrmTemplates ~ adminUpdateCrmTemplates ~ adminGetCrmTemplates ~ adminListCrmTemplates
}

trait AdminCrmTemplateApiComponent {
  def adminCrmTemplateApi: AdminCrmTemplateApi
}

trait DefaultAdminCrmTemplatesApiComponent
    extends AdminCrmTemplateApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: CrmTemplatesServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with QuestionServiceComponent =>

  val crmTemplatesId: PathMatcher1[CrmTemplatesId] = Segment.flatMap(id => Try(CrmTemplatesId(id)).toOption)

  override lazy val adminCrmTemplateApi: AdminCrmTemplateApi =
    new AdminCrmTemplateApi {

      override def adminCreateCrmTemplates: Route = post {
        path("admin" / "crm" / "templates") {
          makeOperation("AdminCreateCrmTemplates") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[CreateTemplatesRequest]) { request: CreateTemplatesRequest =>
                    provideAsync(crmTemplatesService.count(request.questionId, request.getLocale)) { count =>
                      provideAsync(
                        request.questionId.map(questionService.getQuestion).getOrElse(Future.successful(None))
                      ) { question =>
                        val validateOptionalQuestion: Boolean = request.questionId.isEmpty || question.isDefined
                        Validation.validate(
                          Validation.validateEquals(
                            fieldName = "questionId",
                            message = Some("CRM templates already exist for this questionId or locale."),
                            userValue = count,
                            expectedValue = 0
                          ),
                          Validation
                            .validateField(
                              "questionId",
                              "invalid_content",
                              validateOptionalQuestion,
                              "Question is invalid"
                            )
                        )
                        onSuccess(
                          crmTemplatesService.createCrmTemplates(
                            CreateCrmTemplates(
                              questionId = question.map(_.questionId),
                              locale = request.getLocale,
                              registration = request.registration,
                              welcome = request.welcome,
                              proposalAccepted = request.proposalAccepted,
                              proposalRefused = request.proposalRefused,
                              forgottenPassword = request.forgottenPassword,
                              proposalAcceptedOrganisation = request.proposalAcceptedOrganisation,
                              proposalRefusedOrganisation = request.proposalRefusedOrganisation,
                              forgottenPasswordOrganisation = request.forgottenPasswordOrganisation
                            )
                          )
                        ) { crmTemplates =>
                          complete(StatusCodes.Created -> CrmTemplatesIdResponse(crmTemplates.crmTemplatesId))
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

      override def adminUpdateCrmTemplates: Route = put {
        path("admin" / "crm" / "templates" / crmTemplatesId) { crmTemplatesId =>
          makeOperation("AdminUpdateCrmTemplates") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[UpdateTemplatesRequest]) { request: UpdateTemplatesRequest =>
                    provideAsyncOrNotFound(
                      crmTemplatesService.updateCrmTemplates(
                        UpdateCrmTemplates(
                          crmTemplatesId = crmTemplatesId,
                          registration = request.registration,
                          welcome = request.welcome,
                          proposalAccepted = request.proposalAccepted,
                          proposalRefused = request.proposalRefused,
                          forgottenPassword = request.forgottenPassword,
                          proposalAcceptedOrganisation = request.proposalAcceptedOrganisation,
                          proposalRefusedOrganisation = request.proposalRefusedOrganisation,
                          forgottenPasswordOrganisation = request.forgottenPasswordOrganisation
                        )
                      )
                    ) { crmTemplates =>
                      complete(CrmTemplatesResponse(crmTemplates))
                    }
                  }
                }
              }
            }
          }
        }
      }

      override def adminGetCrmTemplates: Route = {
        get {
          path("admin" / "crm" / "templates" / crmTemplatesId) { crmTemplatesId =>
            makeOperation("AdminGetCrmTemplates") { _ =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireAdminRole(userAuth.user) {
                  provideAsyncOrNotFound(crmTemplatesService.getCrmTemplates(crmTemplatesId)) { crmTemplates =>
                    complete(CrmTemplatesResponse(crmTemplates))
                  }
                }
              }
            }
          }
        }
      }

      override def adminListCrmTemplates: Route = {
        get {
          path("admin" / "crm" / "templates") {
            makeOperation("AdminSearchCrmTemplates") { _ =>
              parameters(('_start.as[Int].?, '_end.as[Int].?, 'locale.?, 'questionId.as[QuestionId].?)) {
                (start: Option[Int],
                 end: Option[Int],
                 maybeLocale: Option[String],
                 maybeQuestionId: Option[QuestionId]) =>
                  makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                    requireAdminRole(userAuth.user) {
                      provideAsync(crmTemplatesService.count(maybeQuestionId, maybeLocale)) { count =>
                        onSuccess(
                          crmTemplatesService.find(
                            start = start.getOrElse(0),
                            end = end,
                            questionId = maybeQuestionId,
                            locale = maybeLocale
                          )
                        ) { crmTemplates =>
                          complete(
                            (
                              StatusCodes.OK,
                              List(TotalCountHeader(count.toString)),
                              crmTemplates.map(CrmTemplatesResponse.apply)
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
      }
    }
}
