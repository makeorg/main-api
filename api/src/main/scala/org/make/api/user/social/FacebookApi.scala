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

import java.nio.charset.Charset

import akka.http.scaladsl.model._
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser._
import org.make.api.ActorSystemComponent
import org.make.api.user.social.models.facebook.{UserInfo => FacebookUserInfo}
import org.make.core.{ValidationError, ValidationFailedError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait FacebookApiComponent {
  def facebookApi: FacebookApi
}

trait FacebookApi {
  def getUserInfo(accessToken: String): Future[FacebookUserInfo]
}

trait DefaultFacebookApiComponent extends FacebookApiComponent {
  self: ActorSystemComponent =>

  override lazy val facebookApi: FacebookApi = new FacebookApi with StrictLogging {
    private implicit val system = actorSystem
    private implicit val materializer: ActorMaterializer = ActorMaterializer()
    private val http: HttpExt = Http()

    def getUserInfo(accessToken: String): Future[FacebookUserInfo] = {
      val url =
        s"https://graph.facebook.com/v3.0/me?access_token=$accessToken&fields=email,first_name,last_name"

      http
        .singleRequest(HttpRequest(method = HttpMethods.GET, uri = url))
        .flatMap(strictToString(_))
        .flatMap { entity =>
          parse(entity).flatMap(_.as[FacebookUserInfo]) match {
            case Right(userInfo) if userInfo.email.isEmpty =>
              Future.failed(ValidationFailedError(Seq(ValidationError("email", "mandatory", Some("not valid")))))
            case Right(userInfo) => Future.successful(userInfo)
            case Left(e)         => Future.failed(e)
          }
        }
    }

    private def strictToString(response: HttpResponse, expectedCode: StatusCode = StatusCodes.OK): Future[String] = {
      logger.debug(s"Server answered $response")
      response match {
        case HttpResponse(`expectedCode`, _, entity, _) =>
          val result = entity
            .toStrict(2.second)
            .map(_.data.decodeString(Charset.forName("UTF-8")))
          result
        case HttpResponse(code, _, entity, _) =>
          entity.toStrict(2.second).flatMap { entity =>
            val response = entity.data.decodeString(Charset.forName("UTF-8"))
            Future.failed(new IllegalStateException(s"Got unexpected response code: $code, with body: $response"))
          }
      }
    }

  }
}
