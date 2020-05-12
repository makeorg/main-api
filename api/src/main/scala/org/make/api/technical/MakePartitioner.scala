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

package org.make.api.technical

import java.util

import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.common.Cluster
import org.make.core.Sharded

import scala.jdk.CollectionConverters._

class MakePartitioner extends Partitioner {

  override def partition(
    topic: String,
    key: scala.Any,
    keyBytes: Array[Byte],
    value: scala.Any,
    valueBytes: Array[Byte],
    cluster: Cluster
  ): Int = {

    val partitions = cluster.availablePartitionsForTopic(topic).asScala.map(_.partition()).sorted

    val obj = (Option(key), Option(value)) match {
      case (_, Some(v: Sharded)) => v.id
      case (Some(k), _)          => k
      case (_, Some(other))      => other
      case _                     => "should not happen"
    }

    partitions((obj.hashCode % partitions.size + partitions.size) % partitions.size)

  }

  override def close(): Unit = {}

  override def configure(configs: util.Map[String, _]): Unit = {}
}
