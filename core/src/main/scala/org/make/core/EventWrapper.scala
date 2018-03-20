package org.make.core

import java.time.ZonedDateTime

import shapeless.Coproduct

trait EventWrapper extends Sharded {
  def version: Int
  def date: ZonedDateTime
  def eventType: String
  def event: Coproduct
}

trait Sharded {
  def id: String
}
