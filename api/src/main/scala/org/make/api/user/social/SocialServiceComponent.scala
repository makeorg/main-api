package org.make.api.user.social

import org.make.api.user.social
import org.make.api.user.social.models.UserInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SocialServiceComponent extends GoogleApiComponent with FacebookApiComponent {
  def socialService: SocialService
}

trait SocialService {

  def login(provider: String, idToken: String): Future[UserInfo]
}

trait DefaultSocialServiceComponent extends SocialServiceComponent {

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
    def login(provider: String, token: String): Future[UserInfo] = {

      val userInfo = provider match {
        case GOOGLE_PROVIDER =>
          for {
            googleUserInfo <- googleApi.getUserInfo(token)
          } yield
            UserInfo(
              email = googleUserInfo.email,
              firstName = googleUserInfo.givenName,
              lastName = googleUserInfo.familyName,
              googleId = googleUserInfo.iat,
              facebookId = None
            )
        case FACEBOOK_PROVIDER =>
          for {
            facebookUserInfo <- facebookApi.getUserInfo(token)
          } yield
            UserInfo(
              email = facebookUserInfo.email,
              firstName = facebookUserInfo.firstName,
              lastName = facebookUserInfo.lastName,
              googleId = None,
              facebookId = Some(facebookUserInfo.id)
            )
        case _ => Future.failed(new Exception("Social failed"))
      }

      userInfo
    }
  }
}
