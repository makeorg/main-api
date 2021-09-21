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

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers.stringUnmarshaller
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.http.scaladsl.{Http, HttpExt}
import grizzled.slf4j.Logging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import org.make.api.technical.ActorSystemComponent
import org.make.api.user.social.models.google.PeopleInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait GoogleApiComponent {
  def googleApi: GoogleApi
}

trait GoogleApi {
  def peopleInfo(userToken: String): Future[PeopleInfo]
}

trait DefaultGoogleApiComponent extends GoogleApiComponent with ErrorAccumulatingCirceSupport {
  self: ActorSystemComponent with SocialProvidersConfigurationComponent =>

  override lazy val googleApi: GoogleApi = new DefaultGoogleApi

  class DefaultGoogleApi extends GoogleApi with Logging {
    private val http: HttpExt = Http()

    override def peopleInfo(userToken: String): Future[PeopleInfo] = {
      val apiKey = socialProvidersConfiguration.google.apiKey
      val url =
        s"https://people.googleapis.com/v1/people/me?key=$apiKey&personFields=metadata,names,birthdays,photos,emailAddresses"

      http
        .singleRequest(
          HttpRequest(method = HttpMethods.GET, uri = url, headers = Seq(Authorization(OAuth2BearerToken(userToken))))
        )
        .flatMap(unmarshal[PeopleInfo](_))
    }

    private def unmarshal[Entity](response: HttpResponse, expectedCode: StatusCode = StatusCodes.OK)(
      implicit unmarshaller: Unmarshaller[ResponseEntity, Entity]
    ): Future[Entity] = {
      logger.debug(s"Server answered $response")
      response match {
        case HttpResponse(`expectedCode`, _, entity, _) =>
          Unmarshal(entity).to[Entity]
        case HttpResponse(code, _, entity, _) =>
          Unmarshal(entity).to[String].flatMap { error =>
            Future.failed(SocialProviderException(s"Got unexpected response code: $code, with body: $error"))
          }
      }
    }
  }
}
