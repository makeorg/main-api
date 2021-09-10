/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.demographics

import cats.implicits._
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.make.api.technical.security.AESEncryptionComponent
import org.make.api.technical.{IdGeneratorComponent, MakeRandom}
import org.make.core.demographics.DemographicsCard.Layout
import org.make.core.demographics.{DemographicsCard, DemographicsCardId}
import org.make.core.question.QuestionId
import org.make.core.reference.Language
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.{CirceFormatters, DateHelperComponent, Order}

import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DemographicsCardService {
  def get(id: DemographicsCardId): Future[Option[DemographicsCard]]
  def list(
    start: Option[Start],
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    language: Option[Language],
    dataType: Option[String]
  ): Future[Seq[DemographicsCard]]
  def create(
    name: String,
    layout: Layout,
    dataType: String,
    language: Language,
    title: String,
    parameters: String
  ): Future[DemographicsCard]
  def update(
    id: DemographicsCardId,
    name: String,
    layout: Layout,
    dataType: String,
    language: Language,
    title: String,
    parameters: String
  ): Future[Option[DemographicsCard]]
  def count(language: Option[Language], dataType: Option[String]): Future[Int]
  def generateToken(id: DemographicsCardId, questionId: QuestionId): String
  def isTokenValid(token: String, id: DemographicsCardId, questionId: QuestionId): Boolean
  def getOneRandomCardByQuestion(questionId: QuestionId): Future[Option[DemographicsCardResponse]]
  def getOrPickRandom(
    maybeId: Option[DemographicsCardId],
    maybeToken: Option[String],
    questionId: QuestionId
  ): Future[Option[DemographicsCardResponse]]
}

trait DemographicsCardServiceComponent {
  def demographicsCardService: DemographicsCardService
}

trait DefaultDemographicsCardServiceComponent extends DemographicsCardServiceComponent {
  self: ActiveDemographicsCardServiceComponent
    with AESEncryptionComponent
    with DateHelperComponent
    with IdGeneratorComponent
    with PersistentDemographicsCardServiceComponent =>

  override def demographicsCardService: DemographicsCardService = new DemographicsCardService {

    override def get(id: DemographicsCardId): Future[Option[DemographicsCard]] =
      persistentDemographicsCardService.get(id)

    override def list(
      start: Option[Start],
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      language: Option[Language],
      dataType: Option[String]
    ): Future[Seq[DemographicsCard]] =
      persistentDemographicsCardService.list(start, end, sort, order, language, dataType)

    override def create(
      name: String,
      layout: Layout,
      dataType: String,
      language: Language,
      title: String,
      parameters: String
    ): Future[DemographicsCard] =
      persistentDemographicsCardService.persist(
        DemographicsCard(
          id = idGenerator.nextDemographicsCardId(),
          name = name,
          layout = layout,
          dataType = dataType,
          language = language,
          title = title,
          parameters = parameters,
          createdAt = dateHelper.now(),
          updatedAt = dateHelper.now()
        )
      )

    override def update(
      id: DemographicsCardId,
      name: String,
      layout: Layout,
      dataType: String,
      language: Language,
      title: String,
      parameters: String
    ): Future[Option[DemographicsCard]] = {
      get(id).flatMap(
        _.traverse(
          existing =>
            persistentDemographicsCardService
              .modify(
                existing.copy(
                  name = name,
                  layout = layout,
                  dataType = dataType,
                  language = language,
                  title = title,
                  parameters = parameters,
                  updatedAt = dateHelper.now()
                )
              )
        )
      )
    }

    override def count(language: Option[Language], dataType: Option[String]): Future[Int] =
      persistentDemographicsCardService.count(language, dataType)

    override def generateToken(id: DemographicsCardId, questionId: QuestionId): String = {
      val nowDate: ZonedDateTime = dateHelper.now()
      val token = DemographicToken(nowDate, id, questionId)
      aesEncryption.encryptAndEncode(token.toTokenizedString)
    }

    override def isTokenValid(token: String, id: DemographicsCardId, questionId: QuestionId): Boolean = {
      aesEncryption.decodeAndDecrypt(token).toOption.exists { decryptedToken =>
        val nowDate: ZonedDateTime = dateHelper.now()
        val demoToken = DemographicToken.fromString(decryptedToken)
        demoToken.createdAt.plusHours(1).isAfter(nowDate) && demoToken.id == id && demoToken.questionId == questionId
      }
    }

    override def getOneRandomCardByQuestion(questionId: QuestionId): Future[Option[DemographicsCardResponse]] = {
      activeDemographicsCardService
        .list(start = None, end = None, sort = None, order = None, questionId = Some(questionId))
        .flatMap { actives =>
          MakeRandom.shuffleSeq(actives).headOption.map(_.demographicsCardId) match {
            case Some(id) =>
              get(id).map(_.map(card => DemographicsCardResponse(card, generateToken(card.id, questionId))))
            case None => Future.successful(None)
          }
        }
    }

    override def getOrPickRandom(
      maybeId: Option[DemographicsCardId],
      maybeToken: Option[String],
      questionId: QuestionId
    ): Future[Option[DemographicsCardResponse]] = {
      (maybeId, maybeToken) match {
        case (Some(id), Some(token)) if isTokenValid(token, id, questionId) =>
          get(id).map(_.map(card => DemographicsCardResponse(card, token)))
        case _ => getOneRandomCardByQuestion(questionId)
      }
    }

  }
}

final case class DemographicToken(createdAt: ZonedDateTime, id: DemographicsCardId, questionId: QuestionId)
    extends CirceFormatters {

  private val SEPARATOR: Char = DemographicToken.SEPARATOR

  def toTokenizedString: String = {
    s"${createdAt}${SEPARATOR}${id.value}${SEPARATOR}${questionId.value}"
  }
}

object DemographicToken {

  private val SEPARATOR: Char = '|'

  def fromString(token: String): DemographicToken = {
    val Array(date, id, question) = token.split(SEPARATOR)
    DemographicToken(
      createdAt = ZonedDateTime.parse(date),
      id = DemographicsCardId(id),
      questionId = QuestionId(question)
    )
  }

}

final case class DemographicsCardResponse(
  id: DemographicsCardId,
  name: String,
  layout: DemographicsCard.Layout,
  title: String,
  parameters: Json,
  token: String
)

object DemographicsCardResponse {
  implicit val encoder: Encoder[DemographicsCardResponse] = deriveEncoder[DemographicsCardResponse]

  def apply(card: DemographicsCard, token: String): DemographicsCardResponse =
    DemographicsCardResponse(
      id = card.id,
      name = card.name,
      layout = card.layout,
      title = card.title,
      parameters = card.parameters.asJson,
      token = token
    )
}
