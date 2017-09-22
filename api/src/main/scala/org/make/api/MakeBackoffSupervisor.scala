package org.make.api

import akka.actor.Props
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._

object MakeBackoffSupervisor {

  private def name(childName: String): String = {
    s"$childName-backoff"
  }

  private def props(props: Props, name: String): Props = {
    BackoffSupervisor.props(
      Backoff.onStop(props, childName = name, minBackoff = 3.seconds, maxBackoff = 30.seconds, randomFactor = 0.2)
    )
  }

  def propsAndName(childProps: Props, childName: String): (Props, String) = {
    (props(childProps, childName), name((childName)))
  }

}
