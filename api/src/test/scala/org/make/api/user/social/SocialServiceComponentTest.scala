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

import java.net.URL
import java.text.SimpleDateFormat
import java.time.{LocalDate, ZonedDateTime}
import java.util.Date

import org.make.api.technical.auth._
import org.make.api.user.SocialProvider.{Facebook, GooglePeople}
import org.make.api.user.social.models.UserInfo
import org.make.api.user.social.models.facebook.{UserInfo => FacebookUserInfos}
import org.make.api.user.social.models.google._
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{MakeUnitTest, TestUtils}
import org.make.core.RequestContext
import org.make.core.auth.{ClientId, UserRights}
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.user.UserId
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
    reset(userService)
    reset(oauth2DataHandler)
    reset(googleApi)
    reset(facebookApi)
  }

  val expireInSeconds = 123000
  var refreshTokenValue = "my_refresh_token"
  var accessTokenValue = "my_access_token"

  Feature("login user from google_people provider") {
    Scenario("user with a birth date") {
      val token = "user with a birth date"
      val googleId = "123456789"
      val email = "user-with-a-birth-date@example.com"
      val userId1 = UserId("user-with-a-birth-date")

      when(googleApi.peopleInfo(token)).thenReturn(
        Future.successful(
          PeopleInfo(
            resourceName = s"people/$googleId",
            etag = "",
            names = Seq(
              PeopleName(
                metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                displayName = "user",
                familyName = Some("with"),
                givenName = "birth",
                displayNameLastFirst = "date",
                unstructuredName = "user with a birth date"
              )
            ),
            photos = Seq(
              PeoplePhoto(
                metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                url = new URL("https://example.com/avatar")
              )
            ),
            emailAddresses =
              Seq(PeopleEmailAddress(metadata = ItemMetadata(Some(true), None, MetadataSource("", "")), value = email)),
            birthdays = Some(
              Seq(
                Birthday(
                  metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                  None,
                  GoogleDate(Some(1970), 1, 1)
                )
              )
            )
          )
        )
      )

      when(
        userService.createOrUpdateUserFromSocial(
          argThat[UserInfo](_.email.contains(email)),
          eqTo(None),
          eqTo(Country("FR")),
          eqTo(RequestContext.empty),
          any[Option[ZonedDateTime]]
        )
      ).thenReturn(Future.successful((user(id = userId1, email = email), false)))

      when(oauth2DataHandler.createAccessToken(argThat[AuthInfo[UserRights]](_.user.userId == userId1)))
        .thenReturn(Future.successful(AccessToken("token", None, None, None, new Date())))

      whenReady(
        socialService.login(GooglePeople, token, Country("FR"), None, RequestContext.empty, ClientId("default"), None),
        Timeout(3.seconds)
      ) {
        case (userId, response) =>
          userId should be(userId1)
          response.accessToken should be("token")
          verify(userService).createOrUpdateUserFromSocial(argThat[UserInfo] { userInfo =>
            userInfo.dateOfBirth.contains(LocalDate.parse("1970-01-01")) &&
            userInfo.email.contains(email) &&
            userInfo.domain.contains("example.com") &&
            userInfo.googleId.contains(googleId) &&
            userInfo.picture.contains("https://example.com/avatar")
          }, eqTo(None), eqTo(Country("FR")), eqTo(RequestContext.empty), eqTo(None))
      }

    }

    Scenario("user without a birth date year") {
      val token = "user without a birth date year"
      val googleId = "123456789"
      val email = "user-without-a-birth-date-year@example.com"
      val userId1 = UserId("user-without-a-birth-date-year")

      when(googleApi.peopleInfo(token)).thenReturn(
        Future.successful(
          PeopleInfo(
            resourceName = s"people/$googleId",
            etag = "",
            names = Seq(
              PeopleName(
                metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                displayName = "user",
                familyName = Some("without"),
                givenName = "birth",
                displayNameLastFirst = "date year",
                unstructuredName = "user without a birth date year"
              )
            ),
            photos = Seq(
              PeoplePhoto(
                metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                url = new URL("https://example.com/avatar")
              )
            ),
            emailAddresses =
              Seq(PeopleEmailAddress(metadata = ItemMetadata(Some(true), None, MetadataSource("", "")), value = email)),
            birthdays = Some(
              Seq(
                Birthday(
                  metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                  None,
                  GoogleDate(None, 1, 1)
                )
              )
            )
          )
        )
      )

      when(
        userService.createOrUpdateUserFromSocial(
          argThat[UserInfo](_.email.contains(email)),
          eqTo(None),
          eqTo(Country("FR")),
          eqTo(RequestContext.empty),
          any[Option[ZonedDateTime]]
        )
      ).thenReturn(Future.successful((user(id = userId1, email = email), false)))

      when(oauth2DataHandler.createAccessToken(argThat[AuthInfo[UserRights]](_.user.userId == userId1)))
        .thenReturn(Future.successful(AccessToken("token", None, None, None, new Date())))

      whenReady(
        socialService.login(GooglePeople, token, Country("FR"), None, RequestContext.empty, ClientId("default"), None),
        Timeout(3.seconds)
      ) {
        case (userId, response) =>
          userId should be(userId1)
          response.accessToken should be("token")
          verify(userService).createOrUpdateUserFromSocial(argThat[UserInfo] { userInfo =>
            userInfo.dateOfBirth.isEmpty &&
            userInfo.email.contains(email) &&
            userInfo.domain.contains("example.com") &&
            userInfo.googleId.contains(googleId) &&
            userInfo.picture.contains("https://example.com/avatar")
          }, eqTo(None), eqTo(Country("FR")), eqTo(RequestContext.empty), eqTo(None))
      }

    }

    Scenario("user with multiple birth dates") {
      val token = "user with multiple birth dates"
      val googleId = "123456789"
      val email = "user-with-multiple-birth-dates@example.com"
      val userId1 = UserId("user-with-multiple-birth-dates")

      when(googleApi.peopleInfo(token)).thenReturn(
        Future.successful(
          PeopleInfo(
            resourceName = s"people/$googleId",
            etag = "",
            names = Seq(
              PeopleName(
                metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                displayName = "user",
                familyName = Some("with multiple"),
                givenName = "birth",
                displayNameLastFirst = "dates",
                unstructuredName = "user with a birth date"
              )
            ),
            photos = Seq(
              PeoplePhoto(
                metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                url = new URL("https://example.com/avatar")
              )
            ),
            emailAddresses =
              Seq(PeopleEmailAddress(metadata = ItemMetadata(Some(true), None, MetadataSource("", "")), value = email)),
            birthdays = Some(
              Seq(
                Birthday(
                  metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                  None,
                  GoogleDate(None, 1, 1)
                ),
                Birthday(
                  metadata = ItemMetadata(None, None, MetadataSource("", "")),
                  None,
                  GoogleDate(Some(1970), 1, 2)
                ),
                Birthday(
                  metadata = ItemMetadata(Some(true), None, MetadataSource("", "")),
                  None,
                  GoogleDate(Some(1970), 1, 3)
                )
              )
            )
          )
        )
      )

      when(
        userService.createOrUpdateUserFromSocial(
          argThat[UserInfo](_.email.contains(email)),
          eqTo(None),
          eqTo(Country("FR")),
          eqTo(RequestContext.empty),
          any[Option[ZonedDateTime]]
        )
      ).thenReturn(Future.successful((user(id = userId1, email = email), false)))

      when(oauth2DataHandler.createAccessToken(argThat[AuthInfo[UserRights]](_.user.userId == userId1)))
        .thenReturn(Future.successful(AccessToken("token", None, None, None, new Date())))

      whenReady(
        socialService.login(GooglePeople, token, Country("FR"), None, RequestContext.empty, ClientId("default"), None),
        Timeout(3.seconds)
      ) {
        case (userId, response) =>
          userId should be(userId1)
          response.accessToken should be("token")
          verify(userService).createOrUpdateUserFromSocial(argThat[UserInfo] { userInfo =>
            userInfo.dateOfBirth.contains(LocalDate.parse("1970-01-03")) &&
            userInfo.email.contains(email) &&
            userInfo.domain.contains("example.com") &&
            userInfo.googleId.contains(googleId) &&
            userInfo.picture.contains("https://example.com/avatar")
          }, eqTo(None), eqTo(Country("FR")), eqTo(RequestContext.empty), eqTo(None))
      }

    }

  }

  Feature("login user from facebook provider") {
    Scenario("successful login social user") {
      Given("a user logged via facebook")
      val facebookData = FacebookUserInfos(
        id = "444444",
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("user")
      )

      val userFromFacebook =
        TestUtils.user(id = UserId("boo"), email = "facebook@make.org", firstName = None, lastName = None)

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
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("https://graph.facebook.com/v7.0/444444/picture?width=512&height=512"),
        dateOfBirth = None
      )

      when(facebookApi.getUserInfo(eqTo("facebookToken-444444")))
        .thenReturn(Future.successful(facebookData))

      when(
        userService.createOrUpdateUserFromSocial(
          eqTo(info),
          eqTo(None),
          eqTo(Country("FR")),
          any[RequestContext],
          any[Option[ZonedDateTime]]
        )
      ).thenReturn(Future.successful((userFromFacebook, true)))

      when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      When("login facebook user")
      val tokenResponse =
        socialService.login(
          Facebook,
          "facebookToken-444444",
          Country("FR"),
          None,
          RequestContext.empty,
          ClientId("client"),
          None
        )

      Then("my program call getOrCreateUserFromSocial with facebook data")

      whenReady(tokenResponse, Timeout(3.seconds)) { _ =>
        val userInfoFromFacebook =
          UserInfo(
            email = facebookData.email,
            firstName = facebookData.firstName,
            googleId = None,
            facebookId = Some(facebookData.id),
            picture = Some(s"https://graph.facebook.com/v7.0/${facebookData.id}/picture?width=512&height=512"),
            dateOfBirth = None
          )

        verify(userService).createOrUpdateUserFromSocial(
          eqTo(userInfoFromFacebook),
          eqTo(None),
          eqTo(Country("FR")),
          any[RequestContext],
          eqTo(None)
        )
      }

    }

    Scenario("successful login social user without name") {
      Given("a user logged via facebook")
      val facebookData =
        FacebookUserInfos(id = "444444", email = Some("facebook@make.org"), firstName = None, lastName = None)

      val userFromFacebook =
        TestUtils.user(id = UserId("boo"), email = "facebook@make.org", firstName = None, lastName = None)

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
        googleId = None,
        facebookId = Some("444444"),
        picture = Some("https://graph.facebook.com/v7.0/444444/picture?width=512&height=512"),
        dateOfBirth = None
      )

      when(facebookApi.getUserInfo(eqTo("facebookToken-444444")))
        .thenReturn(Future.successful(facebookData))

      when(
        userService
          .createOrUpdateUserFromSocial(
            eqTo(info),
            any[Option[QuestionId]],
            eqTo(Country("FR")),
            any[RequestContext],
            any[Option[ZonedDateTime]]
          )
      ).thenReturn(Future.successful((userFromFacebook, true)))

      when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      When("login facebook user")
      val tokenResponse =
        socialService.login(
          Facebook,
          "facebookToken-444444",
          Country("FR"),
          None,
          RequestContext.empty,
          ClientId("client"),
          None
        )

      Then("my program call getOrCreateUserFromSocial with facebook data")

      whenReady(tokenResponse, Timeout(3.seconds)) { _ =>
        val userInfoFromFacebook =
          UserInfo(
            email = facebookData.email,
            firstName = facebookData.firstName,
            googleId = None,
            facebookId = Some(facebookData.id),
            picture = Some(s"https://graph.facebook.com/v7.0/${facebookData.id}/picture?width=512&height=512"),
            dateOfBirth = None
          )

        verify(userService).createOrUpdateUserFromSocial(
          eqTo(userInfoFromFacebook),
          eqTo(None),
          eqTo(Country("FR")),
          any[RequestContext],
          eqTo(None)
        )
      }

    }

    Scenario("successfully create access token for persistent user") {
      Given("a user logged via facebook")
      val facebookData = FacebookUserInfos(
        id = "444444",
        email = Some("facebook@make.org"),
        firstName = Some("facebook"),
        lastName = Some("USER")
      )

      val userId = UserId("boo")
      val userFromFacebook =
        TestUtils.user(id = userId, email = "facebook@make.org", firstName = None, lastName = None)

      val accessToken = AccessToken(
        accessTokenValue,
        Some(refreshTokenValue),
        None,
        Some(99000),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2018-08-13 12:00:00"),
        Map.empty
      )

      when(facebookApi.getUserInfo(eqTo("facebookToken2")))
        .thenReturn(Future.successful(facebookData))

      when(
        userService
          .createOrUpdateUserFromSocial(
            any[UserInfo],
            any[Option[QuestionId]],
            any[Country],
            any[RequestContext],
            any[Option[ZonedDateTime]]
          )
      ).thenReturn(Future.successful((userFromFacebook, false)))

      when(oauth2DataHandler.createAccessToken(any[AuthInfo[UserRights]]))
        .thenReturn(Future.successful(accessToken))

      When("login user from facebook")
      val futureTokenResposnse =
        socialService.login(
          Facebook,
          "facebookToken2",
          Country("FR"),
          None,
          RequestContext.empty,
          ClientId("fake-client"),
          None
        )

      Then("my program should return a token response")
      whenReady(futureTokenResposnse, Timeout(2.seconds)) {
        case (id, socialLoginResponse) =>
          id should be(userId)
          socialLoginResponse.accessToken should be(accessTokenValue)
          socialLoginResponse.refreshToken should contain(refreshTokenValue)
          socialLoginResponse.tokenType should be("Bearer")
          socialLoginResponse.accountCreation should be(false)
      }
    }

    Scenario("social user has a bad token") {
      Given("a user logged via facebook")

      when(facebookApi.getUserInfo(eqTo("facebookToken3")))
        .thenReturn(Future.failed(new Exception("invalid token from facebook")))

      When("login user from google")
      val futureTokenResposnse =
        socialService.login(
          Facebook,
          "facebookToken3",
          Country("FR"),
          None,
          RequestContext.empty,
          ClientId("client"),
          None
        )

      whenReady(futureTokenResposnse.failed, Timeout(3.seconds)) { exception =>
        exception.getMessage should be("invalid token from facebook")
      }
    }
  }
}
