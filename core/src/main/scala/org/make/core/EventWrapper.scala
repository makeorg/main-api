package org.make.core

import java.time.ZonedDateTime

import shapeless.Coproduct

trait EventWrapper {
  def version: Int
  def id: String
  def date: ZonedDateTime
  def eventType: String
  def event: Coproduct
}