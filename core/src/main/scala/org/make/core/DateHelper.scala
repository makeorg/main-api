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

package org.make.core

import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

trait DateHelper {
  def now(): ZonedDateTime
  def computeBirthDate(age: Int): LocalDate
  def isLast30daysDate(date: ZonedDateTime): Boolean
}

object DateHelper extends DateHelper {
  private val utc = ZoneOffset.UTC

  def now(): ZonedDateTime = {
    ZonedDateTime.now(utc)
  }

  def isLast30daysDate(date: ZonedDateTime): Boolean = {
    val days: Int = 30
    date.isAfter(DateHelper.now().minusDays(days))
  }

  implicit object OrderedJavaTime extends Ordering[ZonedDateTime] {

    override def compare(x: ZonedDateTime, y: ZonedDateTime): Int = x.compareTo(y)
  }

  implicit class RichJavaTime(val self: ZonedDateTime) extends AnyVal {
    def toUTC: ZonedDateTime = {
      self.withZoneSameInstant(ZoneOffset.UTC)
    }
  }

  def computeBirthDate(age: Int): LocalDate = {
    val birthYear = LocalDate.now().getYear - age
    LocalDate.parse(s"$birthYear-01-01")
  }
}
