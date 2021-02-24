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

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField.{HOUR_OF_DAY, MINUTE_OF_HOUR, NANO_OF_SECOND, SECOND_OF_MINUTE}
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import java.util.Calendar

trait DateHelper {
  def now(): ZonedDateTime
  def computeBirthDate(age: Int): LocalDate
  def isLast30daysDate(date: ZonedDateTime): Boolean
  def format(date: ZonedDateTime): String
}

trait DateHelperComponent {
  def dateHelper: DateHelper
}

trait DefaultDateHelperComponent extends DateHelperComponent {
  override val dateHelper: DateHelper = new DefaultDateHelper()
}

class DefaultDateHelper extends DateHelper {

  private val utc = ZoneOffset.UTC

  private val defaultDateFormatter: DateTimeFormatter = new DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE)
    .appendLiteral("T")
    .appendValue(HOUR_OF_DAY, 2)
    .appendLiteral(':')
    .appendValue(MINUTE_OF_HOUR, 2)
    .optionalStart
    .appendLiteral(':')
    .appendValue(SECOND_OF_MINUTE, 2)
    .optionalStart
    .appendFraction(NANO_OF_SECOND, 3, 3, true)
    .appendOffsetId()
    .toFormatter()

  override def now(): ZonedDateTime = {
    ZonedDateTime.now(utc).truncatedTo(ChronoUnit.MILLIS)
  }

  override def computeBirthDate(age: Int): LocalDate = {
    val birthYear = now().toLocalDate.getYear - age
    LocalDate.parse(s"$birthYear-01-01")
  }

  override def isLast30daysDate(date: ZonedDateTime): Boolean = {
    val days: Int = 30
    date.isAfter(now().minusDays(days))
  }

  override def format(date: ZonedDateTime): String = {
    defaultDateFormatter.format(date)
  }
}

object DateHelper extends DefaultDateHelper {

  implicit object OrderedJavaTime extends Ordering[ZonedDateTime] {
    override def compare(x: ZonedDateTime, y: ZonedDateTime): Int = x.compareTo(y)
  }

  implicit val zonedDateTimeOrder: cats.Order[ZonedDateTime] = cats.Order.fromOrdering

  implicit class RichJavaTime(val self: ZonedDateTime) extends AnyVal {
    def toUTC: ZonedDateTime = {
      self.withZoneSameInstant(ZoneOffset.UTC)
    }
  }

  implicit class RichCalendar(val self: Calendar) extends AnyVal {
    def toZonedDateTime: ZonedDateTime = {
      ZonedDateTime.ofInstant(self.toInstant, self.getTimeZone.toZoneId)
    }
  }

}
