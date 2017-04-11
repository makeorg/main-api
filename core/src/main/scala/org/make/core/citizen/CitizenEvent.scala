package org.make.core.citizen


sealed trait CitizenEvent {
  def citizenId: CitizenId
}

case class CitizenRegistered(citizenId: CitizenId) extends CitizenEvent
