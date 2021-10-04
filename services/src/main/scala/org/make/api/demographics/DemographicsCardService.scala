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

import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Encoder, Json}
import io.swagger.annotations.ApiModelProperty
import org.make.core.demographics.DemographicsCard.Layout
import org.make.core.demographics.{DemographicsCard, DemographicsCardId}
import org.make.core.question.QuestionId
import org.make.core.reference.Language
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.Order

import scala.annotation.meta.field
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

final case class DemographicsCardResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "37e46b4c-73e7-41fa-b8e1-5e18efddaac3")
  id: DemographicsCardId,
  name: String,
  @(ApiModelProperty @field)(
    dataType = "string",
    allowableValues = "Select,OneColumnRadio,ThreeColumnsRadio",
    example = "OneColumnRadio"
  )
  layout: DemographicsCard.Layout,
  title: String,
  @(ApiModelProperty @field)(dataType = "object")
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
      parameters = DemographicsCard.parseParameters(card.parameters),
      token = token
    )
}
