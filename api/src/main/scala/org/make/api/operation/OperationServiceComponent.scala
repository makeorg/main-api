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

package org.make.api.operation

import akka.Done
import akka.stream.scaladsl.{Sink, Source}

import java.time.LocalDate
import io.circe.syntax._
import org.make.api.ActorSystemComponent
import org.make.api.question.PersistentQuestionServiceComponent
import org.make.api.tag.PersistentTagServiceComponent
import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.core.{DateHelper, Order}
import org.make.core.operation._
import org.make.core.operation.OperationActionType._
import org.make.core.reference.Country
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait OperationServiceComponent {
  def operationService: OperationService
}

trait OperationService extends ShortenedNames {
  def find(
    slug: Option[String] = None,
    country: Option[Country] = None,
    maybeSource: Option[String],
    openAt: Option[LocalDate] = None
  ): Future[Seq[Operation]]
  def findSimple(
    start: Start = Start.zero,
    end: Option[End] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    slug: Option[String] = None,
    operationKinds: Option[Seq[OperationKind]] = None
  ): Future[Seq[SimpleOperation]]
  def findOne(operationId: OperationId): Future[Option[Operation]]
  def findOneSimple(operationId: OperationId): Future[Option[SimpleOperation]]
  def findOneBySlug(slug: String): Future[Option[Operation]]
  def create(userId: UserId, slug: String, operationKind: OperationKind): Future[OperationId]
  def update(
    operationId: OperationId,
    userId: UserId,
    slug: Option[String] = None,
    status: Option[OperationStatus] = None,
    operationKind: Option[OperationKind] = None
  ): Future[Option[OperationId]]
  def count(slug: Option[String], operationKinds: Option[Seq[OperationKind]]): Future[Int]
}

trait DefaultOperationServiceComponent extends OperationServiceComponent with ShortenedNames {
  this: PersistentOperationServiceComponent
    with IdGeneratorComponent
    with PersistentTagServiceComponent
    with PersistentQuestionServiceComponent
    with OperationOfQuestionServiceComponent
    with ActorSystemComponent =>

  override lazy val operationService: OperationService = new DefaultOperationService

  class DefaultOperationService extends OperationService {

    override def find(
      slug: Option[String] = None,
      country: Option[Country] = None,
      maybeSource: Option[String],
      openAt: Option[LocalDate] = None
    ): Future[Seq[Operation]] = {

      persistentOperationService.find(slug = slug, country = country, openAt = openAt)
    }

    override def findSimple(
      start: Start = Start.zero,
      end: Option[End] = None,
      sort: Option[String] = None,
      order: Option[Order] = None,
      slug: Option[String] = None,
      operationKinds: Option[Seq[OperationKind]]
    ): Future[Seq[SimpleOperation]] = {

      persistentOperationService.findSimple(
        start = start,
        end = end,
        sort = sort,
        order = order,
        slug = slug,
        operationKinds = operationKinds
      )
    }

    override def findOne(operationId: OperationId): Future[Option[Operation]] = {
      persistentOperationService.getById(operationId)
    }

    override def findOneSimple(operationId: OperationId): Future[Option[SimpleOperation]] = {
      persistentOperationService.getSimpleById(operationId)
    }

    override def findOneBySlug(slug: String): Future[Option[Operation]] = {
      persistentOperationService.getBySlug(slug)
    }

    override def create(userId: UserId, slug: String, operationKind: OperationKind): Future[OperationId] = {
      val now = DateHelper.now()

      val persist: Future[SimpleOperation] = persistentOperationService.persist(
        SimpleOperation(
          operationId = idGenerator.nextOperationId(),
          status = OperationStatus.Pending,
          slug = slug,
          operationKind = operationKind,
          createdAt = Some(now),
          updatedAt = Some(now)
        )
      )

      def addAction(persisted: SimpleOperation): Future[Boolean] =
        persistentOperationService
          .addActionToOperation(
            OperationAction(
              makeUserId = userId,
              actionType = OperationCreateAction.value,
              arguments = Map("operation" -> operationToString(persisted))
            ),
            persisted.operationId
          )

      for {
        persisted <- persist
        _         <- addAction(persisted)
      } yield persisted.operationId
    }

    override def update(
      operationId: OperationId,
      userId: UserId,
      slug: Option[String] = None,
      status: Option[OperationStatus] = None,
      operationKind: Option[OperationKind] = None
    ): Future[Option[OperationId]] = {

      persistentOperationService
        .getById(operationId)
        .flatMap {
          case None => Future.successful(None)
          case Some(operation) =>
            val modify = persistentOperationService.modify(
              SimpleOperation(
                operationId = operationId,
                status = status.getOrElse(operation.status),
                slug = slug.getOrElse(operation.slug),
                operationKind = operationKind.getOrElse(operation.operationKind),
                createdAt = operation.createdAt,
                updatedAt = operation.updatedAt
              )
            )

            def addAction(updated: SimpleOperation): Future[Boolean] =
              persistentOperationService
                .addActionToOperation(
                  OperationAction(
                    makeUserId = userId,
                    actionType = OperationUpdateAction.value,
                    arguments = Map("operation" -> operationToString(updated))
                  ),
                  updated.operationId
                )

            def updateQuestions(updated: SimpleOperation): Future[Done] =
              Source(operation.questions)
                .mapAsync(5) { question =>
                  operationOfQuestionService.indexById(question.question.questionId)
                }
                .runWith(Sink.ignore)

            for {
              updated <- modify
              _       <- addAction(updated)
              _       <- updateQuestions(updated)
            } yield Some(updated.operationId)
        }
    }

    private def operationToString(operation: SimpleOperation): String = {
      scala.collection
        .Map[String, String]("operationId" -> operation.operationId.value, "status" -> operation.status.value)
        .asJson
        .toString
    }

    override def count(slug: Option[String], operationKinds: Option[Seq[OperationKind]]): Future[Int] = {
      persistentOperationService.count(slug = slug, operationKinds = operationKinds)
    }

  }
}
