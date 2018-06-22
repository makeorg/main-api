package org.make.api.technical

import java.util

import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.common.Cluster
import org.make.core.Sharded

import scala.collection.JavaConverters._

class MakePartitioner extends Partitioner {

  override def partition(topic: String,
                         key: scala.Any,
                         keyBytes: Array[Byte],
                         value: scala.Any,
                         valueBytes: Array[Byte],
                         cluster: Cluster): Int = {

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
