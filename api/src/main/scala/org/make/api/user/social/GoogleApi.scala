package org.make.api.user.social

import java.nio.charset.Charset

import akka.http.scaladsl.model._
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser._
import org.make.api.ActorSystemComponent
import org.make.api.user.social.models.google.{UserInfo => GoogleUserInfo}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

trait GoogleApiComponent {
  def googleApi: GoogleApi
}

trait GoogleApi {
  def getUserInfo(idToken: String): Future[GoogleUserInfo]
}

trait DefaultGoogleApiComponent extends GoogleApiComponent {
  self: ActorSystemComponent =>

  override lazy val googleApi: GoogleApi = new GoogleApi with StrictLogging {
    private implicit val system = actorSystem
    private implicit val materializer: ActorMaterializer = ActorMaterializer()
    private val http: HttpExt = Http()

    def getUserInfo(idToken: String): Future[GoogleUserInfo] = {
      val url =
        s"https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=$idToken"

      http
        .singleRequest(HttpRequest(method = HttpMethods.GET, uri = url))
        .flatMap(strictToString(_))
        .flatMap { entity =>
          parse(entity).flatMap(_.as[GoogleUserInfo]) match {
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
