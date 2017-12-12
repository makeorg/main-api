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
import org.make.core.{HttpCodes, Validation}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Moderation Idea")
@Path(value = "/moderation/ideas")
trait ModerationIdeaApi extends MakeAuthenticationDirectives {
  this: IdeaServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  @ApiOperation(
    value = "list-ideas",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[Idea]])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operation", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "question", paramType = "query", dataType = "string")
    )
  )
  @Path(value = "/")
  def listIdeas: Route = {
    get {
      path("moderation" / "ideas") {
        parameters('language.?, 'country.?, 'operation.?, 'question.?) { (language, country, operation, question) =>
          makeTrace("Get all ideas") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                val filters: IdeaFiltersRequest =
                  IdeaFiltersRequest(language = language, country = country, operation = operation, question = question)
                provideAsync(ideaService.fetchAll(filters)) { ideas =>
                  complete(ideas)
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "get-idea",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "ideaId", paramType = "path", dataType = "string")))
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Idea])))
  @Path(value = "/{ideaId}")
  def getIdea: Route = {
    get {
      path("moderation" / "ideas" / ideaId) { ideaId =>
        makeTrace("GetIdea") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              provideAsyncOrNotFound(ideaService.fetchOne(ideaId)) { idea =>
                complete(idea)
              }
            }
          }
        }
      }
    }
  }

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
  @Path(value = "/")
  def createIdea: Route = post {
    path("moderation" / "ideas") {
      makeTrace("CreateIdea") { requestContext =>
        makeOAuth2 { userAuth: AuthInfo[UserRights] =>
          requireAdminRole(userAuth.user) {
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[IdeaId])))
  @Path(value = "/{ideaId}")
  def updateIdea: Route = put {
    path("moderation" / "ideas" / ideaId) { ideaId =>
      makeTrace("UpdateIdea") { requestContext =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireAdminRole(auth.user) {
            decodeRequest {
              entity(as[UpdateIdeaRequest]) { request: UpdateIdeaRequest =>
                onSuccess(ideaService.update(ideaId = ideaId, name = request.name)) { _ =>
                  complete(ideaId)
                }
              }
            }
          }
        }
      }
    }
  }

  val ideaRoutes: Route = createIdea ~ updateIdea ~ listIdeas ~ getIdea

  val ideaId: PathMatcher1[IdeaId] =
    Segment.flatMap(id => Try(IdeaId(id)).toOption)
}

final case class CreateIdeaRequest(name: String)

object CreateIdeaRequest {
  implicit val decoder: Decoder[CreateIdeaRequest] = deriveDecoder[CreateIdeaRequest]
}

final case class UpdateIdeaRequest(name: String)

object UpdateIdeaRequest {
  implicit val decoder: Decoder[UpdateIdeaRequest] = deriveDecoder[UpdateIdeaRequest]
}

final case class IdeaFiltersRequest(language: Option[String],
                                    country: Option[String],
                                    operation: Option[String],
                                    question: Option[String])

object IdeaFiltersRequest {
  val empty: IdeaFiltersRequest = IdeaFiltersRequest(None, None, None, None)
}
