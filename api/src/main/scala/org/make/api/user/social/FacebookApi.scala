package org.make.api.user.social

import java.nio.charset.Charset

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import org.make.api.user.social.models.facebook.{UserInfo => FacebookUserInfo}
import io.circe.parser._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait FacebookApiComponent {
  def facebookApi: FacebookApi
}

trait FacebookApi {
  def getUserInfo(accessToken: String): Future[FacebookUserInfo]
}

trait DefaultFacebookApiComponent extends FacebookApiComponent {
  def actorSystem: ActorSystem

  override lazy val facebookApi: FacebookApi = new FacebookApi with StrictLogging {
    private implicit val system = actorSystem
    private implicit val materializer: ActorMaterializer = ActorMaterializer()
    private val http: HttpExt = Http()

    def getUserInfo(accessToken: String) = {
      val url =
        s"https://graph.facebook.com/v2.1/me?access_token=$accessToken&fields=email,first_name,last_name,picture"

      http
        .singleRequest(HttpRequest(method = HttpMethods.GET, uri = url))
        .flatMap(strictToString(_))
        .flatMap { entity =>
          parse(entity).flatMap(_.as[FacebookUserInfo]) match {
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
