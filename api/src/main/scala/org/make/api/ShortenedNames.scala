package org.make.api

import scala.concurrent.ExecutionContext

/**
  * Created by francois on 5/9/17.
  */
trait ShortenedNames {
  type EC = ExecutionContext
  val EC = ExecutionContext
  val ECGlobal: EC = ExecutionContext.Implicits.global
}
