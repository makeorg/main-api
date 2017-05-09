package org.make.api

import java.time.{ZoneId, ZonedDateTime}

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


}
