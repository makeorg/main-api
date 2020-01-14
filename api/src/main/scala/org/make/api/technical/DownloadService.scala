/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.Executors

import akka.http.javadsl.model.headers.Accept
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO

import scala.concurrent.duration.DurationInt
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait DownloadServiceComponent {
  def downloadService: DownloadService
}

trait DownloadService {
  def downloadImage(imageUrl: String, destFn: ContentType => File): Future[(ContentType, File)]
}

trait DefaultDownloadServiceComponent extends DownloadServiceComponent with StrictLogging {
  this: ActorSystemComponent =>

  override lazy val downloadService: DownloadService = new DefaultDownloadService

  class DefaultDownloadService extends DownloadService {
    implicit lazy val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

    implicit val ec: ExecutionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))
    override def downloadImage(imageUrl: String, destFn: ContentType => File): Future[(ContentType, File)] = {
      Try(Uri(imageUrl)) match {
        case Success(uri) =>
          val req = HttpRequest(uri = uri, headers = Seq(Accept.create(MediaRanges.`image/*`)))

          Http()(actorSystem)
            .singleRequest(req)
            .flatMap {
              case response if response.status.isFailure() =>
                response.entity.toStrict(2.second).flatMap { entity =>
                  val body = entity.data.decodeString(Charset.forName("UTF-8"))
                  val code = response.status.value
                  Future.failed(
                    new IllegalStateException(s"URL failed with status code: $code, from: $imageUrl with body: $body")
                  )
                }
              case response if !response.entity.httpEntity.contentType.mediaType.isImage =>
                response.discardEntityBytes()
                Future.failed(new IllegalStateException(s"URL does not refer to an image: $imageUrl"))
              case response if response.status.isRedirection() =>
                response.discardEntityBytes()
                Future.failed(new IllegalStateException(s"URL is a redirection: $imageUrl"))
              case response =>
                val contentType = response.entity.httpEntity.contentType
                val dest = destFn(contentType)
                response.entity.dataBytes
                  .runWith(FileIO.toPath(dest.toPath))
                  .map(_ => contentType -> dest)
            }
        case Failure(e) => Future.failed(e)
      }
    }
  }
}
