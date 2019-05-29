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

package org.make.api.user.social

import java.text.SimpleDateFormat

import org.make.api.MakeUnitTest
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth._
import org.make.api.user.social.models.UserInfo
import org.make.api.user.social.models.facebook.{UserInfo => FacebookUserInfos}
import org.make.api.user.social.models.google.{UserInfo   => GoogleUserInfos}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.core.auth.UserRights
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future
import scala.concurrent.duration.DurationDouble

class SocialServiceComponentTest
    extends MakeUnitTest
    with DefaultSocialServiceComponent
    with UserServiceComponent
    with MakeDataHandlerComponent {

  override val userService: UserService = mock[UserService]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val googleApi: GoogleApi = mock[GoogleApi]
  override val facebookApi: FacebookApi = mock[FacebookApi]

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(userService, oauth2DataHandler, googleApi, facebookApi)
  }

  val expireInSeconds = 123000
  var refreshTokenValue = "my_refresh_token"
  var accessTokenValue = "my_access_token"

  feature("login user from google provider") {
    scenario("successful create UserInfo Object") {
      Given("a user logged via google")
      val googleData = GoogleUserInfos(
        azp = None,
        aud = None,
        sub = None,
        hd = Some("make.org"),
        email = Some("google@make.org"),
        emailVerified = "true",
        atHash = None,
        iss = None,
        iat = Some("123456789"),
        exp = None,
        name = Some("google user"),
        picture = "picture_url",
        givenName = Some("google"),
        familyName = Some("user"),
        local = None,
        alg = None,
        kid = None
      )

      val userFromGoogle = User(
        userId = UserId("boo"),
        email = "google@make.org",
        firstName = None,
        lastName = None,
        lastIp = None,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq.empty,
        country = Country("FR"),
        language = Language("fr"),
        profile = None,
        createdAt = None,
        updatedAt = None,
        lastMailingError = None,
        availableQuestions = Seq.empty
      )

      val accessToken = AccessToken(
        accessTokenValue,
        Some(refreshTokenValue),
        None,
        Some(expireInSeconds),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2018-07-13 12:00:00"),
        Map.empty
      )

      Mockito
        .when(googleApi.getUserInfo(matches("googleToken-a user logged via google")))
        .thenReturn(Future.successful(googleData))

      Mockito
        .when(
          userService.createOrUpdateUserFromSocial(
            any[UserInfo],
            any[Option[String]],
            any[Option[QuestionId]],
            any[RequestContext]
          )
        )
        .thenReturn(Future.successful(userFromGoogle))

      Mockito
        .when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      When("login google user")
      val tokenResposnse =
        socialService.login(
          "google",
          "googleToken-a user logged via google",
          Country("FR"),
          Language("fr"),
          None,
          None,
          RequestContext.empty
        )

      whenReady(tokenResposnse, Timeout(3.seconds)) { _ =>
        Then("my program call getOrCreateUserFromSocial with google data")
        val userInfoFromGoogle =
          UserInfo(
            email = googleData.email,
            firstName = googleData.givenName,
            lastName = googleData.familyName,
            country = "FR",
            language = "fr",
            googleId = googleData.iat,
            domain = googleData.hd,
            facebookId = None,
            picture = Some("picture_url")
          )

        verify(userService).createOrUpdateUserFromSocial(
          matches(userInfoFromGoogle),
          matches(None),
          matches(None),
          any[RequestContext]
        )
      }
    }

    scenario("successful create UserInfo Object without name") {
      Given("a user logged via google")
      val googleData = GoogleUserInfos(
        azp = None,
        aud = None,
        sub = None,
        hd = Some("make.org"),
        email = Some("google@make.org"),
        emailVerified = "true",
        atHash = None,
        iss = None,
        iat = Some("123456789"),
        exp = None,
        name = Some("google user"),
        picture = "picture_url",
        givenName = None,
        familyName = None,
        local = None,
        alg = None,
        kid = None
      )

      val userFromGoogle = User(
        userId = UserId("boo"),
        email = "google@make.org",
        firstName = None,
        lastName = None,
        lastIp = None,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq.empty,
        country = Country("FR"),
        language = Language("fr"),
        profile = None,
        createdAt = None,
        updatedAt = None,
        availableQuestions = Seq.empty
      )

      val accessToken = AccessToken(
        accessTokenValue,
        Some(refreshTokenValue),
        None,
        Some(expireInSeconds),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2018-07-13 12:00:00"),
        Map.empty
      )

      Mockito
        .when(googleApi.getUserInfo(matches("googleToken-a user logged via google")))
        .thenReturn(Future.successful(googleData))

      Mockito
        .when(
          userService.createOrUpdateUserFromSocial(
            any[UserInfo],
            any[Option[String]],
            any[Option[QuestionId]],
            any[RequestContext]
          )
        )
        .thenReturn(Future.successful(userFromGoogle))

      Mockito
        .when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      When("login google user")
      val tokenResposnse =
        socialService.login(
          "google",
          "googleToken-a user logged via google",
          Country("FR"),
          Language("fr"),
          None,
          None,
          RequestContext.empty
        )

      whenReady(tokenResposnse, Timeout(3.seconds)) { _ =>
        Then("my program call getOrCreateUserFromSocial with google data")
        val userInfoFromGoogle =
          UserInfo(
            email = googleData.email,
            firstName = googleData.givenName,
            lastName = googleData.familyName,
            country = "FR",
            language = "fr",
            googleId = googleData.iat,
            domain = googleData.hd,
            facebookId = None,
            picture = Some("picture_url")
          )

        verify(userService).createOrUpdateUserFromSocial(
          matches(userInfoFromGoogle),
          matches(None),
          matches(None),
          any[RequestContext]
        )
      }
    }

    scenario("successfully create access token for persistent user") {
      Given("a user logged via google")
      val googleData = GoogleUserInfos(
        azp = None,
        aud = None,
        sub = None,
        hd = None,
        email = Some("google@make.org"),
        emailVerified = "true",
        atHash = None,
        iss = None,
        iat = Some("333333"),
        exp = None,
        name = Some("google user"),
        picture = "picture_url",
        givenName = Some("google"),
        familyName = Some("user"),
        local = None,
        alg = None,
        kid = None
      )

      val userFromGoogle = User(
        userId = UserId("boo"),
        email = "google@make.org",
        firstName = None,
        lastName = None,
        lastIp = None,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq.empty,
        country = Country("FR"),
        language = Language("fr"),
        profile = None,
        createdAt = None,
        updatedAt = None,
        availableQuestions = Seq.empty
      )

      val accessToken = AccessToken(
        accessTokenValue,
        Some(refreshTokenValue),
        None,
        Some(expireInSeconds),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2018-07-13 12:00:00"),
        Map.empty
      )

      Mockito
        .when(googleApi.getUserInfo(any[String]))
        .thenReturn(Future.successful(googleData))

      Mockito
        .when(
          userService.createOrUpdateUserFromSocial(
            any[UserInfo],
            any[Option[String]],
            any[Option[QuestionId]],
            any[RequestContext]
          )
        )
        .thenReturn(Future.successful(userFromGoogle))

      Mockito
        .when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      When("login user from google")
      val futureTokenResposnse =
        socialService.login("google", "token", Country("FR"), Language("fr"), None, None, RequestContext.empty)

      Then("my program should return a token response")
      whenReady(futureTokenResposnse, Timeout(2.seconds)) { tokenResponse =>
        tokenResponse.token shouldBe a[TokenResponse]
        tokenResponse.token.access_token should be(accessTokenValue)
        tokenResponse.token.refresh_token should be(refreshTokenValue)
        tokenResponse.token.token_type should be("Bearer")
      }
    }

    scenario("social user has a bad token") {
      Given("a user logged via google")

      Mockito
        .when(googleApi.getUserInfo(any[String]))
        .thenReturn(Future.failed(new Exception("invalid token from google")))

      When("login user from google")
      val futureTokenResposnse =
        socialService.login("google", "token", Country("FR"), Language("fr"), None, None, RequestContext.empty)

      whenReady(futureTokenResposnse.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[Exception]
        exception.asInstanceOf[Exception].getMessage should be("invalid token from google")
      }
    }
  }

  feature("login user from facebook provider") {
    scenario("successful login social user") {
      Given("a user logged via facebook")
      val facebookData = FacebookUserInfos(
        id = "444444",
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("user")
      )

      val userFromFacebook = User(
        userId = UserId("boo"),
        email = "facebook@make.org",
        firstName = None,
        lastName = None,
        lastIp = None,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq.empty,
        country = Country("FR"),
        language = Language("fr"),
        profile = None,
        createdAt = None,
        updatedAt = None,
        availableQuestions = Seq.empty
      )

      val accessToken = AccessToken(
        accessTokenValue,
        Some(refreshTokenValue),
        None,
        Some(99000),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2018-08-13 12:00:00"),
        Map.empty
      )

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("user"),
        country = "FR",
        language = "fr",
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("https://graph.facebook.com/v3.0/444444/picture")
      )

      Mockito
        .when(facebookApi.getUserInfo(matches("facebookToken-444444")))
        .thenReturn(Future.successful(facebookData))

      Mockito
        .when(
          userService.createOrUpdateUserFromSocial(matches(info), matches(None), matches(None), any[RequestContext])
        )
        .thenReturn(Future.successful(userFromFacebook))

      Mockito
        .when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      When("login facebook user")
      val tokenResponse =
        socialService.login(
          "facebook",
          "facebookToken-444444",
          Country("FR"),
          Language("fr"),
          None,
          None,
          RequestContext.empty
        )

      Then("my program call getOrCreateUserFromSocial with facebook data")

      whenReady(tokenResponse, Timeout(3.seconds)) { _ =>
        val userInfoFromFacebook =
          UserInfo(
            email = facebookData.email,
            firstName = facebookData.firstName,
            lastName = facebookData.lastName,
            country = "FR",
            language = "fr",
            googleId = None,
            facebookId = Some(facebookData.id),
            picture = Some(s"https://graph.facebook.com/v3.0/${facebookData.id}/picture")
          )

        verify(userService).createOrUpdateUserFromSocial(
          matches(userInfoFromFacebook),
          matches(None),
          matches(None),
          any[RequestContext]
        )
      }

    }

    scenario("successful login social user without name") {
      Given("a user logged via facebook")
      val facebookData =
        FacebookUserInfos(id = "444444", email = Some("facebook@make.org"), firstName = None, lastName = None)

      val userFromFacebook = User(
        userId = UserId("boo"),
        email = "facebook@make.org",
        firstName = None,
        lastName = None,
        lastIp = None,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq.empty,
        country = Country("FR"),
        language = Language("fr"),
        profile = None,
        createdAt = None,
        updatedAt = None,
        availableQuestions = Seq.empty
      )

      val accessToken = AccessToken(
        accessTokenValue,
        Some(refreshTokenValue),
        None,
        Some(99000),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2018-08-13 12:00:00"),
        Map.empty
      )

      val info = UserInfo(
        email = Some("facebook@make.org"),
        firstName = None,
        lastName = None,
        country = "FR",
        language = "fr",
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("https://graph.facebook.com/v3.0/444444/picture")
      )

      Mockito
        .when(facebookApi.getUserInfo(matches("facebookToken-444444")))
        .thenReturn(Future.successful(facebookData))

      Mockito
        .when(
          userService
            .createOrUpdateUserFromSocial(matches(info), matches(None), any[Option[QuestionId]], any[RequestContext])
        )
        .thenReturn(Future.successful(userFromFacebook))

      Mockito
        .when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      When("login facebook user")
      val tokenResponse =
        socialService.login(
          "facebook",
          "facebookToken-444444",
          Country("FR"),
          Language("fr"),
          None,
          None,
          RequestContext.empty
        )

      Then("my program call getOrCreateUserFromSocial with facebook data")

      whenReady(tokenResponse, Timeout(3.seconds)) { _ =>
        val userInfoFromFacebook =
          UserInfo(
            email = facebookData.email,
            firstName = facebookData.firstName,
            lastName = facebookData.lastName,
            country = "FR",
            language = "fr",
            googleId = None,
            facebookId = Some(facebookData.id),
            picture = Some(s"https://graph.facebook.com/v3.0/${facebookData.id}/picture")
          )

        verify(userService).createOrUpdateUserFromSocial(
          matches(userInfoFromFacebook),
          matches(None),
          matches(None),
          any[RequestContext]
        )
      }

    }

    scenario("successfully create access token for persistent user") {
      Given("a user logged via facebook")
      val facebookData = FacebookUserInfos(
        id = "444444",
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("USER")
      )

      val userFromFacebook = User(
        userId = UserId("boo"),
        email = "facebook@make.org",
        firstName = None,
        lastName = None,
        lastIp = None,
        hashedPassword = None,
        enabled = true,
        emailVerified = true,
        lastConnection = DateHelper.now(),
        verificationToken = None,
        verificationTokenExpiresAt = None,
        resetToken = None,
        resetTokenExpiresAt = None,
        roles = Seq.empty,
        country = Country("FR"),
        language = Language("fr"),
        profile = None,
        createdAt = None,
        updatedAt = None,
        availableQuestions = Seq.empty
      )

      val accessToken = AccessToken(
        accessTokenValue,
        Some(refreshTokenValue),
        None,
        Some(99000),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2018-08-13 12:00:00"),
        Map.empty
      )

      Mockito
        .when(facebookApi.getUserInfo(matches("facebookToken2")))
        .thenReturn(Future.successful(facebookData))

      Mockito
        .when(
          userService.createOrUpdateUserFromSocial(
            any[UserInfo],
            any[Option[String]],
            any[Option[QuestionId]],
            any[RequestContext]
          )
        )
        .thenReturn(Future.successful(userFromFacebook))

      Mockito
        .when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      When("login user from facebook")
      val futureTokenResposnse =
        socialService.login(
          "facebook",
          "facebookToken2",
          Country("FR"),
          Language("fr"),
          None,
          None,
          RequestContext.empty
        )

      Then("my program should return a token response")
      whenReady(futureTokenResposnse, Timeout(2.seconds)) { tokenResponse =>
        tokenResponse.token shouldBe a[TokenResponse]
        tokenResponse.token.access_token should be(accessTokenValue)
        tokenResponse.token.refresh_token should be(refreshTokenValue)
        tokenResponse.token.token_type should be("Bearer")
      }
    }

    scenario("social user has a bad token") {
      Given("a user logged via facebook")

      Mockito
        .when(facebookApi.getUserInfo(matches("facebookToken3")))
        .thenReturn(Future.failed(new Exception("invalid token from facebook")))

      When("login user from google")
      val futureTokenResposnse =
        socialService.login(
          "facebook",
          "facebookToken3",
          Country("FR"),
          Language("fr"),
          None,
          None,
          RequestContext.empty
        )

      whenReady(futureTokenResposnse.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[Exception]
        exception.asInstanceOf[Exception].getMessage should be("invalid token from facebook")
      }
    }
  }

  feature("login user from unknown provider") {
    scenario("failed login from unkown provider") {
      Given("a user logged via instagram")

      When("login user from instagram")
      val futureTokenResposnse =
        socialService.login("instagram", "token", Country("FR"), Language("fr"), None, None, RequestContext.empty)

      whenReady(futureTokenResposnse.failed, Timeout(3.seconds)) { exception =>
        exception shouldBe a[Exception]
        exception.asInstanceOf[Exception].getMessage should be("Social login failed: undefined provider instagram")
      }
    }
  }

}
