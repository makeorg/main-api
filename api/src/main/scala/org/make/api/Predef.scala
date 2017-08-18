package org.make.api

import java.time.{ZoneId, ZoneOffset, ZonedDateTime}
import java.util.concurrent.TimeUnit

import akka.http.scaladsl.server.{Directive0, Directive1}
import org.joda.time.DateTime

import scala.concurrent.duration.FiniteDuration

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
        ZoneId.of(self.getZone.getID, ZoneId.SHORT_IDS)
      )
    }
  }

  implicit class RichJavaTime(val self: ZonedDateTime) extends AnyVal {

    def toUTC: ZonedDateTime = {
      self.withZoneSameInstant(ZoneOffset.UTC)
    }
  }

  implicit class RichScalaDuration(val self: java.time.Duration) extends AnyVal {
    def toScala: FiniteDuration = {
      FiniteDuration(self.toNanos, TimeUnit.NANOSECONDS)
    }
  }

  implicit class RichDirective0(val self: Directive0) extends AnyVal {
    def map[B](f: Unit => B): Directive1[B] = {
      self.tmap(f)
    }

    def flatMap[B](f: Unit => Directive1[B]): Directive1[B] = {
      self.tflatMap(f)
    }
  }
}
