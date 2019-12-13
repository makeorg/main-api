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

package org.make.core.question

import io.circe.{Decoder, Encoder, Json, KeyDecoder, KeyEncoder}
import org.make.core.StringValue
import org.make.core.operation.OperationId
import org.make.core.reference.{Country, Language, ThemeId}
import spray.json.{JsString, JsValue, JsonFormat}

case class Question(questionId: QuestionId,
                    slug: String,
                    country: Country,
                    language: Language,
                    question: String,
                    operationId: Option[OperationId],
                    themeId: Option[ThemeId]) {
  def getLocale: String = s"${language.value.toLowerCase}_${country.value.toUpperCase}"
}

case class QuestionId(value: String) extends StringValue

object QuestionId {
  implicit lazy val QuestionIdEncoder: Encoder[QuestionId] =
    (a: QuestionId) => Json.fromString(a.value)
  implicit lazy val QuestionIdDecoder: Decoder[QuestionId] =
    Decoder.decodeString.map(QuestionId(_))

  implicit val QuestionIdFormatter: JsonFormat[QuestionId] = new JsonFormat[QuestionId] {
    override def read(json: JsValue): QuestionId = json match {
      case JsString(s) => QuestionId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: QuestionId): JsValue = {
      JsString(obj.value)
    }
  }

  implicit val questionIdKeyEncoder: KeyEncoder[QuestionId] = new KeyEncoder[QuestionId] {
    override def apply(questionId: QuestionId): String = questionId.value
  }
  implicit val questionIdKeyDecoder: KeyDecoder[QuestionId] = new KeyDecoder[QuestionId] {
    override def apply(key: String): Option[QuestionId] = Some(QuestionId(key))
  }
}
