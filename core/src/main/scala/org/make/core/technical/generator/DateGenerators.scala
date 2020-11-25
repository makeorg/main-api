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

package org.make.core.technical.generator

import java.time.temporal.{ChronoField, TemporalAmount}
import java.time.{Instant, LocalDate, ZoneOffset, ZonedDateTime}

import org.make.core.{DateHelper, Validation}
import org.scalacheck.Gen

trait DateGenerators {

  /**
    * Creates a date generator which will generate dates in years close to current year
    *
    * Example:
    * `genDateWithOffset(lowerOffset = Duration.of(-3L, ChronoUnit.YEARS), upperOffset =  Duration.of(6L, ChronoUnit.MONTHS))`
    * will generate a date between now minus 3 years and now plus 6 months
    *
    * @param lowerOffset the minimum temporal amount to offset
    * @param upperOffset the maximum temporal amount to offset
    * @param fromDate the date to apply offsets to
    * @return a generator with dates constrained
    */
  def genDateWithOffset(
    lowerOffset: TemporalAmount,
    upperOffset: TemporalAmount,
    fromDate: ZonedDateTime = DateHelper.now()
  ): Gen[ZonedDateTime] = {
    val lowerBound: Long = fromDate.plus(lowerOffset).toInstant.toEpochMilli
    val upperBound: Long = fromDate.plus(upperOffset).toInstant.toEpochMilli

    Gen.choose(lowerBound, upperBound).map(offset => Instant.ofEpochMilli(offset).atZone(ZoneOffset.UTC))
  }

  lazy val genBirthDate: Gen[LocalDate] = {
    for {
      age <- Gen.choose(Validation.minAgeWithLegalConsent, Validation.maxAge)
    } yield {
      val currentYear: Int = DateHelper.now().get(ChronoField.YEAR)
      LocalDate.parse(s"${currentYear - age}-01-01")
    }
  }

}
