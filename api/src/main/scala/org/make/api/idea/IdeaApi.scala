package org.make.api.idea

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.reference.{Idea, IdeaId}
import org.make.core.user.Role.RoleAdmin
import org.make.core.{HttpCodes, Validation}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Idea")
@Path(value = "/")
trait IdeaApi extends MakeAuthenticationDirectives {
  this: IdeaServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  @ApiOperation(
    value = "create-idea",
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
    value =
      Array(new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.idea.CreateIdeaRequest"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Idea])))
  @Path(value = "/idea")
  def createIdea: Route = post {
    path("idea") {
      makeTrace("CreateIdea") { requestContext =>
        makeOAuth2 { userAuth: AuthInfo[UserRights] =>
          authorize(userAuth.user.roles.contains(RoleAdmin)) {
            decodeRequest {
              entity(as[CreateIdeaRequest]) { request: CreateIdeaRequest =>
                provideAsync(ideaService.fetchOneByName(request.name)) { idea =>
                  Validation.validate(
                    Validation.requireNotPresent(
                      fieldName = "name",
                      fieldValue = idea,
                      message = Some("idea already exist. Duplicates are not allowed")
                    )
                  )

                  onSuccess(
                    ideaService
                      .insert(
                        name = request.name,
                        language = requestContext.language,
                        country = requestContext.country,
                        operation = requestContext.operation,
                        question = requestContext.question
                      )
                  ) { idea =>
                    complete(StatusCodes.Created -> idea)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "update-idea",
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
      new ApiImplicitParam(name = "ideaId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.idea.UpdateIdeaRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Idea])))
  @Path(value = "/ideas/{ideaId}")
  def updateIdea: Route = put {
    path("ideas" / ideaId) { ideaId =>
      makeTrace("UpdateIdea") { requestContext =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireAdminRole(auth.user) {
            decodeRequest {
              entity(as[UpdateIdeaRequest]) { request: UpdateIdeaRequest =>
                provideAsync(ideaService.update(name = request.name)) { idea =>
                  complete(idea)
                }
              }
            }
          }
        }
      }
    }
  }

  val ideaRoutes: Route = createIdea ~ updateIdea

  val ideaId: PathMatcher1[IdeaId] =
    Segment.flatMap(id => Try(IdeaId(id)).toOption)
}

case class CreateIdeaRequest(name: String)

object CreateIdeaRequest {
  implicit val decoder: Decoder[CreateIdeaRequest] = deriveDecoder[CreateIdeaRequest]
}

case class UpdateIdeaRequest(name: String)

object UpdateIdeaRequest {
  implicit val decoder: Decoder[UpdateIdeaRequest] = deriveDecoder[UpdateIdeaRequest]
}
