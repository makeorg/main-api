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
import org.make.api.technical.{`X-Total-Count`, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.crmTemplate.CrmTemplatesId
import org.make.core.question.QuestionId
import org.make.core.{HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.Future

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

  val crmTemplatesId: PathMatcher1[CrmTemplatesId] = Segment.map(id => CrmTemplatesId(id))

  override lazy val adminCrmTemplateApi: AdminCrmTemplateApi = new DefaultAdminCrmTemplateApi

  class DefaultAdminCrmTemplateApi extends AdminCrmTemplateApi {

    override def adminCreateCrmTemplates: Route = post {
      path("admin" / "crm" / "templates") {
        makeOperation("AdminCreateCrmTemplates") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[CreateTemplatesRequest]) { request: CreateTemplatesRequest =>
                  provideAsync(crmTemplatesService.count(request.questionId, request.getLocale)) { count =>
                    provideAsync(request.questionId.map(questionService.getQuestion).getOrElse(Future.successful(None))) {
                      question =>
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
                        val locale = request.getLocale.orElse(question.map(_.getLocale))
                        provideAsync(crmTemplatesService.getDefaultTemplate(locale = locale)) { defaultTemplate =>
                          val crmTemplates = for {
                            registration <- request.registration.orElse(defaultTemplate.map(_.registration))
                            welcome      <- request.welcome.orElse(defaultTemplate.map(_.welcome))
                            proposalAccepted <- request.proposalAccepted
                              .orElse(defaultTemplate.map(_.proposalAccepted))
                            proposalRefused <- request.proposalRefused.orElse(defaultTemplate.map(_.proposalRefused))
                            forgottenPassword <- request.forgottenPassword
                              .orElse(defaultTemplate.map(_.forgottenPassword))
                            resendRegistration <- request.resendRegistration
                              .orElse(defaultTemplate.map(_.resendRegistration))
                            proposalAcceptedOrganisation <- request.proposalAcceptedOrganisation
                              .orElse(defaultTemplate.map(_.proposalAcceptedOrganisation))
                            proposalRefusedOrganisation <- request.proposalRefusedOrganisation
                              .orElse(defaultTemplate.map(_.proposalRefusedOrganisation))
                            forgottenPasswordOrganisation <- request.forgottenPasswordOrganisation
                              .orElse(defaultTemplate.map(_.forgottenPasswordOrganisation))
                            organisationEmailChangeConfirmation <- request.organisationEmailChangeConfirmation
                              .orElse(defaultTemplate.map(_.organisationEmailChangeConfirmation))
                          } yield {
                            CreateCrmTemplates(
                              questionId = question.map(_.questionId),
                              locale = request.getLocale,
                              registration,
                              welcome,
                              proposalAccepted,
                              proposalRefused,
                              forgottenPassword,
                              resendRegistration,
                              proposalAcceptedOrganisation,
                              proposalRefusedOrganisation,
                              forgottenPasswordOrganisation,
                              organisationEmailChangeConfirmation
                            )
                          }
                          crmTemplates match {
                            case None =>
                              validateDefaultTemplates(request)
                              complete(StatusCodes.BadRequest)
                            case Some(createCrmTemplates) =>
                              onSuccess(crmTemplatesService.createCrmTemplates(createCrmTemplates)) { crmTemplates =>
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
      }
    }

    override def adminUpdateCrmTemplates: Route = put {
      path("admin" / "crm" / "templates" / crmTemplatesId) { crmTemplatesId =>
        makeOperation("AdminUpdateCrmTemplates") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[UpdateTemplatesRequest]) { request: UpdateTemplatesRequest =>
                  provideAsyncOrNotFound(crmTemplatesService.getCrmTemplates(crmTemplatesId)) { crmTemplate =>
                    provideAsync(
                      crmTemplate.questionId.map(questionService.getQuestion).getOrElse(Future.successful(None))
                    ) { question =>
                      val locale = crmTemplate.locale.orElse(question.map(_.getLocale))
                      provideAsync(crmTemplatesService.getDefaultTemplate(locale = locale)) { defaultTemplate =>
                        val crmTemplates = for {
                          registration <- request.registration.orElse(defaultTemplate.map(_.registration))
                          welcome      <- request.welcome.orElse(defaultTemplate.map(_.welcome))
                          proposalAccepted <- request.proposalAccepted
                            .orElse(defaultTemplate.map(_.proposalAccepted))
                          proposalRefused <- request.proposalRefused.orElse(defaultTemplate.map(_.proposalRefused))
                          forgottenPassword <- request.forgottenPassword
                            .orElse(defaultTemplate.map(_.forgottenPassword))
                          resendRegistration <- request.resendRegistration
                            .orElse(defaultTemplate.map(_.resendRegistration))
                          proposalAcceptedOrganisation <- request.proposalAcceptedOrganisation
                            .orElse(defaultTemplate.map(_.proposalAcceptedOrganisation))
                          proposalRefusedOrganisation <- request.proposalRefusedOrganisation
                            .orElse(defaultTemplate.map(_.proposalRefusedOrganisation))
                          forgottenPasswordOrganisation <- request.forgottenPasswordOrganisation
                            .orElse(defaultTemplate.map(_.forgottenPasswordOrganisation))
                          organisationEmailChangeConfirmation <- request.organisationEmailChangeConfirmation
                            .orElse(defaultTemplate.map(_.organisationEmailChangeConfirmation))
                        } yield {
                          UpdateCrmTemplates(
                            crmTemplatesId = crmTemplatesId,
                            registration,
                            welcome,
                            proposalAccepted,
                            proposalRefused,
                            forgottenPassword,
                            resendRegistration,
                            proposalAcceptedOrganisation,
                            proposalRefusedOrganisation,
                            forgottenPasswordOrganisation,
                            organisationEmailChangeConfirmation
                          )
                        }
                        crmTemplates match {
                          case None =>
                            validateDefaultTemplates(request)
                            complete(StatusCodes.BadRequest)
                          case Some(updateCrmTemplates) =>
                            provideAsyncOrNotFound(crmTemplatesService.updateCrmTemplates(updateCrmTemplates)) {
                              crmTemplates =>
                                complete(CrmTemplatesResponse(crmTemplates))
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
    }

    private def validateDefaultTemplates(request: CrmTemplatesRequest): Unit = {
      Validation.validate(
        Validation.requirePresent(fieldName = "registration", fieldValue = request.registration),
        Validation.requirePresent(fieldName = "welcome", fieldValue = request.welcome),
        Validation
          .requirePresent(fieldName = "proposalAccepted", fieldValue = request.proposalAccepted),
        Validation
          .requirePresent(fieldName = "proposalRefused", fieldValue = request.proposalRefused),
        Validation
          .requirePresent(fieldName = "forgottenPassword", fieldValue = request.forgottenPassword),
        Validation
          .requirePresent(fieldName = "resendRegistration", fieldValue = request.resendRegistration),
        Validation
          .requirePresent(
            fieldName = "proposalAcceptedOrganisation",
            fieldValue = request.proposalAcceptedOrganisation
          ),
        Validation
          .requirePresent(fieldName = "proposalRefusedOrganisation", fieldValue = request.proposalRefusedOrganisation),
        Validation
          .requirePresent(
            fieldName = "forgottenPasswordOrganisation",
            fieldValue = request.forgottenPasswordOrganisation
          ),
        Validation
          .requirePresent(
            fieldName = "organisationEmailChangeConfirmation",
            fieldValue = request.organisationEmailChangeConfirmation
          )
      )
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
            parameters(
              (
                Symbol("_start").as[Int].?,
                Symbol("_end").as[Int].?,
                Symbol("locale").?,
                Symbol("questionId").as[QuestionId].?
              )
            ) {
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
                            List(`X-Total-Count`(count.toString)),
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
