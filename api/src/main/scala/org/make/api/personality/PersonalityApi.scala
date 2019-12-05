package org.make.api.personality

import akka.http.scaladsl.server._
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.{UserResponse, UserServiceComponent}
import org.make.core._
import org.make.core.user.UserId

@Api(value = "Personalities")
@Path(value = "/personalities")
trait PersonalityApi extends Directives {

  @ApiOperation(value = "get-personality", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "d22c8e70-f709-42ff-8a52-9398d159c753"
      )
    )
  )
  @Path(value = "/{userId}")
  def getPersonality: Route

  def routes: Route = getPersonality

}

trait PersonalityApiComponent {
  def personalityApi: PersonalityApi
}

trait DefaultPersonalityApiComponent extends PersonalityApiComponent with MakeAuthenticationDirectives {
  this: UserServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent =>

  override lazy val personalityApi: PersonalityApi = new DefaultPersonalityApi

  class DefaultPersonalityApi extends PersonalityApi {

    val userId: PathMatcher1[UserId] = Segment.map(id => UserId(id))

    override def getPersonality: Route =
      get {
        path("personalities" / userId) { userId =>
          makeOperation("GetPersonality") { _ =>
            provideAsyncOrNotFound(userService.getPersonality(userId)) { user =>
              complete(UserResponse(user))
            }
          }
        }
      }

  }

}
