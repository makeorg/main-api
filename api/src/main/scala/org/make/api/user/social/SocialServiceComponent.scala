package org.make.api.user.social

import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.user.social.models.UserInfo
import org.make.api.user.{social, UserServiceComponent}
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalaoauth2.provider.AuthInfo

trait SocialServiceComponent extends GoogleApiComponent with FacebookApiComponent {
  def socialService: SocialService
}

trait SocialService {

  def login(provider: String,
            token: String,
            clientIp: Option[String],
            requestContext: RequestContext): Future[UserIdAndToken]
}

case class UserIdAndToken(userId: UserId, token: TokenResponse)

trait DefaultSocialServiceComponent extends SocialServiceComponent {
  self: UserServiceComponent with MakeDataHandlerComponent =>

  override lazy val socialService: social.SocialService = new SocialService {

    private val GOOGLE_PROVIDER = "google"
    private val FACEBOOK_PROVIDER = "facebook"

    /**
      *
      * @param provider string
      * @param token  string
      *
      * @return Future[UserInfo]
      */
    def login(provider: String,
              token: String,
              clientIp: Option[String],
              requestContext: RequestContext): Future[UserIdAndToken] = {

      val futureUserInfo: Future[UserInfo] = provider match {
        case GOOGLE_PROVIDER =>
          for {
            googleUserInfo <- googleApi.getUserInfo(token)
          } yield
            UserInfo(
              email = googleUserInfo.email,
              firstName = googleUserInfo.givenName,
              lastName = googleUserInfo.familyName,
              googleId = googleUserInfo.iat,
              picture = Option(googleUserInfo.picture),
              domain = googleUserInfo.hd
            )
        case FACEBOOK_PROVIDER =>
          for {
            facebookUserInfo <- facebookApi.getUserInfo(token)
          } yield
            UserInfo(
              email = facebookUserInfo.email,
              firstName = facebookUserInfo.firstName,
              lastName = facebookUserInfo.lastName,
              gender = facebookUserInfo.gender,
              facebookId = Some(facebookUserInfo.id),
              picture = Option(facebookUserInfo.picture.data.url)
            )
        case _ => Future.failed(new Exception(s"Social login failed: undefined provider $provider"))
      }

      for {
        userInfo <- futureUserInfo
        user     <- userService.getOrCreateUserFromSocial(userInfo, clientIp, requestContext)
        accessToken <- oauth2DataHandler.createAccessToken(
          authInfo =
            AuthInfo(user = UserRights(user.userId, user.roles), clientId = None, scope = None, redirectUri = None)
        )
      } yield {
        UserIdAndToken(
          userId = user.userId,
          token = TokenResponse(
            "Bearer",
            accessToken.token,
            accessToken.expiresIn.getOrElse(1L),
            accessToken.refreshToken.getOrElse("")
          )
        )
      }
    }

  }
}
