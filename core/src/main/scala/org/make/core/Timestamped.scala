package org.make.core

import java.time.ZonedDateTime

trait Timestamped {
  def createdAt: Option[ZonedDateTime]
  def updatedAt: Option[ZonedDateTime]
}
