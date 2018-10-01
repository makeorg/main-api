/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api

import akka.actor.Props
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration.DurationInt

object MakeBackoffSupervisor {

  private def name(childName: String): String = {
    s"$childName-backoff"
  }

  private def props(props: Props, name: String): Props = {
    val maxNrOfRetries = 50
    BackoffSupervisor.props(
      Backoff.onStop(
        props,
        childName = name,
        minBackoff = 3.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2,
        maxNrOfRetries = maxNrOfRetries
      )
    )
  }

  def propsAndName(childProps: Props, childName: String): (Props, String) = {
    (props(childProps, childName), name(childName))
  }

}
