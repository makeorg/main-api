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
import org.make.api.tag.PersistentTagServiceComponent
import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OperationServiceComponent {
  def operationService: OperationService
}

trait OperationService extends ShortenedNames {
  def find(slug: Option[String] = None,
           country: Option[Country] = None,
           maybeSource: Option[String],
           openAt: Option[LocalDate] = None): Future[Seq[Operation]]
  def findOne(operationId: OperationId): Future[Option[Operation]]
  def findOneBySlug(slug: String): Future[Option[Operation]]
  def create(userId: UserId,
             slug: String,
             translations: Seq[OperationTranslation] = Seq.empty,
             defaultLanguage: Language,
             countriesConfiguration: Seq[OperationCountryConfiguration]): Future[OperationId]
  def update(operationId: OperationId,
             userId: UserId,
             slug: Option[String] = None,
             translations: Option[Seq[OperationTranslation]] = None,
             defaultLanguage: Option[Language] = None,
             countriesConfiguration: Option[Seq[OperationCountryConfiguration]] = None,
             status: Option[OperationStatus] = None): Future[Option[OperationId]]
  def activate(operationId: OperationId, userId: UserId): Unit
  def archive(operationId: OperationId, userId: UserId): Unit
}

trait DefaultOperationServiceComponent extends OperationServiceComponent with ShortenedNames {
  this: PersistentOperationServiceComponent with IdGeneratorComponent with PersistentTagServiceComponent =>

  val operationService: OperationService = new OperationService {

    override def find(slug: Option[String] = None,
                      country: Option[Country] = None,
                      maybeSource: Option[String],
                      openAt: Option[LocalDate] = None): Future[Seq[Operation]] = {

      persistentOperationService.find(slug = slug, country = country, openAt = openAt).flatMap { operations =>
        val filteredOperations = maybeSource match {
          case Some(source) => operations.filter(operation => operation.allowedSources.contains(source))
          case _            => operations
        }
        Future.traverse(filteredOperations) { operation =>
          Future
            .traverse(operation.countriesConfiguration) { configuration =>
              configuration.questionId.map { questionId =>
                persistentTagService.findByQuestion(questionId).map { tags =>
                  configuration.copy(tagIds = tags.map(_.tagId))
                }
              }.getOrElse(Future.successful(configuration))
            }
            .map { configurations =>
              operation.copy(countriesConfiguration = configurations)
            }
        }
      }
    }

    override def findOne(operationId: OperationId): Future[Option[Operation]] = {
      persistentOperationService.getById(operationId)
    }

    override def findOneBySlug(slug: String): Future[Option[Operation]] = {
      persistentOperationService.getBySlug(slug)
    }

    override def create(userId: UserId,
                        slug: String,
                        translations: Seq[OperationTranslation] = Seq.empty,
                        defaultLanguage: Language,
                        countriesConfiguration: Seq[OperationCountryConfiguration]): Future[OperationId] = {
      val now = DateHelper.now()
      val operation: Operation = Operation(
        operationId = idGenerator.nextOperationId(),
        status = OperationStatus.Pending,
        slug = slug,
        translations = translations,
        defaultLanguage = defaultLanguage,
        allowedSources = Seq.empty,
        countriesConfiguration = countriesConfiguration,
        events = Nil,
        createdAt = Some(now),
        updatedAt = Some(now)
      )
      persistentOperationService
        .persist(
          operation.copy(
            events = List(
              OperationAction(
                makeUserId = userId,
                actionType = OperationCreateAction.name,
                arguments = Map("operation" -> operationToString(operation))
              )
            )
          )
        )
        .map(_.operationId)
    }

    override def update(operationId: OperationId,
                        userId: UserId,
                        slug: Option[String] = None,
                        translations: Option[Seq[OperationTranslation]] = None,
                        defaultLanguage: Option[Language] = None,
                        countriesConfiguration: Option[Seq[OperationCountryConfiguration]] = None,
                        status: Option[OperationStatus] = None): Future[Option[OperationId]] = {

      val now = DateHelper.now()
      persistentOperationService
        .getById(operationId)
        .flatMap(_.map { registeredOperation =>
          val operationUpdated = registeredOperation.copy(
            slug = slug.getOrElse(registeredOperation.slug),
            translations = translations.getOrElse(registeredOperation.translations),
            defaultLanguage = defaultLanguage.getOrElse(registeredOperation.defaultLanguage),
            countriesConfiguration = countriesConfiguration.getOrElse(registeredOperation.countriesConfiguration),
            status = status.getOrElse(registeredOperation.status),
            updatedAt = Some(now)
          )
          persistentOperationService
            .modify(
              operationUpdated.copy(
                events = OperationAction(
                  makeUserId = userId,
                  actionType = OperationUpdateAction.name,
                  arguments = Map("operation" -> operationToString(operationUpdated))
                ) :: registeredOperation.events
              )
            )
            .map(operation => Some(operation.operationId))
        }.getOrElse(Future.successful(None)))
    }

    override def activate(operationId: OperationId, userId: UserId): Unit = {
      persistentOperationService
        .getById(operationId)
        .map(_.map { operation =>
          val operationUpdated: Operation =
            operation.copy(status = OperationStatus.Active, updatedAt = Some(DateHelper.now()))

          persistentOperationService.modify(
            operationUpdated.copy(
              events = OperationAction(
                makeUserId = userId,
                actionType = OperationActivateAction.name,
                arguments = Map("operation" -> operationToString(operationUpdated))
              ) :: operation.events
            )
          )
        })
    }

    override def archive(operationId: OperationId, userId: UserId): Unit = {
      persistentOperationService
        .getById(operationId)
        .map(_.map { operation =>
          val operationUpdated = operation.copy(status = OperationStatus.Archived, updatedAt = Some(DateHelper.now()))

          persistentOperationService.modify(
            operationUpdated.copy(
              events = OperationAction(
                makeUserId = userId,
                actionType = OperationArchiveAction.name,
                arguments = Map("operation" -> operationToString(operationUpdated))
              ) :: operation.events
            )
          )
        })
    }

    private def operationToString(operation: Operation): String = {
      scala.collection
        .Map[String, String](
          "operationId" -> operation.operationId.value,
          "status" -> operation.status.shortName,
          "translations" -> operation.translations
            .map(translation => s"${translation.language}:${translation.title}")
            .mkString(","),
          "defaultLanguage" -> operation.defaultLanguage.value,
          "countriesConfiguration" -> operation.countriesConfiguration
            .map(countryConfiguration => s"""${countryConfiguration.countryCode}:
                  |${countryConfiguration.landingSequenceId}:
                  |${countryConfiguration.tagIds.map(_.value).mkString("[", ",", "]")}""".stripMargin)
            .mkString(",")
        )
        .asJson
        .toString
    }
  }
}
