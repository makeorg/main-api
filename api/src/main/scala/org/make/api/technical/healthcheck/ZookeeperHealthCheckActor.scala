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

package org.make.api.technical.healthcheck

import akka.actor.Props
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.RetryNTimes
import org.apache.curator.utils.CloseableUtils
import org.apache.zookeeper.CreateMode

import scala.concurrent.{ExecutionContext, Future}

class ZookeeperHealthCheckActor(healthCheckExecutionContext: ExecutionContext) extends HealthCheck {

  override val techno: String = "zookeeper"

  override def healthCheck(): Future[String] = {

    Future {
      val connectString: String = context.system.settings.config.getString("make-api.zookeeper.url")
      val client: CuratorFramework = CuratorFrameworkFactory.newClient(connectString, new RetryNTimes(3, 500))
      val path: String = "/ephemeral_path"
      val data: String = System.currentTimeMillis.toString

      client.start()
      val realPath = client.create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(path, data.getBytes("utf-8"))
      val result = new String(client.getData.forPath(realPath), "utf-8")
      client.delete().forPath(realPath)

      CloseableUtils.closeQuietly(client)
      if (result != data) {
        log.warning(s"""Unexpected result in zookeeper health check: expected "$result" but got "$data"""")
        "NOK"
      } else {
        "OK"
      }
    }(healthCheckExecutionContext)
  }
}

object ZookeeperHealthCheckActor extends HealthCheckActorDefinition {
  override val name: String = "zookeeper-health-check"

  override def props(healthCheckExecutionContext: ExecutionContext): Props =
    Props(new ZookeeperHealthCheckActor(healthCheckExecutionContext))
}
