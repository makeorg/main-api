package org.make.api.technical

import java.util.concurrent.ThreadPoolExecutor

import scala.concurrent.ExecutionContext

class MonitorableExecutionContext(executorService: ThreadPoolExecutor) extends ExecutionContext {
  private val executionContext = ExecutionContext.fromExecutorService(executorService)

  override def execute(runnable: Runnable): Unit = executionContext.execute(runnable)
  override def reportFailure(cause: Throwable): Unit = executionContext.reportFailure(cause)

  def activeTasks: Int = executorService.getActiveCount
  def currentTasks: Int = executorService.getPoolSize
  def maxTasks: Int = executorService.getMaximumPoolSize
  def waitingTasks: Int = executorService.getQueue.size()
}
