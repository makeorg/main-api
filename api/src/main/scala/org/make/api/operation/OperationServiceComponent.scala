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

import java.time.LocalDate

import io.circe.syntax._
import org.make.api.question.PersistentQuestionServiceComponent
import org.make.api.tag.PersistentTagServiceComponent
import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.core.{DateHelper, Order}
import org.make.core.operation._
import org.make.core.operation.OperationActionType._
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
    start: Int = 0,
    end: Option[Int] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    slug: Option[String] = None,
    operationKinds: Option[Seq[OperationKind]] = None
  ): Future[Seq[SimpleOperation]]
  def findOne(operationId: OperationId): Future[Option[Operation]]
  def findOneSimple(operationId: OperationId): Future[Option[SimpleOperation]]
  def findOneBySlug(slug: String): Future[Option[Operation]]
  def create(
    userId: UserId,
    slug: String,
    defaultLanguage: Language,
    allowedSources: Seq[String],
    operationKind: OperationKind
  ): Future[OperationId]
  def update(
    operationId: OperationId,
    userId: UserId,
    slug: Option[String] = None,
    defaultLanguage: Option[Language] = None,
    status: Option[OperationStatus] = None,
    allowedSources: Option[Seq[String]] = None,
    operationKind: Option[OperationKind]
  ): Future[Option[OperationId]]
  def count(slug: Option[String], operationKinds: Option[Seq[OperationKind]]): Future[Int]
}

trait DefaultOperationServiceComponent extends OperationServiceComponent with ShortenedNames {
  this: PersistentOperationServiceComponent
    with IdGeneratorComponent
    with PersistentTagServiceComponent
    with PersistentQuestionServiceComponent =>

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
      start: Int = 0,
      end: Option[Int] = None,
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

    override def create(
      userId: UserId,
      slug: String,
      defaultLanguage: Language,
      allowedSources: Seq[String],
      operationKind: OperationKind
    ): Future[OperationId] = {
      val now = DateHelper.now()

      val operation: SimpleOperation = SimpleOperation(
        operationId = idGenerator.nextOperationId(),
        status = OperationStatus.Pending,
        slug = slug,
        defaultLanguage = defaultLanguage,
        allowedSources = allowedSources,
        operationKind = operationKind,
        createdAt = Some(now),
        updatedAt = Some(now)
      )

      persistentOperationService.persist(operation).flatMap { persisted =>
        persistentOperationService
          .addActionToOperation(
            OperationAction(
              makeUserId = userId,
              actionType = OperationCreateAction.value,
              arguments = Map("operation" -> operationToString(operation))
            ),
            persisted.operationId
          )
          .map(_ => persisted.operationId)
      }
    }

    override def update(
      operationId: OperationId,
      userId: UserId,
      slug: Option[String] = None,
      defaultLanguage: Option[Language] = None,
      status: Option[OperationStatus] = None,
      allowedSources: Option[Seq[String]] = None,
      operationKind: Option[OperationKind]
    ): Future[Option[OperationId]] = {

      persistentOperationService
        .getById(operationId)
        .flatMap {
          case None => Future.successful(None)
          case Some(operation) =>
            val modifiedOperation = SimpleOperation(
              operationId = operationId,
              status = status.getOrElse(operation.status),
              slug = slug.getOrElse(operation.slug),
              allowedSources = allowedSources.getOrElse(operation.allowedSources),
              defaultLanguage = defaultLanguage.getOrElse(operation.defaultLanguage),
              operationKind = operationKind.getOrElse(operation.operationKind),
              createdAt = operation.createdAt,
              updatedAt = operation.updatedAt
            )
            persistentOperationService.modify(modifiedOperation).flatMap { operationUpdated =>
              persistentOperationService
                .addActionToOperation(
                  OperationAction(
                    makeUserId = userId,
                    actionType = OperationUpdateAction.value,
                    arguments = Map("operation" -> operationToString(operationUpdated))
                  ),
                  operationUpdated.operationId
                )
                .map(_ => Some(operationUpdated.operationId))
            }
        }
    }

    private def operationToString(operation: SimpleOperation): String = {
      scala.collection
        .Map[String, String](
          "operationId" -> operation.operationId.value,
          "status" -> operation.status.value,
          "defaultLanguage" -> operation.defaultLanguage.value
        )
        .asJson
        .toString
    }

    override def count(slug: Option[String], operationKinds: Option[Seq[OperationKind]]): Future[Int] = {
      persistentOperationService.count(slug = slug, operationKinds = operationKinds)
    }

  }
}
