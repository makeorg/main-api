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

import java.net.URLEncoder
import java.time.{LocalDate, ZonedDateTime}

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Set-Cookie`, HttpCookie}
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.sksamuel.elastic4s.searches.sort.SortOrder
import com.sksamuel.elastic4s.searches.sort.SortOrder.Desc
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.proposal.{ProposalServiceComponent, ProposalsResultResponse, ProposalsResultSeededResponse}
import org.make.api.question.QuestionServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{
  EventBusServiceComponent,
  IdGeneratorComponent,
  MakeAuthenticationDirectives,
  ReadJournalComponent
}
import org.make.api.user.UserUpdateEvent.{UserUpdateValidatedEvent, UserUpdatedOptInNewsletterEvent}
import org.make.api.user.social.SocialServiceComponent
import org.make.api.userhistory.UserEvent._
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.Validation._
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.common.indexed.Sort
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.RoleAdmin
import org.make.core.user.{MailingErrorLog, Role, User, UserId}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field
import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}

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
      new ApiImplicitParam(name = "qualifications", paramType = "query", dataType = "string")
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

  @ApiOperation(value = "Resend validation email", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/{userId}/resend-validation-email")
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
  def resendValidationEmail: Route

  @ApiOperation(value = "subscribe-newsletter", httpMethod = "POST", code = HttpCodes.NoContent)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.user.SubscribeToNewsLetter")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No content")))
  @Path(value = "/newsletter")
  def subscribeToNewsLetter: Route

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
      )
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
      subscribeToNewsLetter ~
      validateAccountRoute ~
      getVotedProposalsByUser ~
      getProposalsByUser ~
      patchUser ~
      changePassword ~
      deleteUser ~
      followUser ~
      unfollowUser

  val userId: PathMatcher1[UserId] =
    Segment.flatMap(id => Try(UserId(id)).toOption)
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
    with OperationServiceComponent
    with QuestionServiceComponent
    with EventBusServiceComponent
    with PersistentUserServiceComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent =>

  override lazy val userApi: UserApi = new UserApi {

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
        makeOperation("SocialLogin") { requestContext =>
          decodeRequest {
            entity(as[SocialLoginRequest]) { request: SocialLoginRequest =>
              extractClientIP { clientIp =>
                val ip = clientIp.toOption.map(_.getHostAddress).getOrElse("unknown")
                onSuccess(
                  socialService
                    .login(
                      request.provider,
                      request.token,
                      request.country.orElse(requestContext.country).getOrElse(Country("FR")),
                      request.language.orElse(requestContext.language).getOrElse(Language("fr")),
                      Some(ip),
                      requestContext
                    )
                    .flatMap { social =>
                      sessionHistoryCoordinatorService
                        .convertSession(requestContext.sessionId, social.userId)
                        .map(_ => social)
                    }
                ) { social =>
                  mapResponseHeaders { responseHeaders =>
                    if (responseHeaders.exists(
                          header =>
                            header.name() == `Set-Cookie`.name && header
                              .asInstanceOf[`Set-Cookie`]
                              .cookie
                              .name == makeSettings.SessionCookie.name
                        )) {
                      responseHeaders
                    } else {
                      responseHeaders ++ Seq(
                        `Set-Cookie`(
                          HttpCookie(
                            name = makeSettings.SessionCookie.name,
                            value = social.token.access_token,
                            secure = makeSettings.SessionCookie.isSecure,
                            httpOnly = true,
                            maxAge = Some(makeSettings.SessionCookie.lifetime.toSeconds),
                            path = Some("/"),
                            domain = Some(makeSettings.SessionCookie.domain)
                          )
                        )
                      )
                    }
                  } {
                    complete(StatusCodes.Created -> social.token)
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
            parameters(('votes.as[immutable.Seq[VoteKey]].?, 'qualifications.as[immutable.Seq[QualificationKey]].?)) {
              (votes: Option[Seq[VoteKey]], qualifications: Option[Seq[QualificationKey]]) =>
                if (userAuth.user.userId != userId) {
                  complete(StatusCodes.Forbidden)
                } else {
                  provideAsync(
                    operationService
                      .find(slug = None, country = None, maybeSource = requestContext.source, openAt = None)
                  ) { operations =>
                    provideAsync(
                      proposalService.searchProposalsVotedByUser(
                        userId = userId,
                        filterVotes = votes,
                        filterQualifications = qualifications,
                        requestContext = requestContext
                      )
                    ) { proposalsSearchResult =>
                      val proposalListFiltered = proposalsSearchResult.results.filter { proposal =>
                        proposal.operationId
                          .exists(operationId => operations.map(_.operationId).contains(operationId))
                      }
                      val result =
                        proposalsSearchResult.copy(total = proposalListFiltered.size, results = proposalListFiltered)
                      complete(result)
                    }
                  }
                }
            }
          }
        }
      }
    }

    override def register: Route = post {
      path("user") {
        makeOperation("RegisterUser") { requestContext =>
          decodeRequest {
            entity(as[RegisterUserRequest]) { request: RegisterUserRequest =>
              val country: Country = request.country.orElse(requestContext.country).getOrElse(Country("FR"))
              val language: Language = request.language.orElse(requestContext.language).getOrElse(Language("fr"))

              val futureMaybeQuestion: Future[Option[Question]] =
                questionService.findQuestionByQuestionIdOrThemeOrOperation(
                  maybeOperationId = None,
                  maybeQuestionId = request.questionId,
                  maybeThemeId = None,
                  country = country,
                  language = language
                )

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
                        optInPartner = request.optInPartner
                      ),
                      requestContext
                    )
                    .flatMap { user =>
                      sessionHistoryCoordinatorService
                        .convertSession(requestContext.sessionId, user.userId)
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
          makeOperation("UserValidation") { requestContext =>
            provideAsyncOrNotFound(
              persistentUserService
                .findUserByUserIdAndVerificationToken(userId, verificationToken)
            ) { user =>
              if (user.verificationTokenExpiresAt.forall(_.isBefore(DateHelper.now()))) {
                complete(StatusCodes.BadRequest)
              } else {
                onSuccess(
                  userService
                    .validateEmail(verificationToken = verificationToken)
                    .map { result =>
                      eventBusService.publish(
                        UserValidatedAccountEvent(
                          userId = userId,
                          country = user.country,
                          language = user.language,
                          requestContext = requestContext,
                          eventDate = DateHelper.now()
                        )
                      )
                      eventBusService
                        .publish(UserUpdateValidatedEvent(userId = Some(user.userId), eventDate = DateHelper.now()))

                      result
                    }
                ) { _ =>
                  complete(StatusCodes.NoContent)
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
          makeOperation("ResetPasswordRequest") { requestContext =>
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
          makeOperation("ResetPasswordCheck") { _ =>
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
          makeOperation("ResetPassword") { _ =>
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

    override def resendValidationEmail: Route = post {
      path("user" / userId / "resend-validation-email") { userId =>
        makeOperation("ResendValidateEmail") { requestContext =>
          makeOAuth2 { userAuth =>
            authorize(userId == userAuth.user.userId || userAuth.user.roles.contains(RoleAdmin)) {
              provideAsyncOrNotFound(persistentUserService.get(userId)) { user =>
                eventBusService.publish(
                  ResendValidationEmailEvent(
                    userId = userId,
                    connectedUserId = userAuth.user.userId,
                    country = user.country,
                    language = user.language,
                    requestContext = requestContext
                  )
                )
                complete(StatusCodes.NoContent)
              }
            }
          }
        }
      }
    }

    override def subscribeToNewsLetter: Route = post {
      path("user" / "newsletter") {
        makeOperation("SubscribeToNewsletter") { _ =>
          decodeRequest {
            entity(as[SubscribeToNewsLetter]) { request: SubscribeToNewsLetter =>
              // Keep as info in case sending form would fail
              logger.info(s"inscribing ${request.email} to newsletter")

              val encodedEmailFieldName = URLEncoder.encode("fields[Email]", "UTF-8")
              val encodedEmail = URLEncoder.encode(request.email.toLowerCase, "UTF-8")
              val encodedName = URLEncoder.encode("VFF Séquence Test", "UTF-8")

              val httpRequest = HttpRequest(
                method = HttpMethods.POST,
                uri = makeSettings.newsletterUrl,
                entity = HttpEntity(
                  ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`),
                  s"$encodedEmailFieldName=$encodedEmail&name=$encodedName"
                )
              )
              logger.debug(s"Subscribe newsletter request: ${httpRequest.toString}")
              val futureHttpResponse: Future[HttpResponse] =
                Http(actorSystem).singleRequest(httpRequest)

              onSuccess(futureHttpResponse) { httpResponse =>
                httpResponse.status match {
                  case StatusCodes.OK => complete(StatusCodes.NoContent)
                  case _              => complete(StatusCodes.ServiceUnavailable)
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
            parameters(('sort.?, 'order.as[SortOrder].?)) { (sort: Option[String], order: Option[SortOrder]) =>
              val connectedUserId: UserId = userAuth.user.userId
              if (connectedUserId != userId) {
                complete(StatusCodes.Forbidden)
              } else {
                provideAsync(
                  operationService.find(slug = None, country = None, maybeSource = requestContext.source, openAt = None)
                ) { operations =>
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
                        sort = Some(Sort(field = sort.orElse(defaultSort), mode = order.orElse(defaultOrder)))
                      ),
                      requestContext = requestContext
                    )
                  ) { proposalsSearchResult =>
                    val proposalListFiltered = proposalsSearchResult.results.filter { proposal =>
                      proposal.operationId
                        .exists(operationId => operations.map(_.operationId).contains(operationId))
                    }
                    val result =
                      proposalsSearchResult.copy(total = proposalListFiltered.size, results = proposalListFiltered)
                    complete(result)
                  }
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

                    val profile: Profile = user.profile.getOrElse(Profile.default)

                    val updatedProfile: Profile = profile.copy(
                      dateOfBirth = request.dateOfBirth match {
                        case None     => user.profile.flatMap(_.dateOfBirth)
                        case Some("") => None
                        case date     => date.map(date => LocalDate.parse(date))
                      },
                      profession = UpdateUserRequest.updateValue(user.profile.flatMap(_.profession), request.profession),
                      postalCode = UpdateUserRequest.updateValue(user.profile.flatMap(_.postalCode), request.postalCode),
                      phoneNumber =
                        UpdateUserRequest.updateValue(user.profile.flatMap(_.phoneNumber), request.phoneNumber),
                      description =
                        UpdateUserRequest.updateValue(user.profile.flatMap(_.description), request.description),
                      optInNewsletter = request.optInNewsletter.getOrElse(user.profile.exists(_.optInNewsletter)),
                      gender = UpdateUserRequest
                        .updateValue(user.profile.flatMap(_.gender.map(_.shortName)), request.gender)
                        .flatMap(Gender.matchGender),
                      genderName = request.genderName.orElse(user.profile.flatMap(_.genderName)),
                      socioProfessionalCategory = UpdateUserRequest
                        .updateValue(
                          user.profile.flatMap(_.socioProfessionalCategory.map(_.shortName)),
                          request.socioProfessionalCategory
                        )
                        .flatMap(SocioProfessionalCategory.matchSocioProfessionalCategory)
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
                      if (optInNewsletterHasChanged && !user.isOrganisation) {
                        eventBusService.publish(
                          UserUpdatedOptInNewsletterEvent(
                            userId = Some(user.userId),
                            eventDate = DateHelper.now(),
                            optInNewsletter = user.profile.exists(_.optInNewsletter)
                          )
                        )
                      }
                      if (user.isOrganisation) {
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
                      complete(StatusCodes.BadRequest -> Seq(ValidationError("password", Some("Wrong password"))))
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
        makeOperation("deleteUser") { _ =>
          decodeRequest {
            entity(as[DeleteUserRequest]) { request: DeleteUserRequest =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                if (userAuth.user.userId != userId) {
                  complete(StatusCodes.Forbidden)
                } else {
                  provideAsync(userService.getUserByUserIdAndPassword(userId, request.password)) {
                    case Some(user) =>
                      provideAsync(userService.anonymize(user)) { _ =>
                        provideAsync(oauth2DataHandler.removeTokenByUserId(userId)) { _ =>
                          complete(StatusCodes.OK)
                        }
                      }
                    case None =>
                      complete(StatusCodes.BadRequest -> Seq(ValidationError("password", Some("Wrong password"))))
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
          makeOperation("FollowUser") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              provideAsyncOrNotFound(userService.getUser(userId)) { user =>
                if (!user.publicProfile) {
                  complete(StatusCodes.Forbidden)
                } else {
                  provideAsync(userService.getFollowedUsers(userAuth.user.userId)) { followedUsers =>
                    if (followedUsers.contains(userId)) {
                      complete(StatusCodes.BadRequest)
                    } else {
                      onSuccess(userService.followUser(followedUserId = userId, userId = userAuth.user.userId)) { _ =>
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
          makeOperation("FollowUser") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              provideAsyncOrNotFound(userService.getUser(userId)) { _ =>
                provideAsync(userService.getFollowedUsers(userAuth.user.userId)) { followedUsers =>
                  if (!followedUsers.contains(userId)) {
                    complete(StatusCodes.BadRequest -> Seq(ValidationError("userId", Some("User already unfollowed"))))
                  } else {
                    onSuccess(userService.unfollowUser(followedUserId = userId, userId = userAuth.user.userId)) { _ =>
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
}

case class RegisterUserRequest(
  email: String,
  password: String,
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01") dateOfBirth: Option[LocalDate],
  firstName: Option[String],
  lastName: Option[String],
  profession: Option[String],
  postalCode: Option[String],
  @(ApiModelProperty @field)(dataType = "string") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string") language: Option[Language],
  @(ApiModelProperty @field)(dataType = "boolean") optIn: Option[Boolean],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "M,F,O") gender: Option[Gender],
  @(ApiModelProperty @field)(dataType = "string", allowableValues = "FARM,AMCD,MHIO,INPR,EMPL,WORK,HSTU,STUD,APRE,O") socioProfessionalCategory: Option[
    SocioProfessionalCategory
  ],
  @(ApiModelProperty @field)(dataType = "string", example = "e4805533-7b46-41b6-8ef6-58caabb2e4e5") questionId: Option[
    QuestionId
  ],
  @(ApiModelProperty @field)(dataType = "boolean") optInPartner: Option[Boolean]
) {

  validate(
    mandatoryField("firstName", firstName),
    validateOptionalUserInput("firstName", firstName, None),
    mandatoryField("email", email),
    validateEmail("email", email.toLowerCase),
    validateUserInput("email", email, None),
    mandatoryField("password", password),
    validateField("password", Option(password).exists(_.length >= 8), "Password must be at least 8 characters"),
    validateOptionalUserInput("lastName", lastName, None),
    validateOptionalUserInput("profession", profession, None),
    validateField("postalCode", postalCode.forall(_.length <= 10), "postal code cannot be longer than 10 characters"),
    validateOptionalUserInput("postalCode", postalCode, None),
    mandatoryField("language", language),
    mandatoryField("country", country),
    validateAge("dateOfBirth", dateOfBirth)
  )
}

object RegisterUserRequest extends CirceFormatters {
  implicit val decoder: Decoder[RegisterUserRequest] = deriveDecoder[RegisterUserRequest]
}

case class UpdateUserRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "1970-01-01") dateOfBirth: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  organisationName: Option[String],
  profession: Option[String],
  postalCode: Option[String],
  phoneNumber: Option[String],
  description: Option[String],
  @(ApiModelProperty @field)(dataType = "boolean") optInNewsletter: Option[Boolean],
  gender: Option[String],
  genderName: Option[String],
  @(ApiModelProperty @field)(dataType = "string") country: Option[Country],
  @(ApiModelProperty @field)(dataType = "string") language: Option[Language],
  socioProfessionalCategory: Option[String]
) {
  private val maxLanguageLength = 3
  private val maxCountryLength = 3
  private val maxPostalCodeLength = 10
  private val maxDescriptionLength = 450

  validate(
    firstName.map(value => requireNonEmpty("firstName", value, Some("firstName should not be an empty string"))),
    Some(validateOptionalUserInput("firstName", firstName, None)),
    organisationName.map(
      value => requireNonEmpty("organisationName", value, Some("organisationName should not be an empty string"))
    ),
    Some(validateOptionalUserInput("organisationName", organisationName, None)),
    postalCode.map(
      value =>
        maxLength(
          "postalCode",
          maxPostalCodeLength,
          value,
          Some(_ => "postal code cannot be longer than 10 characters")
      )
    ),
    language.map(lang   => maxLength("language", maxLanguageLength, lang.value)),
    country.map(country => maxLength("country", maxCountryLength, country.value)),
    gender.map(
      value =>
        validateField(
          "gender",
          value == "" || Gender.matchGender(value).isDefined,
          s"gender should be on of this specified values: ${Gender.genders.keys.mkString(",")}"
      )
    ),
    socioProfessionalCategory.map(
      value =>
        validateField(
          "socio professional category",
          value == "" || SocioProfessionalCategory.matchSocioProfessionalCategory(value).isDefined,
          s"CSP should be on of this specified values: ${SocioProfessionalCategory.socioProfessionalCategories.keys.mkString(",")}"
      )
    ),
    description.map(value => maxLength("description", maxDescriptionLength, value)),
    Some(validateOptionalUserInput("phoneNumber", phoneNumber, None)),
    Some(validateOptionalUserInput("description", description, None)),
    dateOfBirth match {
      case Some("") => None
      case None     => None
      case Some(date) =>
        val localDate = Try(LocalDate.parse(date)) match {
          case Success(parsedDate) => Some(parsedDate)
          case _                   => None
        }
        Some(validateAge("dateOfBirth", localDate))
    }
  )
}

object UpdateUserRequest extends CirceFormatters {
  implicit val decoder: Decoder[UpdateUserRequest] = deriveDecoder[UpdateUserRequest]

  def updateValue(oldValue: Option[String], newValue: Option[String]): Option[String] = {
    newValue match {
      case None     => oldValue
      case Some("") => None
      case value    => value
    }
  }
}

case class SocialLoginRequest(provider: String,
                              token: String,
                              @(ApiModelProperty @field)(dataType = "string") country: Option[Country],
                              @(ApiModelProperty @field)(dataType = "string") language: Option[Language]) {
  validate(mandatoryField("language", language), mandatoryField("country", country))
}

object SocialLoginRequest {
  implicit val decoder: Decoder[SocialLoginRequest] = deriveDecoder[SocialLoginRequest]
}

final case class ResetPasswordRequest(email: String) {
  validate(
    mandatoryField("email", email),
    validateEmail("email", email.toLowerCase),
    validateUserInput("email", email, None)
  )
}

object ResetPasswordRequest {
  implicit val decoder: Decoder[ResetPasswordRequest] = deriveDecoder[ResetPasswordRequest]
}

final case class ResetPassword(resetToken: String, password: String) {
  validate(mandatoryField("resetToken", resetToken))
  validate(
    mandatoryField("password", password),
    validateField("password", Option(password).exists(_.length >= 8), "Password must be at least 8 characters")
  )
}

object ResetPassword {
  implicit val decoder: Decoder[ResetPassword] = deriveDecoder[ResetPassword]
}

final case class ChangePasswordRequest(actualPassword: Option[String], newPassword: String) {
  validate(
    mandatoryField("newPassword", newPassword),
    validateField("newPassword", Option(newPassword).exists(_.length >= 8), "Password must be at least 8 characters")
  )
}

object ChangePasswordRequest {
  implicit val decoder: Decoder[ChangePasswordRequest] = deriveDecoder[ChangePasswordRequest]
}

final case class DeleteUserRequest(password: Option[String])

object DeleteUserRequest {
  implicit val decoder: Decoder[DeleteUserRequest] = deriveDecoder[DeleteUserRequest]
}

final case class SubscribeToNewsLetter(email: String) {
  validate(
    mandatoryField("email", email),
    validateEmail("email", email.toLowerCase),
    validateUserInput("email", email, None)
  )
}

object SubscribeToNewsLetter {
  implicit val decoder: Decoder[SubscribeToNewsLetter] = deriveDecoder[SubscribeToNewsLetter]
}

case class MailingErrorLogResponse(error: String,
                                   @(ApiModelProperty @field)(
                                     dataType = "string",
                                     example = "2019-01-21T16:33:21.523+01:00[Europe/Paris]"
                                   ) date: ZonedDateTime)
object MailingErrorLogResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[MailingErrorLogResponse] = deriveEncoder[MailingErrorLogResponse]
  implicit val decoder: Decoder[MailingErrorLogResponse] = deriveDecoder[MailingErrorLogResponse]

  def apply(mailingErrorLog: MailingErrorLog): MailingErrorLogResponse =
    MailingErrorLogResponse(error = mailingErrorLog.error, date = mailingErrorLog.date)
}

case class UserResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "9bccc3ce-f5b9-47c0-b907-01a9cb159e55") userId: UserId,
  email: String,
  firstName: Option[String],
  lastName: Option[String],
  organisationName: Option[String],
  enabled: Boolean,
  emailVerified: Boolean,
  isOrganisation: Boolean,
  @(ApiModelProperty @field)(dataType = "string", example = "2019-01-21T16:33:21.523+01:00[Europe/Paris]")
  lastConnection: ZonedDateTime,
  @(ApiModelProperty @field)(dataType = "list[string]", allowableValues = "ROLE_CITIZEN,ROLE_MODERATOR,ROLE_ADMIN")
  roles: Seq[Role],
  profile: Option[Profile],
  @(ApiModelProperty @field)(dataType = "string") country: Country,
  @(ApiModelProperty @field)(dataType = "string") language: Language,
  isHardBounce: Boolean,
  @(ApiModelProperty @field)(dataType = "org.make.api.user.MailingErrorLogResponse")
  lastMailingError: Option[MailingErrorLogResponse],
  hasPassword: Boolean,
  @(ApiModelProperty @field)(dataType = "list[string]") followedUsers: Seq[UserId] = Seq.empty
)

object UserResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[UserResponse] = deriveEncoder[UserResponse]
  implicit val decoder: Decoder[UserResponse] = deriveDecoder[UserResponse]

  def apply(user: User): UserResponse = UserResponse(user, Seq.empty)

  def apply(user: User, followedUsers: Seq[UserId]): UserResponse = UserResponse(
    userId = user.userId,
    email = user.email,
    firstName = user.firstName,
    lastName = user.lastName,
    organisationName = user.organisationName,
    enabled = user.enabled,
    emailVerified = user.emailVerified,
    isOrganisation = user.isOrganisation,
    lastConnection = user.lastConnection,
    roles = user.roles,
    profile = user.profile,
    country = user.country,
    language = user.language,
    isHardBounce = user.isHardBounce,
    lastMailingError = user.lastMailingError.map(MailingErrorLogResponse(_)),
    hasPassword = user.hashedPassword.isDefined,
    followedUsers = followedUsers
  )
}
