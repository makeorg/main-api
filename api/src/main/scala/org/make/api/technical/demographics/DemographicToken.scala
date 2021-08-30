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

package org.make.api.technical.demographics

import org.make.core.question.QuestionId
import org.make.core.reference.Country

import java.time.ZonedDateTime

final case class DemographicToken(createdAt: ZonedDateTime, cardName: String, country: Country, question: QuestionId) {

  private val SEPARATOR: Char = DemographicToken.SEPARATOR

  def toTokenizedString(): String = {
    s"${createdAt.toString()}${SEPARATOR}${cardName}${SEPARATOR}${country.value}${SEPARATOR}${question.value}"
  }
}

object DemographicToken {

  private val SEPARATOR: Char = '|'

  def fromString(token: String): DemographicToken = {
    val Array(date, cardName, country, question) = token.split(SEPARATOR)
    DemographicToken(
      createdAt = ZonedDateTime.parse(date),
      cardName = cardName,
      country = Country(country),
      question = QuestionId(question)
    )
  }

}
