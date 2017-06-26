package org.make.api.extensions

import scala.concurrent.ExecutionContext

trait MakeDBExecutionContextComponent {
  def readExecutionContext: ExecutionContext
  def writeExecutionContext: ExecutionContext
}
