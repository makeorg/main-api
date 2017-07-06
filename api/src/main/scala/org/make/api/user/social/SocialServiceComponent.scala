package org.make.api.user.social

import org.make.api.user.social
import org.make.api.user.social.models.UserInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SocialServiceComponent {
  def socialService: SocialService
}

trait SocialService {
  def login(provider: String, idToken: String): Future[UserInfo]
}

trait DefaultSocialServiceComponent extends SocialServiceComponent { self: GoogleApiComponent =>

  override lazy val socialService: social.SocialService = new SocialService {

    private val GOOGLE_PROVIDER = "google"
    private val FACEBOOK_PROVIDER = "facebook"

    /**
      *
      * @param provider string
      * @param idToken  string
      *
      * @return Future[UserInfo]
      */
    def login(provider: String, idToken: String): Future[UserInfo] = {

      val userInfo = provider match {
        case GOOGLE_PROVIDER =>
          for {
            googleUserInfo <- googleApi.getUserInfo(idToken)
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
            facebookUserInfo <- googleApi.getUserInfo(idToken)
          } yield
            UserInfo(
              email = facebookUserInfo.email,
              firstName = facebookUserInfo.givenName,
              lastName = facebookUserInfo.familyName,
              googleId = None,
              facebookId = facebookUserInfo.iat
            )
        case _ => Future.failed(new Exception("Social failed"))
      }

      userInfo
    }
  }
}
