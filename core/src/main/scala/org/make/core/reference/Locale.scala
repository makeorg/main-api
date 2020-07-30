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

package org.make.core.reference

import cats.Show
import cats.syntax.show._
import io.circe.{Decoder, Encoder}
import spray.json.{JsString, JsValue, JsonFormat}

final case class Locale(language: Language, country: Country)

object Locale {

  def parse(s: String): Either[String, Locale] = s.split('_').toList match {
    case language :: country :: Nil => Right(Locale(Language(language), Country(country)))
    case _                          => Left(s"Unable to convert $s")
  }

  implicit val localeShow: Show[Locale] = Show.show(locale => s"${locale.language.value}_${locale.country.value}")

  implicit val localeDecoder: Decoder[Locale] = Decoder[String].emap(parse)
  implicit val localeEncoder: Encoder[Locale] = Encoder[String].contramap(_.show)

  implicit val localeFormatter: JsonFormat[Locale] = new JsonFormat[Locale] {
    override def read(json: JsValue): Locale = json match {
      case JsString(s) => parse(s).fold(e => throw new IllegalArgumentException(e), identity)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(locale: Locale): JsValue = JsString(locale.show)
  }

}
