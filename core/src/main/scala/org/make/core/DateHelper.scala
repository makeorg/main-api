package org.make.core

import java.time.{ZoneOffset, ZonedDateTime}

trait DateHelper {
  def now(): ZonedDateTime
}
object DateHelper extends DateHelper {
  private val utc = ZoneOffset.UTC

  def now(): ZonedDateTime = {
    ZonedDateTime.now(utc)
  }

  implicit class RichJavaTime(val self: ZonedDateTime) extends AnyVal {

    def toUTC: ZonedDateTime = {
      self.withZoneSameInstant(ZoneOffset.UTC)
    }
  }
}
