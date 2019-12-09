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

package org.make.api.user

import java.time.LocalDate

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.sksamuel.elastic4s.searches.sort.SortOrder
import com.sksamuel.elastic4s.searches.sort.SortOrder.Desc
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.{ProposalServiceComponent, ProposalsResultResponse, ProposalsResultSeededResponse}
import org.make.api.question.QuestionServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical._
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.storage._
import org.make.api.user.social.SocialServiceComponent
import org.make.api.userhistory.{UserHistoryCoordinatorServiceComponent, _}
import org.make.core._
import org.make.core.auth.{ClientId, UserRights}
import org.make.core.common.indexed.Sort
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal._
import org.make.core.question.Question
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.RoleAdmin
import org.make.core.user._
import scalaoauth2.provider.AuthInfo

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "User")
@Path(value = "/user")
trait UserApi extends Directives {

  @Path(value = "/{userId}")
  @ApiOperation(
    value = "get-user",
    httpMethod = "GET",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      )
    )
  )
  def getUser: Route

  @Path(value = "/me")
  @ApiOperation(
    value = "get-me",
    httpMethod = "GET",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  def getMe: Route

  @Path(value = "/login/social")
  @ApiOperation(value = "Login Social", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value =
      Array(new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.SocialLoginRequest"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TokenResponse])))
  def socialLogin: Route

  @Path(value = "/{userId}/votes")
  @ApiOperation(value = "voted-proposals", httpMethod = "GET")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      ),
      new ApiImplicitParam(name = "votes", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "qualifications", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultResponse]))
  )
  def getVotedProposalsByUser: Route

  @ApiOperation(value = "register-user", httpMethod = "POST", code = HttpCodes.Created)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.RegisterUserRequest"),
      new ApiImplicitParam(
        value = "X-Forwarded-For",
        paramType = "header",
        dataType = "string",
        name = "X-Forwarded-For"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.Created, message = "Created", response = classOf[UserResponse]))
  )
  def register: Route

  @ApiOperation(value = "verifiy user email", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/{userId}/validate/:verificationToken")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      ),
      new ApiImplicitParam(name = "verificationToken", paramType = "path", dataType = "string")
    )
  )
  def validateAccountRoute: Route

  @ApiOperation(value = "Reset password request token", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/reset-password/request-reset")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.user.ResetPasswordRequest")
    )
  )
  def resetPasswordRequestRoute: Route

  @ApiOperation(value = "Reset password token check", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/reset-password/check-validity/:userId/:resetToken")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      ),
      new ApiImplicitParam(name = "resetToken", paramType = "path", dataType = "string")
    )
  )
  def resetPasswordCheckRoute: Route

  @ApiOperation(value = "Reset password", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/reset-password/change-password/:userId")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.user.ResetPassword"),
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      )
    )
  )
  def resetPasswordRoute: Route

  @ApiOperation(
    value = "reload-history",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/{userId}/reload-history")
  def rebuildUserHistory: Route

  @ApiOperation(
    value = "reload-all-users-history",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/reload-history")
  def rebuildAllUsersHistory: Route

  @Path(value = "/{userId}/proposals")
  @ApiOperation(
    value = "user-proposals",
    httpMethod = "GET",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      ),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer")
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultSeededResponse]))
  )
  def getProposalsByUser: Route

  @ApiOperation(
    value = "user-update",
    httpMethod = "PATCH",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "user", description = "application user"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @ApiImplicitParams(
    value =
      Array(new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.UpdateUserRequest"))
  )
  @Path(value = "/")
  def patchUser: Route

  @ApiOperation(
    value = "change-password",
    httpMethod = "POST",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "user", description = "application user"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      ),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.ChangePasswordRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "{userId}/change-password")
  def changePassword: Route

  @ApiOperation(
    value = "delete-user",
    httpMethod = "POST",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "user", description = "application user"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      ),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.DeleteUserRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "{userId}/delete")
  def deleteUser: Route

  @ApiOperation(
    value = "follow-user",
    httpMethod = "POST",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "user", description = "application user"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      )
    )
  )
  @Path(value = "/{userId}/follow")
  def followUser: Route

  @ApiOperation(
    value = "unfollow-user",
    httpMethod = "POST",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "user", description = "application user"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      )
    )
  )
  @Path(value = "/{userId}/unfollow")
  def unfollowUser: Route

  @ApiOperation(value = "reconnect-info", httpMethod = "POST")
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ReconnectInfo])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "userId",
        paramType = "path",
        dataType = "string",
        example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55"
      )
    )
  )
  @Path(value = "/{userId}/reconnect")
  def reconnectInfo: Route

  @ApiOperation(value = "resend-validation-email", httpMethod = "POST")
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content", response = classOf[Unit]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.user.ResendValidationEmailRequest"
      )
    )
  )
  @Path(value = "/validation-email")
  def resendValidationEmail: Route

  @ApiOperation(
    value = "upload-avatar",
    httpMethod = "POST",
    code = HttpCodes.OK,
    consumes = "multipart/form-data",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "data", paramType = "formData", dataType = "file")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UploadResponse])))
  @Path(value = "/{userId}/upload-avatar")
  def uploadAvatar: Route

  def routes: Route =
    getMe ~
      getUser ~
      register ~
      socialLogin ~
      rebuildAllUsersHistory ~
      rebuildUserHistory ~
      resetPasswordRequestRoute ~
      resetPasswordCheckRoute ~
      resetPasswordRoute ~
      validateAccountRoute ~
      getVotedProposalsByUser ~
      getProposalsByUser ~
      patchUser ~
      changePassword ~
      deleteUser ~
      followUser ~
      unfollowUser ~
      reconnectInfo ~
      resendValidationEmail ~
      uploadAvatar

  val userId: PathMatcher1[UserId] =
    Segment.map(id => UserId(id))
}

trait UserApiComponent {
  def userApi: UserApi
}

trait DefaultUserApiComponent
    extends UserApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: UserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SocialServiceComponent
    with ProposalServiceComponent
    with QuestionServiceComponent
    with EventBusServiceComponent
    with PersistentUserServiceComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent
    with StorageServiceComponent
    with StorageConfigurationComponent =>

  override lazy val userApi: UserApi = new DefaultUserApi

  class DefaultUserApi extends UserApi {

    override def getUser: Route = {
      get {
        path("user" / userId) { userId =>
          makeOperation("GetUser") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              authorize(userId == userAuth.user.userId || userAuth.user.roles.contains(RoleAdmin)) {
                provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                  provideAsync(userService.getFollowedUsers(userId)) { followedUsers =>
                    complete(UserResponse(user, followedUsers))
                  }
                }
              }
            }
          }
        }
      }
    }

    override def getMe: Route = {
      get {
        path("user" / "me") {
          makeOperation("GetMe") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              provideAsyncOrNotFound(userService.getUser(userAuth.user.userId)) { user =>
                provideAsync(userService.getFollowedUsers(user.userId)) { followedUsers =>
                  complete(UserResponse(user, followedUsers))
                }
              }
            }
          }
        }
      }
    }

    override def socialLogin: Route = post {
      path("user" / "login" / "social") {
        makeOperation("SocialLogin", EndpointType.CoreOnly) { requestContext =>
          decodeRequest {
            entity(as[SocialLoginRequest]) { request: SocialLoginRequest =>
              extractClientIP { clientIp =>
                val ip = clientIp.toOption.map(_.getHostAddress).getOrElse("unknown")
                val country: Country = request.country.orElse(requestContext.country).getOrElse(Country("FR"))
                val language: Language = request.language.orElse(requestContext.language).getOrElse(Language("fr"))

                val futureMaybeQuestion: Future[Option[Question]] = requestContext.questionId match {
                  case Some(questionId) => questionService.getQuestion(questionId)
                  case _                => Future.successful(None)
                }
                provideAsync(futureMaybeQuestion) { maybeQuestion =>
                  onSuccess(
                    socialService
                      .login(
                        request.provider,
                        request.token,
                        country,
                        language,
                        Some(ip),
                        maybeQuestion.map(_.questionId),
                        requestContext,
                        request.clientId.getOrElse(ClientId(makeSettings.Authentication.defaultClientId))
                      )
                      .flatMap { social =>
                        sessionHistoryCoordinatorService
                          .convertSession(requestContext.sessionId, social.userId, requestContext)
                          .map(_ => social)
                      }
                  ) { social =>
                    setMakeSecure(social.token.access_token, social.userId) {
                      complete(StatusCodes.Created -> social.token)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    /**
      *
      * proposals from themes will not be retrieve since themes are not displayed on front
      */
    override def getVotedProposalsByUser: Route = get {
      path("user" / userId / "votes") { userId: UserId =>
        makeOperation("UserVotedProposals") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            parameters(
              (
                Symbol("votes").as[immutable.Seq[VoteKey]].?,
                Symbol("qualifications").as[immutable.Seq[QualificationKey]].?,
                Symbol("sort").?,
                Symbol("order").as[SortOrder].?,
                Symbol("limit").as[Int].?,
                Symbol("skip").as[Int].?
              )
            ) {
              (votes: Option[Seq[VoteKey]],
               qualifications: Option[Seq[QualificationKey]],
               sort: Option[String],
               order: Option[SortOrder],
               limit: Option[Int],
               skip: Option[Int]) =>
                if (userAuth.user.userId != userId) {
                  complete(StatusCodes.Forbidden)
                } else {
                  val defaultSort = Some("createdAt")
                  val defaultOrder = Some(Desc)
                  provideAsync(
                    proposalService.searchProposalsVotedByUser(
                      userId = userId,
                      filterVotes = votes,
                      filterQualifications = qualifications,
                      sort = Some(Sort(field = sort.orElse(defaultSort), mode = order.orElse(defaultOrder))),
                      limit = limit,
                      skip = skip,
                      requestContext = requestContext
                    )
                  ) { proposalsSearchResult =>
                    complete(proposalsSearchResult)
                  }
                }
            }
          }
        }
      }
    }

    override def register: Route = post {
      path("user") {
        makeOperation("RegisterUser", EndpointType.Public) { requestContext =>
          decodeRequest {
            entity(as[RegisterUserRequest]) { request: RegisterUserRequest =>
              val country: Country = request.country.orElse(requestContext.country).getOrElse(Country("FR"))
              val language: Language = request.language.orElse(requestContext.language).getOrElse(Language("fr"))

              val futureMaybeQuestion: Future[Option[Question]] = request.questionId match {
                case Some(questionId) => questionService.getQuestion(questionId)
                case _                => Future.successful(None)
              }

              provideAsync(futureMaybeQuestion) { maybeQuestion =>
                onSuccess(
                  userService
                    .register(
                      UserRegisterData(
                        email = request.email,
                        firstName = request.firstName,
                        lastName = request.lastName,
                        password = Some(request.password),
                        lastIp = requestContext.ipAddress,
                        dateOfBirth = request.dateOfBirth,
                        profession = request.profession,
                        postalCode = request.postalCode,
                        country = country,
                        language = language,
                        gender = request.gender,
                        socioProfessionalCategory = request.socioProfessionalCategory,
                        questionId = maybeQuestion.map(_.questionId),
                        optIn = request.optIn,
                        optInPartner = request.optInPartner,
                        politicalParty = request.politicalParty,
                        website = request.website.map(_.value)
                      ),
                      requestContext
                    )
                    .flatMap { user =>
                      sessionHistoryCoordinatorService
                        .convertSession(requestContext.sessionId, user.userId, requestContext)
                        .map(_ => user)
                    }
                ) { result =>
                  complete(StatusCodes.Created -> UserResponse(result))
                }
              }
            }
          }
        }
      }
    }

    override def validateAccountRoute: Route = {
      post {
        path("user" / userId / "validate" / Segment) { (userId: UserId, verificationToken: String) =>
          makeOperation("UserValidation", EndpointType.Public) { requestContext =>
            provideAsyncOrNotFound(
              persistentUserService
                .findUserByUserIdAndVerificationToken(userId, verificationToken)
            ) { user =>
              if (user.verificationTokenExpiresAt.forall(_.isBefore(DateHelper.now()))) {
                complete(StatusCodes.BadRequest)
              } else {
                onSuccess(
                  userService
                    .validateEmail(user = user, verificationToken = verificationToken)
                    .map { token =>
                      eventBusService.publish(
                        UserValidatedAccountEvent(
                          userId = userId,
                          country = user.country,
                          language = user.language,
                          requestContext = requestContext,
                          eventDate = DateHelper.now()
                        )
                      )
                      token
                    }
                ) { token =>
                  setMakeSecure(token.access_token, userId) {
                    complete(StatusCodes.NoContent)
                  }
                }
              }
            }
          }
        }
      }
    }

    override def resetPasswordRequestRoute: Route = {
      post {
        path("user" / "reset-password" / "request-reset") {
          makeOperation("ResetPasswordRequest", EndpointType.Public) { requestContext =>
            optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
              decodeRequest(entity(as[ResetPasswordRequest]) { request =>
                provideAsyncOrNotFound(persistentUserService.findByEmail(request.email.toLowerCase)) { user =>
                  onSuccess(userService.requestPasswordReset(user.userId).map { result =>
                    eventBusService.publish(
                      ResetPasswordEvent(
                        userId = user.userId,
                        connectedUserId = userAuth.map(_.user.userId),
                        country = user.country,
                        language = user.language,
                        requestContext = requestContext,
                        eventDate = DateHelper.now()
                      )
                    )
                    result
                  }) { _ =>
                    complete(StatusCodes.NoContent)
                  }
                }
              })
            }
          }
        }
      }
    }

    override def resetPasswordCheckRoute: Route = {
      post {
        path("user" / "reset-password" / "check-validity" / userId / Segment) { (userId: UserId, resetToken: String) =>
          makeOperation("ResetPasswordCheck", EndpointType.Public) { _ =>
            provideAsyncOrNotFound(persistentUserService.findUserByUserIdAndResetToken(userId, resetToken)) { user =>
              if (user.resetTokenExpiresAt.exists(_.isAfter(DateHelper.now()))) {
                complete(StatusCodes.NoContent)
              } else {
                complete(StatusCodes.BadRequest)
              }
            }
          }
        }
      }
    }

    override def resetPasswordRoute: Route = {
      post {
        path("user" / "reset-password" / "change-password" / userId) { userId =>
          makeOperation("ResetPassword", EndpointType.Public) { _ =>
            decodeRequest {
              entity(as[ResetPassword]) { request: ResetPassword =>
                provideAsyncOrNotFound(persistentUserService.findUserByUserIdAndResetToken(userId, request.resetToken)) {
                  user =>
                    if (user.resetTokenExpiresAt.forall(_.isBefore(DateHelper.now()))) {
                      complete(StatusCodes.BadRequest)
                    } else {
                      onSuccess(
                        userService
                          .updatePassword(
                            userId = userId,
                            resetToken = Some(request.resetToken),
                            password = request.password
                          )
                      ) { _ =>
                        complete(StatusCodes.NoContent)
                      }
                    }
                }
              }
            }
          }
        }
      }
    }

    override def rebuildUserHistory: Route = post {
      path("user" / userId / "reload-history") { userId =>
        makeOperation("ReloadUserHistory") { _ =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              userHistoryCoordinatorService.reloadHistory(userId)
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }

    override def rebuildAllUsersHistory: Route = post {
      path("user" / "reload-history") {
        makeOperation("ReloadAllUsersHistory") { _ =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
              userJournal
                .currentPersistenceIds()
                .runForeach(id => userHistoryCoordinatorService.reloadHistory(UserId(id)))

              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }

    /**
      *
      * proposals from themes will not be retrieve since themes are not displayed on front
      */
    override def getProposalsByUser: Route = get {
      path("user" / userId / "proposals") { userId: UserId =>
        makeOperation("UserProposals") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            parameters(
              (Symbol("sort").?, Symbol("order").as[SortOrder].?, Symbol("limit").as[Int].?, Symbol("skip").as[Int].?)
            ) { (sort: Option[String], order: Option[SortOrder], limit: Option[Int], skip: Option[Int]) =>
              val connectedUserId: UserId = userAuth.user.userId
              if (connectedUserId != userId) {
                complete(StatusCodes.Forbidden)
              } else {
                val defaultSort = Some("createdAt")
                val defaultOrder = Some(Desc)
                provideAsync(
                  proposalService.searchForUser(
                    userId = Some(userId),
                    query = SearchQuery(
                      filters = Some(
                        SearchFilters(
                          user = Some(UserSearchFilter(userId = userId)),
                          status = Some(StatusSearchFilter(ProposalStatus.statusMap.values.filter { status =>
                            status != ProposalStatus.Archived
                          }.toSeq))
                        )
                      ),
                      sort = Some(Sort(field = sort.orElse(defaultSort), mode = order.orElse(defaultOrder))),
                      limit = limit,
                      skip = skip
                    ),
                    requestContext = requestContext
                  )
                ) { proposalsSearchResult =>
                  complete(proposalsSearchResult)
                }
              }
            }
          }
        }
      }
    }

    override def patchUser: Route =
      patch {
        path("user") {
          makeOperation("PatchUser") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              decodeRequest {
                entity(as[UpdateUserRequest]) { request: UpdateUserRequest =>
                  provideAsyncOrNotFound(userService.getUser(userAuth.user.userId)) { user =>
                    val optInNewsletterHasChanged: Boolean = (request.optInNewsletter, user.profile) match {
                      case (Some(value), Some(profileValue)) => value != profileValue.optInNewsletter
                      case (Some(_), None)                   => true
                      case _                                 => false
                    }

                    val profile: Profile = user.profile.getOrElse(Profile.parseProfile().get)

                    val updatedProfile: Profile = profile.copy(
                      dateOfBirth = request.dateOfBirth match {
                        case None     => user.profile.flatMap(_.dateOfBirth)
                        case Some("") => None
                        case date     => date.map(date => LocalDate.parse(date))
                      },
                      profession = RequestHelper.updateValue(user.profile.flatMap(_.profession), request.profession),
                      postalCode = RequestHelper.updateValue(user.profile.flatMap(_.postalCode), request.postalCode),
                      phoneNumber = RequestHelper.updateValue(user.profile.flatMap(_.phoneNumber), request.phoneNumber),
                      description = RequestHelper.updateValue(user.profile.flatMap(_.description), request.description),
                      optInNewsletter = request.optInNewsletter.getOrElse(user.profile.exists(_.optInNewsletter)),
                      gender = RequestHelper
                        .updateValue(user.profile.flatMap(_.gender.map(_.shortName)), request.gender)
                        .flatMap(Gender.matchGender),
                      genderName = request.genderName.orElse(user.profile.flatMap(_.genderName)),
                      socioProfessionalCategory = RequestHelper
                        .updateValue(
                          user.profile.flatMap(_.socioProfessionalCategory.map(_.shortName)),
                          request.socioProfessionalCategory
                        )
                        .flatMap(SocioProfessionalCategory.matchSocioProfessionalCategory),
                      politicalParty = RequestHelper
                        .updateValue(user.profile.flatMap(_.politicalParty), request.politicalParty),
                      website = RequestHelper
                        .updateValue(user.profile.flatMap(_.website), request.website.map(_.value))
                    )

                    onSuccess(
                      userService.update(
                        user.copy(
                          firstName = request.firstName.orElse(user.firstName),
                          lastName = request.lastName.orElse(user.lastName),
                          organisationName = request.organisationName.orElse(user.organisationName),
                          country = request.country.getOrElse(user.country),
                          language = request.language.getOrElse(user.language),
                          profile = Some(updatedProfile)
                        ),
                        requestContext
                      )
                    ) { user: User =>
                      if (optInNewsletterHasChanged && user.userType == UserType.UserTypeUser) {
                        eventBusService.publish(
                          UserUpdatedOptInNewsletterEvent(
                            connectedUserId = Some(userAuth.user.userId),
                            userId = user.userId,
                            requestContext = requestContext,
                            eventDate = DateHelper.now(),
                            country = user.country,
                            language = user.language,
                            optInNewsletter = user.profile.exists(_.optInNewsletter)
                          )
                        )
                      }
                      if (user.userType == UserType.UserTypeOrganisation) {
                        eventBusService.publish(
                          OrganisationUpdatedEvent(
                            connectedUserId = Some(user.userId),
                            userId = user.userId,
                            requestContext = requestContext,
                            country = user.country,
                            language = user.language,
                            eventDate = DateHelper.now()
                          )
                        )
                      }
                      provideAsync(userService.getFollowedUsers(user.userId)) { followedUsers =>
                        complete(StatusCodes.NoContent -> UserResponse(user, followedUsers))
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

    override def changePassword: Route = post {
      path("user" / userId / "change-password") { userId: UserId =>
        makeOperation("ChangePassword") { _ =>
          decodeRequest {
            entity(as[ChangePasswordRequest]) { request: ChangePasswordRequest =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                val connectedUserId: UserId = userAuth.user.userId
                if (connectedUserId != userId) {
                  complete(StatusCodes.Forbidden)
                } else {
                  provideAsync(userService.getUserByUserIdAndPassword(userId, request.actualPassword)) {
                    case Some(_) =>
                      provideAsync(userService.updatePassword(userId, None, request.newPassword)) { _ =>
                        complete(StatusCodes.OK)
                      }
                    case None =>
                      complete(
                        StatusCodes.BadRequest -> Seq(
                          ValidationError("password", "invalid_password", Some("Wrong password"))
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

    override def deleteUser: Route = post {
      path("user" / userId / "delete") { userId: UserId =>
        makeOperation("deleteUser") { requestContext =>
          decodeRequest {
            entity(as[DeleteUserRequest]) { request: DeleteUserRequest =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                if (userAuth.user.userId != userId) {
                  complete(StatusCodes.Forbidden)
                } else {
                  provideAsync(userService.getUserByUserIdAndPassword(userId, request.password)) {
                    case Some(user) =>
                      provideAsync(userService.anonymize(user, userAuth.user.userId, requestContext)) { _ =>
                        provideAsync(oauth2DataHandler.removeTokenByUserId(userId)) { _ =>
                          complete(StatusCodes.OK)
                        }
                      }
                    case None =>
                      complete(
                        StatusCodes.BadRequest -> Seq(
                          ValidationError("password", "invalid_password", Some("Wrong password"))
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

    override def followUser: Route =
      post {
        path("user" / userId / "follow") { userId =>
          makeOperation("FollowUser") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                if (!user.publicProfile) {
                  complete(StatusCodes.Forbidden)
                } else {
                  provideAsync(userService.getFollowedUsers(userAuth.user.userId)) { followedUsers =>
                    if (followedUsers.contains(userId)) {
                      complete(StatusCodes.BadRequest)
                    } else {
                      onSuccess(userService.followUser(userId, userAuth.user.userId, requestContext)) { _ =>
                        complete(StatusCodes.OK)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

    override def unfollowUser: Route =
      post {
        path("user" / userId / "unfollow") { userId =>
          makeOperation("FollowUser") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              provideAsyncOrNotFound(userService.getUser(userId)) { _ =>
                provideAsync(userService.getFollowedUsers(userAuth.user.userId)) { followedUsers =>
                  if (!followedUsers.contains(userId)) {
                    complete(
                      StatusCodes.BadRequest -> Seq(
                        ValidationError("userId", "invalid_state", Some("User already unfollowed"))
                      )
                    )
                  } else {
                    onSuccess(userService.unfollowUser(userId, userAuth.user.userId, requestContext)) { _ =>
                      complete(StatusCodes.OK)
                    }
                  }
                }
              }
            }
          }
        }
      }

    override def reconnectInfo: Route = {
      post {
        path("user" / userId / "reconnect") { userId =>
          makeOperation("ReconnectInfo") { _ =>
            provideAsyncOrNotFound(userService.reconnectInfo(userId)) { reconnectInfo =>
              complete(StatusCodes.OK -> reconnectInfo)
            }
          }
        }
      }
    }

    override def resendValidationEmail: Route = {
      post {
        path("user" / "validation-email") {
          makeOperation("ResendValidationEmail") { requestContext =>
            decodeRequest {
              entity(as[ResendValidationEmailRequest]) { entity =>
                provideAsync(userService.getUserByEmail(entity.email)) { maybeUser =>
                  maybeUser.foreach { user =>
                    eventBusService.publish(
                      ResendValidationEmailEvent(
                        connectedUserId = None,
                        eventDate = DateHelper.now(),
                        userId = user.userId,
                        country = user.country,
                        language = user.language,
                        requestContext = requestContext
                      )
                    )
                  }
                  complete(StatusCodes.NoContent)
                }
              }
            }
          }
        }
      }
    }

    override def uploadAvatar: Route = {
      post {
        path("user" / userId / "upload-avatar") { userId =>
          makeOperation("UserUploadAvatar") { requestContext =>
            makeOAuth2 { user =>
              authorize(user.user.userId == userId || user.user.roles.contains(RoleAdmin)) {
                val date = DateHelper.now()
                def uploadFile(extension: String, contentType: String, fileContent: Content): Future[String] = {
                  storageService
                    .uploadFile(
                      FileType.Avatar,
                      s"${date.getYear}/${date.getMonthValue}/${userId.value}/${idGenerator.nextId()}$extension",
                      contentType,
                      fileContent
                    )
                }
                uploadImageAsync("data", uploadFile, sizeLimit = Some(storageConfiguration.maxFileSize)) {
                  (path, file) =>
                    file.delete()
                    provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                      val modifiedProfile = user.profile match {
                        case Some(profile) => Some(profile.copy(avatarUrl = Some(path)))
                        case None          => Profile.parseProfile(avatarUrl = Some(path))
                      }

                      onSuccess(userService.update(user.copy(profile = modifiedProfile), requestContext)) { _ =>
                        complete(UploadResponse(path))
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
