package org.make.api.migrations

import org.make.api.MakeApi

import scala.concurrent.Future

trait Migration {
  def initialize(api: MakeApi): Future[Unit]
  def migrate(api: MakeApi): Future[Unit]
  def runInProduction: Boolean
}
