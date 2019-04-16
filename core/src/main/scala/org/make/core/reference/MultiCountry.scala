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

package org.make.core.reference

import io.circe.{Decoder, Encoder, Json}
import org.make.core.StringValue
import org.make.core.Validation.{maxLength, validate}
import spray.json.{JsString, JsValue, JsonFormat}

case class Country(value: String) extends StringValue {
  override def toString: String = value

  validate(maxLength("country", 3, value))
}

object Country {
  // Make sure countries are always upper case
  implicit lazy val countryEncoder: Encoder[Country] =
    (a: Country) => Json.fromString(a.value.toUpperCase())
  implicit lazy val countryDecoder: Decoder[Country] =
    Decoder.decodeString.map(country => Country(country.toUpperCase()))

  implicit val CountryFormatter: JsonFormat[Country] = new JsonFormat[Country] {
    override def read(json: JsValue): Country = json match {
      case JsString(s) => Country(s.toUpperCase())
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: Country): JsValue = {
      JsString(obj.value.toUpperCase())
    }
  }
}

case class Language(value: String) extends StringValue {
  override def toString: String = value

  validate(maxLength("language", 3, value))
}

object Language {
  // Make sure languages are always lower case
  implicit lazy val LanguageEncoder: Encoder[Language] =
    (a: Language) => Json.fromString(a.value.toLowerCase())
  implicit lazy val LanguageDecoder: Decoder[Language] =
    Decoder.decodeString.map(language => Language(language.toLowerCase()))

  implicit val LanguageFormatter: JsonFormat[Language] = new JsonFormat[Language] {
    override def read(json: JsValue): Language = json match {
      case JsString(s) => Language(s.toLowerCase())
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: Language): JsValue = {
      JsString(obj.value.toLowerCase())
    }
  }
}
