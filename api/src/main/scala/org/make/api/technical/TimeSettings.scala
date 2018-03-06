package org.make.api.technical

import akka.util.Timeout
import scala.concurrent.duration.DurationInt

object TimeSettings {

  val defaultTimeout: Timeout = Timeout(5.seconds)

}
