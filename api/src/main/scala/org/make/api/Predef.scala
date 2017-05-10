package org.make.api

import java.time.{ZoneId, ZoneOffset, ZonedDateTime}

import org.joda.time.DateTime


object Predef {


  implicit class RichJodaDateTime(val self: DateTime) extends AnyVal {

    def toJavaTime: ZonedDateTime = {
      ZonedDateTime.of(
        self.getYear,
        self.getMonthOfYear,
        self.getDayOfMonth,
        self.getHourOfDay,
        self.getMinuteOfHour,
        self.getSecondOfMinute,
        self.getMillisOfSecond * 1000000,
        ZoneId.of(self.getZone.getID, ZoneId.SHORT_IDS))
    }
  }


  implicit class RichJavaTime(val self: ZonedDateTime) extends AnyVal {

    def toUTC: ZonedDateTime = {
      self.withZoneSameInstant(ZoneOffset.UTC)
    }
  }

}
