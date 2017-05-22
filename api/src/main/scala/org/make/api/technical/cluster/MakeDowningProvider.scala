package org.make.api.technical.cluster

import akka.actor.{ActorSystem, Props}
import akka.cluster.DowningProvider
import org.make.api.extensions.MakeSettings

import scala.concurrent.duration.FiniteDuration

class MakeDowningProvider(system: ActorSystem) extends DowningProvider {
  private val settings = MakeSettings(system)

  override def downRemovalMargin: FiniteDuration = ???
  override def downingActorProps: Option[Props] = ???
}
