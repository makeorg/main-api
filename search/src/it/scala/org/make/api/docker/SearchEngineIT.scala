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

package org.make.api.docker

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Flow, Source => AkkaSource}
import grizzled.slf4j.Logging
import io.circe.Encoder
import io.circe.syntax._
import org.make.api.EmptyActorSystemComponent
import org.make.core.StringValue
import org.scalatest.Suite

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

trait SearchEngineIT[Id <: StringValue, T]
    extends DockerElasticsearchService
    with EmptyActorSystemComponent
    with Logging { self: Suite =>

  def eSIndexName: String
  def eSDocType: String
  def docs: Seq[T]

  def initializeElasticsearch(id: T => Id)(implicit encoder: Encoder[T]): Unit = {
    val elasticsearchEndpoint = s"http://localhost:$elasticsearchExposedPort"
    val mapping =
      Source.fromResource(s"elasticsearch-mappings/$eSDocType.json")(Codec.UTF8).getLines().mkString("")
    val createMappingFuture: Future[HttpResponse] = Http().singleRequest(
      HttpRequest(
        uri = s"$elasticsearchEndpoint/$eSIndexName",
        method = HttpMethods.PUT,
        entity = HttpEntity(ContentTypes.`application/json`, mapping)
      )
    )

    Await.result(createMappingFuture, 5.seconds)
    createMappingFuture.onComplete {
      case Failure(e) =>
        logger.error(s"Cannot create elasticsearch schema: ${e.getStackTrace.mkString("\n")}")
        fail(e)
      case Success(_) => logger.debug(s"""Elasticsearch mapped successfully on index "$eSIndexName" """)
    }

    val pool: Flow[(HttpRequest, Id), (Try[HttpResponse], Id), Http.HostConnectionPool] =
      Http().cachedHostConnectionPool[Id](
        "localhost",
        elasticsearchExposedPort,
        ConnectionPoolSettings(actorSystem).withMaxConnections(3)
      )

    val insertFutures = AkkaSource[T](docs).map { value =>
      val indexAndDocTypeEndpoint = s"$eSIndexName/_doc"
      (
        HttpRequest(
          uri = s"$elasticsearchEndpoint/$indexAndDocTypeEndpoint/${id(value).value}",
          method = HttpMethods.PUT,
          entity = HttpEntity(ContentTypes.`application/json`, value.asJson.toString)
        ),
        id(value)
      )
    }.via(pool).runForeach {
      case (response, id) =>
        val error = response match {
          case Failure(e) => Some(e)
          case Success(HttpResponse(StatusCodes.ClientError(_), _, HttpEntity.Strict(_, e), _)) =>
            Some(new Exception(e.utf8String))
          case _ => None
        }
        error.foreach(e => logger.error(s"Error when indexing $eSDocType ${id.value}:", e))
        ()
    }

    Await.result(insertFutures, 150.seconds)
    logger.debug(s"${eSDocType.capitalize} indexed successfully.")

    val responseRefreshFuture: Future[HttpResponse] = Http().singleRequest(
      HttpRequest(uri = s"$elasticsearchEndpoint/$eSIndexName/_refresh", method = HttpMethods.POST)
    )
    Await.result(responseRefreshFuture, 5.seconds)

    ()
  }

}
