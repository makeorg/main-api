package org.make.api.technical

import java.time.{ZoneOffset, ZonedDateTime}

object DateHelper {
  private val utc = ZoneOffset.UTC

  def now(): ZonedDateTime = {
    ZonedDateTime.now(utc)
  }

  def toUtc(zonedDateTime: ZonedDateTime): ZonedDateTime = {
    zonedDateTime.withZoneSameInstant(utc)
  }
}
