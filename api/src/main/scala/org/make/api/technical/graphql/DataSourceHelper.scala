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

package org.make.api.technical.graphql

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated}
import cats.implicits._
import cats.{Id, Show, Traverse}
import org.make.core.StringValue
import zio.query.{CompletedRequestMap, DataSource}
import zio.{Chunk, Task}

import scala.concurrent.{ExecutionContext, Future}

object DataSourceHelper {
  def createDataSource[F[_]: Traverse, EntityId <: StringValue, Entity, Request <: IdsRequest[F, EntityId, Entity]](
    name: String,
    findFromIds: Seq[EntityId] => ExecutionContext => Future[Map[EntityId, Entity]]
  ): DataSource[Any, Request] = {
    DataSource.Batched.make(name) { chunk =>
      val resultMap = CompletedRequestMap.empty
      val ids: Chunk[EntityId] = chunk.toSeq.flatMap(_.ids.toIterable)
      val entities: Task[Map[EntityId, Entity]] = Task.fromFuture(ec => findFromIds(ids)(ec))
      implicit val showId: Show[EntityId] = Show.show[EntityId](_.value)

      entities.fold(
        err => chunk.foldLeft(resultMap) { case (map, req) => map.insert(req)(Left(err)) }, { result =>
          chunk.foldLeft(resultMap) {
            case (map, req) =>
              val res: Either[Exception, F[Entity]] = Traverse[F]
                .traverse(req.ids) { id =>
                  result.get(id) match {
                    case Some(value) => Valid(value)
                    case None        => Validated.invalidNel(id)
                  }
                }
                .toEither
                .leftMap(invalids => DataSourceIdsNotFound(name, invalids))
              map.insert(req)(res)
          }
        }
      )
    }
  }

  def one[EntityId <: StringValue, Entity, Request <: IdsRequest[Id, EntityId, Entity]](
    name: String,
    findFromIds: Seq[EntityId] => ExecutionContext => Future[Map[EntityId, Entity]]
  ): DataSource[Any, Request] = {
    createDataSource[Id, EntityId, Entity, Request](name, findFromIds)
  }

  def seq[EntityId <: StringValue, Entity, Request <: IdsRequest[List, EntityId, Entity]](
    name: String,
    findFromIds: Seq[EntityId] => ExecutionContext => Future[Map[EntityId, Entity]]
  ): DataSource[Any, Request] = {
    createDataSource[List, EntityId, Entity, Request](name, findFromIds)
  }

}

final case class DataSourceIdsNotFound[EntityId: Show](name: String, idsNotFound: NonEmptyList[EntityId])
    extends Exception(s"DataSource $name could not find entities with ids: ${idsNotFound.mkString_(", ")}")
