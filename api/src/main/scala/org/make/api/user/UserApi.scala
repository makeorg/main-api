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

import java.time.{LocalDate, ZonedDateTime}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import grizzled.slf4j.Logging
import io.swagger.annotations._

import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.proposal.{ProposalServiceComponent, ProposalsResultResponse, ProposalsResultSeededResponse}
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical._
import org.make.api.technical.CsvReceptacle._
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.directives.ClientDirectives
import org.make.api.technical.storage._
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.social.SocialServiceComponent
import org.make.api.user.validation.UserRegistrationValidatorComponent
import org.make.api.userhistory.{UserHistoryCoordinatorServiceComponent, _}
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.common.indexed.Sort
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal._
import org.make.core.question.Question
import org.make.core.reference.Country
import org.make.core.user.Role.RoleAdmin
import org.make.core.user._
import scalaoauth2.provider.AuthInfo

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

  @Path(value = "/{userId}/profile")
  @ApiOperation(
    value = "get-user-profile",
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserProfileResponse]))
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
  def getUserProfile: Route
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

  @Path(value = "/current")
  @ApiOperation(
    value = "current-user",
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[CurrentUserResponse]))
  )
  def currentUser: Route

  @Path(value = "/login/social")
  @ApiOperation(value = "social-login", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value =
      Array(new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.SocialLoginRequest"))
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SocialLoginResponse]))
  )
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
      new ApiImplicitParam(
        name = "votes",
        paramType = "query",
        dataType = "string",
        allowableValues = "agree,disagree,neutral",
        allowMultiple = true
      ),
      new ApiImplicitParam(
        name = "qualifications",
        paramType = "query",
        dataType = "string",
        allowableValues =
          "likeIt,doable,platitudeAgree,noWay,impossible,platitudeDisagree,doNotUnderstand,noOpinion,doNotCare",
        allowMultiple = true
      ),
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.RegisterUserRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.Created, message = "Created", response = classOf[UserResponse]))
  )
  def register: Route

  @ApiOperation(value = "verifiy-user-email", httpMethod = "POST", code = HttpCodes.NoContent)
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

  @ApiOperation(value = "reset-password-request-token", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/reset-password/request-reset")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.user.ResetPasswordRequest")
    )
  )
  def resetPasswordRequestRoute: Route

  @ApiOperation(value = "reset-password-token-check", httpMethod = "POST", code = HttpCodes.NoContent)
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

  @ApiOperation(value = "reset-password", httpMethod = "POST", code = HttpCodes.NoContent)
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
  def patchCurrentUser: Route

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

  @ApiOperation(value = "resend-validation-email", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
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

  @ApiOperation(
    value = "update-user-profile",
    httpMethod = "PUT",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "user", description = "application user"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserProfileResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.UserProfileRequest")
    )
  )
  @Path(value = "/{userId}/profile")
  def modifyUserProfile: Route

  @Path(value = "/privacy-policy")
  @ApiOperation(value = "get-privacy-policy-approval-date", httpMethod = "POST")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.PrivacyPolicyRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserPrivacyPolicyResponse]))
  )
  def getPrivacyPolicy: Route

  @Path(value = "/social/privacy-policy")
  @ApiOperation(value = "get-social-privacy-policy-approval-date", httpMethod = "POST")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.user.SocialPrivacyPolicyRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserPrivacyPolicyResponse]))
  )
  def getSocialPrivacyPolicy: Route

  @Path(value = "/check-registration")
  @ApiOperation(value = "check-registration", httpMethod = "POST")
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.CheckRegistrationRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  def checkRegistration: Route

  def routes: Route =
    getMe ~
      currentUser ~
      getUser ~
      register ~
      socialLogin ~
      resetPasswordRequestRoute ~
      resetPasswordCheckRoute ~
      resetPasswordRoute ~
      validateAccountRoute ~
      getVotedProposalsByUser ~
      getProposalsByUser ~
      patchCurrentUser ~
      changePassword ~
      deleteUser ~
      followUser ~
      unfollowUser ~
      reconnectInfo ~
      resendValidationEmail ~
      uploadAvatar ~
      getUserProfile ~
      modifyUserProfile ~
      getPrivacyPolicy ~
      getSocialPrivacyPolicy ~
      checkRegistration

  val userId: PathMatcher1[UserId] =
    Segment.map(id => UserId(id))
}

trait UserApiComponent {
  def userApi: UserApi
}

trait DefaultUserApiComponent
    extends UserApiComponent
    with MakeAuthenticationDirectives
    with Logging
    with ParameterExtractors {

  this: MakeDirectivesDependencies
    with UserServiceComponent
    with SocialServiceComponent
    with ProposalServiceComponent
    with QuestionServiceComponent
    with EventBusServiceComponent
    with PersistentUserServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent
    with StorageServiceComponent
    with StorageConfigurationComponent
    with UserRegistrationValidatorComponent
    with ClientDirectives =>

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

    override def getUserProfile: Route = {
      get {
        path("user" / userId / "profile") { userId =>
          makeOperation("GetUserProfile") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              authorize(userId == userAuth.user.userId) {
                provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                  complete(
                    UserProfileResponse(
                      firstName = user.firstName,
                      lastName = user.lastName,
                      dateOfBirth = user.profile.flatMap(_.dateOfBirth),
                      avatarUrl = user.profile.flatMap(_.avatarUrl),
                      profession = user.profile.flatMap(_.profession),
                      description = user.profile.flatMap(_.description),
                      postalCode = user.profile.flatMap(_.postalCode),
                      optInNewsletter = user.profile.forall(_.optInNewsletter),
                      website = user.profile.flatMap(_.website)
                    )
                  )
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

    override def currentUser: Route = {
      get {
        path("user" / "current") {
          makeOperation("GetCurrentUser") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              provideAsyncOrNotFound(userService.getUser(userAuth.user.userId)) { user =>
                complete(
                  CurrentUserResponse(
                    userId = user.userId,
                    email = user.email,
                    displayName = user.displayName,
                    userType = user.userType,
                    roles = user.roles,
                    hasPassword = user.hashedPassword.isDefined,
                    enabled = user.enabled,
                    emailVerified = user.emailVerified,
                    country = user.country,
                    avatarUrl = user.profile.flatMap(_.avatarUrl),
                    privacyPolicyApprovalDate = user.privacyPolicyApprovalDate
                  )
                )
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
              extractClientOrDefault { client =>
                val country: Country = request.country.orElse(requestContext.country).getOrElse(Country("FR"))

                val futureMaybeQuestion: Future[Option[Question]] = requestContext.questionId match {
                  case Some(questionId) => questionService.getQuestion(questionId)
                  case _                => Future.successful(None)
                }
                val privacyPolicyApprovalDate: Option[ZonedDateTime] = request.approvePrivacyPolicy match {
                  case Some(true) => Some(DateHelper.now())
                  case _          => None
                }
                provideAsync(futureMaybeQuestion) { maybeQuestion =>
                  onSuccess(
                    socialService
                      .login(
                        request.provider,
                        request.token,
                        country,
                        maybeQuestion.map(_.questionId),
                        requestContext,
                        client.clientId,
                        privacyPolicyApprovalDate
                      )
                      .flatMap {
                        case (userId, social) =>
                          sessionHistoryCoordinatorService
                            .convertSession(requestContext.sessionId, userId, requestContext)
                            .map(_ => (userId, social))
                      }
                  ) {
                    case (userId, social) =>
                      setMakeSecure(requestContext.applicationName, social.toTokenResponse, userId) {
                        complete(StatusCodes.Created -> social)
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
              "votes".csv[VoteKey],
              "qualifications".csv[QualificationKey],
              "sort".?,
              "order".as[Order].?,
              "limit".as[Int].?,
              "skip".as[Int].?
            ) {
              (
                votes: Option[Seq[VoteKey]],
                qualifications: Option[Seq[QualificationKey]],
                sort: Option[String],
                order: Option[Order],
                limit: Option[Int],
                skip: Option[Int]
              ) =>
                if (userAuth.user.userId != userId) {
                  complete(StatusCodes.Forbidden)
                } else {
                  val defaultSort = Some("createdAt")
                  val defaultOrder = Some(Order.desc)
                  provideAsync(
                    proposalService.searchProposalsVotedByUser(
                      userId = userId,
                      filterVotes = votes,
                      filterQualifications = qualifications,
                      sort = Some(
                        Sort(field = sort.orElse(defaultSort), mode = order.orElse(defaultOrder).map(_.sortOrder))
                      ),
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
              Validation.validate(userRegistrationValidator.requirements(request): _*)
              val country: Country = request.country.orElse(requestContext.country).getOrElse(Country("FR"))

              val futureMaybeQuestion: Future[Option[Question]] = request.questionId match {
                case Some(questionId) => questionService.getQuestion(questionId)
                case _                => Future.successful(None)
              }

              provideAsync(futureMaybeQuestion) { maybeQuestion =>
                val privacyPolicyApprovalDate = request.approvePrivacyPolicy match {
                  case Some(true) => Some(DateHelper.now())
                  case _          => None
                }
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
                        gender = request.gender,
                        socioProfessionalCategory = request.socioProfessionalCategory,
                        questionId = maybeQuestion.map(_.questionId),
                        optIn = request.optIn,
                        optInPartner = request.optInPartner,
                        politicalParty = request.politicalParty,
                        website = request.website.map(_.value),
                        legalMinorConsent = request.legalMinorConsent,
                        legalAdvisorApproval = request.legalAdvisorApproval,
                        privacyPolicyApprovalDate = privacyPolicyApprovalDate
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
                          requestContext = requestContext,
                          eventDate = DateHelper.now(),
                          eventId = Some(idGenerator.nextEventId())
                        )
                      )
                      token
                    }
                ) { token =>
                  setMakeSecure(requestContext.applicationName, token, userId) {
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
                        requestContext = requestContext,
                        eventDate = DateHelper.now(),
                        eventId = Some(idGenerator.nextEventId())
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

    /**
      *
      * proposals from themes will not be retrieve since themes are not displayed on front
      */
    override def getProposalsByUser: Route = get {
      path("user" / userId / "proposals") { userId: UserId =>
        makeOperation("UserProposals") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            parameters("sort".?, "order".as[Order].?, "limit".as[Int].?, "skip".as[Int].?) {
              (sort: Option[String], order: Option[Order], limit: Option[Int], skip: Option[Int]) =>
                val connectedUserId: UserId = userAuth.user.userId
                if (connectedUserId != userId) {
                  complete(StatusCodes.Forbidden)
                } else {
                  val defaultSort = Some("createdAt")
                  val defaultOrder = Some(Order.desc)
                  provideAsync(
                    proposalService.searchForUser(
                      userId = Some(userId),
                      query = SearchQuery(
                        filters = Some(
                          SearchFilters(
                            users = Some(UserSearchFilter(userIds = Seq(userId))),
                            status =
                              Some(StatusSearchFilter(ProposalStatus.values.filter(_ != ProposalStatus.Archived)))
                          )
                        ),
                        sort = Some(
                          Sort(field = sort.orElse(defaultSort), mode = order.orElse(defaultOrder).map(_.sortOrder))
                        ),
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

    override def patchCurrentUser: Route =
      patch {
        path("user") {
          makeOperation("PatchCurrentUser") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              decodeRequest {
                entity(as[UpdateUserRequest]) { request: UpdateUserRequest =>
                  provideAsyncOrNotFound(userService.getUser(userAuth.user.userId)) { user =>
                    val optInNewsletterHasChanged: Boolean = (request.optInNewsletter, user.profile) match {
                      case (Some(value), Some(profileValue)) => value != profileValue.optInNewsletter
                      case (Some(_), None)                   => true
                      case _                                 => false
                    }

                    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
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
                        .updateValue(user.profile.flatMap(_.gender.map(_.value)), request.gender)
                        .flatMap(Gender.withValueOpt),
                      genderName = request.genderName.orElse(user.profile.flatMap(_.genderName)),
                      socioProfessionalCategory = RequestHelper
                        .updateValue(
                          user.profile.flatMap(_.socioProfessionalCategory.map(_.value)),
                          request.socioProfessionalCategory
                        )
                        .flatMap(SocioProfessionalCategory.withValueOpt),
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
                            optInNewsletter = user.profile.exists(_.optInNewsletter),
                            eventId = Some(idGenerator.nextEventId())
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
                            eventDate = DateHelper.now(),
                            eventId = Some(idGenerator.nextEventId())
                          )
                        )
                      }
                      provideAsync(userService.getFollowedUsers(user.userId)) { followedUsers =>
                        complete(StatusCodes.OK -> UserResponse(user, followedUsers))
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
                      provideAsync(
                        userService.anonymize(user, userAuth.user.userId, requestContext, Anonymization.Explicit)
                      ) { _ =>
                        provideAsync(oauth2DataHandler.removeTokenByUserId(userId)) { _ =>
                          addCookies(requestContext.applicationName, logoutCookies()) { complete(StatusCodes.OK) }
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
                        requestContext = requestContext,
                        eventId = Some(idGenerator.nextEventId())
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
                def uploadFile(extension: String, contentType: String, fileContent: Content): Future[String] =
                  storageService.uploadUserAvatar(userId, extension, contentType, fileContent)
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

    override def modifyUserProfile: Route = {
      put {
        path("user" / userId / "profile") { userId =>
          makeOperation("modifyUserProfile") { requestContext =>
            makeOAuth2 { user =>
              authorize(user.user.userId == userId) {
                decodeRequest {
                  entity(as[UserProfileRequest]) { entity =>
                    Validation.validate(userRegistrationValidator.requirements(entity): _*)
                    provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                      val modifiedProfile = user.profile
                        .orElse(Profile.parseProfile())
                        .map(
                          _.copy(
                            dateOfBirth = entity.dateOfBirth,
                            avatarUrl = entity.avatarUrl.map(_.value),
                            profession = entity.profession,
                            description = entity.description,
                            postalCode = entity.postalCode,
                            optInNewsletter = entity.optInNewsletter,
                            website = entity.website.map(_.value)
                          )
                        )

                      val modifiedUser =
                        user.copy(
                          profile = modifiedProfile,
                          firstName = Some(entity.firstName),
                          lastName = entity.lastName
                        )

                      provideAsync(userService.update(modifiedUser, requestContext)) { result =>
                        complete(
                          UserProfileResponse(
                            firstName = result.firstName,
                            lastName = result.lastName,
                            dateOfBirth = result.profile.flatMap(_.dateOfBirth),
                            avatarUrl = result.profile.flatMap(_.avatarUrl),
                            profession = result.profile.flatMap(_.profession),
                            description = result.profile.flatMap(_.description),
                            postalCode = result.profile.flatMap(_.postalCode),
                            optInNewsletter = result.profile.forall(_.optInNewsletter),
                            website = result.profile.flatMap(_.website)
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

    override def getPrivacyPolicy: Route = {
      post {
        path("user" / "privacy-policy") {
          makeOperation("GetPrivacyPolicy") { _ =>
            decodeRequest {
              entity(as[PrivacyPolicyRequest]) { request =>
                provideAsync(userService.getUserByEmailAndPassword(request.email, request.password)) {
                  case None =>
                    complete(
                      StatusCodes.BadRequest -> Seq(
                        ValidationError("email", "invalid", Some("email or password is invalid."))
                      )
                    )
                  case Some(user) => complete(UserPrivacyPolicyResponse(user.privacyPolicyApprovalDate))
                }
              }
            }
          }
        }
      }
    }

    override def getSocialPrivacyPolicy: Route = {
      post {
        path("user" / "social" / "privacy-policy") {
          makeOperation("GetSocialPrivacyPolicy") { _ =>
            decodeRequest {
              entity(as[SocialPrivacyPolicyRequest]) { request =>
                provideAsync(socialService.getUserByProviderAndToken(request.provider, request.token)) { maybeUser =>
                  complete(UserPrivacyPolicyResponse(maybeUser.flatMap(_.privacyPolicyApprovalDate)))
                }
              }
            }
          }
        }
      }
    }

    override def checkRegistration: Route = {
      post {
        path("user" / "check-registration") {
          makeOperation("CheckRegistration") { _ =>
            decodeRequest {
              entity(as[CheckRegistrationRequest]) { request =>
                provideAsync(userService.getUserByEmail(request.email)) {
                  case None       => complete(StatusCodes.NoContent)
                  case Some(user) => failWith(EmailAlreadyRegisteredException(request.email))
                }
              }
            }
          }
        }
      }
    }
  }
}
