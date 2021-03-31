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
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations.{ApiModelProperty, _}

import javax.ws.rs.Path
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{`X-Total-Count`, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.crmTemplate.{CrmLanguageTemplate, CrmTemplateKind, TemplateId}
import org.make.core.reference.Language
import org.make.core.{BusinessConfig, HttpCodes, Validation}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field
import scala.util.Try

@Api(value = "Admin Crm Templates - Languages")
@Path(value = "/admin/crm-templates/languages")
trait AdminCrmLanguageTemplatesApi extends Directives {

  @ApiOperation(
    value = "list-crm-templates-languages",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[CrmLanguageTemplates]]))
  )
  @Path(value = "/")
  def adminListCrmLanguageTemplates: Route

  @ApiOperation(
    value = "create-crm-templates-language",
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
        dataType = "org.make.api.crmTemplates.CrmLanguageTemplates"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CrmLanguageTemplates]))
  )
  @Path(value = "/")
  def adminCreateCrmLanguageTemplates: Route

  @Path(value = "/{language}")
  @ApiOperation(
    value = "get-crm-templates-language",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CrmLanguageTemplates]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "language", paramType = "path", dataType = "string")))
  def adminGetCrmLanguageTemplates: Route

  @ApiOperation(
    value = "update-crm-templates-language",
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
      new ApiImplicitParam(name = "language", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.crmTemplates.CrmLanguageTemplates"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CrmLanguageTemplates]))
  )
  @Path(value = "/{language}")
  def adminUpdateCrmLanguageTemplates: Route

  def routes: Route =
    adminListCrmLanguageTemplates ~ adminCreateCrmLanguageTemplates ~ adminGetCrmLanguageTemplates ~ adminUpdateCrmLanguageTemplates
}

trait AdminCrmLanguageTemplatesApiComponent {
  def adminCrmLanguageTemplatesApi: AdminCrmLanguageTemplatesApi
}

trait DefaultAdminCrmLanguageTemplatesApiComponent
    extends AdminCrmLanguageTemplatesApiComponent
    with MakeAuthenticationDirectives {
  this: MakeDirectivesDependencies with CrmTemplatesServiceComponent =>

  val language: PathMatcher1[Language] = Segment.flatMap(lang => Try(Language(lang)).toOption)

  override lazy val adminCrmLanguageTemplatesApi: AdminCrmLanguageTemplatesApi = new DefaultAdminCrmLanguageTemplatesApi

  class DefaultAdminCrmLanguageTemplatesApi extends AdminCrmLanguageTemplatesApi {

    override def adminListCrmLanguageTemplates: Route = {
      get {
        path("admin" / "crm-templates" / "languages") {
          makeOperation("AdminListCrmLanguageTemplates") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                provideAsync(crmTemplatesService.listByLanguage()) { all =>
                  complete((StatusCodes.OK, List(`X-Total-Count`(all.size.toString)), all.map {
                    case (language, values) => CrmLanguageTemplates(language, values)
                  }))
                }
              }
            }
          }
        }
      }
    }

    override def adminCreateCrmLanguageTemplates: Route = post {
      path("admin" / "crm-templates" / "languages") {
        makeOperation("AdminCreateCrmLanguageTemplates") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[CrmLanguageTemplates]) { request: CrmLanguageTemplates =>
                  provideAsync(crmTemplatesService.get(request.id)) { maybeCrmLanguageTemplates =>
                    Validation.validate(
                      Validation.validateField(
                        field = "id",
                        key = "invalid_value",
                        condition = BusinessConfig.supportedCountries.exists(_.supportedLanguages.contains(request.id)),
                        message = "Invalid language."
                      ),
                      Validation.validateEquals(
                        fieldName = "id",
                        message = Some("CRM templates already exist for this language."),
                        userValue = maybeCrmLanguageTemplates,
                        expectedValue = None
                      )
                    )
                    provideAsync(crmTemplatesService.create(request.id, request.values)) { values =>
                      complete(StatusCodes.Created -> CrmLanguageTemplates(request.id, values))
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def adminGetCrmLanguageTemplates: Route = {
      get {
        path("admin" / "crm-templates" / "languages" / language) { language =>
          makeOperation("AdminGetCrmLanguageTemplates") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                provideAsyncOrNotFound(crmTemplatesService.get(language)) { values =>
                  complete(CrmLanguageTemplates(language, values))
                }
              }
            }
          }
        }
      }
    }

    override def adminUpdateCrmLanguageTemplates: Route = put {
      path("admin" / "crm-templates" / "languages" / language) { language =>
        makeOperation("AdminUpdateCrmLanguageTemplates") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[CrmLanguageTemplates]) { request: CrmLanguageTemplates =>
                  provideAsyncOrNotFound(crmTemplatesService.get(language)) { _ =>
                    provideAsync(crmTemplatesService.update(language, request.values)) { values =>
                      complete(CrmLanguageTemplates(language, values))
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

final case class CrmLanguageTemplates(
  @(ApiModelProperty @field)(dataType = "string", example = "fr") id: Language,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") registration: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") resendRegistration: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") welcome: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalAccepted: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") proposalRefused: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") forgottenPassword: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") b2bProposalAccepted: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") b2bProposalRefused: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") b2bForgottenPassword: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") b2bRegistration: TemplateId,
  @(ApiModelProperty @field)(dataType = "string", example = "123456") b2bEmailChangedConfirmation: TemplateId
) {
  def values: CrmTemplateKind => TemplateId = {
    case CrmTemplateKind.Registration         => registration
    case CrmTemplateKind.ResendRegistration   => resendRegistration
    case CrmTemplateKind.Welcome              => welcome
    case CrmTemplateKind.ProposalAccepted     => proposalAccepted
    case CrmTemplateKind.ProposalRefused      => proposalRefused
    case CrmTemplateKind.ForgottenPassword    => forgottenPassword
    case CrmTemplateKind.B2BProposalAccepted  => b2bProposalAccepted
    case CrmTemplateKind.B2BProposalRefused   => b2bProposalRefused
    case CrmTemplateKind.B2BForgottenPassword => b2bForgottenPassword
    case CrmTemplateKind.B2BRegistration      => b2bRegistration
    case CrmTemplateKind.B2BEmailChanged      => b2bEmailChangedConfirmation
  }
}

object CrmLanguageTemplates {
  implicit val encoder: Encoder[CrmLanguageTemplates] = deriveEncoder[CrmLanguageTemplates]
  implicit val decoder: Decoder[CrmLanguageTemplates] = deriveDecoder[CrmLanguageTemplates]

  def apply(language: Language, templates: CrmTemplateKind => CrmLanguageTemplate): CrmLanguageTemplates =
    CrmLanguageTemplates(
      id = language,
      registration = templates(CrmTemplateKind.Registration).template,
      resendRegistration = templates(CrmTemplateKind.ResendRegistration).template,
      welcome = templates(CrmTemplateKind.Welcome).template,
      proposalAccepted = templates(CrmTemplateKind.ProposalAccepted).template,
      proposalRefused = templates(CrmTemplateKind.ProposalRefused).template,
      forgottenPassword = templates(CrmTemplateKind.ForgottenPassword).template,
      b2bProposalAccepted = templates(CrmTemplateKind.B2BProposalAccepted).template,
      b2bProposalRefused = templates(CrmTemplateKind.B2BProposalRefused).template,
      b2bForgottenPassword = templates(CrmTemplateKind.B2BForgottenPassword).template,
      b2bRegistration = templates(CrmTemplateKind.B2BRegistration).template,
      b2bEmailChangedConfirmation = templates(CrmTemplateKind.B2BEmailChanged).template
    )
}
