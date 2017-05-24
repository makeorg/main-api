package org.make.api

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}

class ShardingActorTest(actorSystem: ActorSystem = TestHelper.defaultActorSystem()) extends TestKit(actorSystem)
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ImplicitSender

object TestHelper {
  val configuration: String =
    """
      |akka {
      |
      |  actor {
      |    provider = "akka.cluster.ClusterActorRefProvider"
      |  }
      |
      |  persistence {
      |    journal.plugin = "inmemory-journal"
      |    snapshot-store.plugin = "inmemory-snapshot-store"
      |  }
      |
      |  cluster {
      |
      |     sharding {
      |      guardian-name = sharding
      |      remember-entities = on
      |      state-store-mode = persistence
      |
      |      snapshot-plugin-id = "inmemory-snapshot-store"
      |      journal-plugin-id = "inmemory-journal"
      |    }
      |  }
      |}
    """.stripMargin

  def defaultActorSystem(conf: String = configuration): ActorSystem = {
    val system = ActorSystem("test-system", ConfigFactory.parseString(conf))
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    system
  }

}