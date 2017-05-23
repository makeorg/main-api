package org.make.api.citizen

import java.time.LocalDate

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.cluster.Cluster
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.make.core.citizen._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}

class CitizenActorTest extends TestKit(CitizenActorTest.actorSystem)
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with ImplicitSender {

  var coordinator: ActorRef = _

  val mainCitizenId = CitizenId("1234")

  override protected def beforeAll(): Unit = super.beforeAll()


  override protected def beforeEach(): Unit = {
    coordinator = system.actorOf(CitizenCoordinator.props, CitizenCoordinator.name)
  }

  override protected def afterEach(): Unit = {
    coordinator ! PoisonPill
  }

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)


  "Register a citizen" should {
    "Initialize the state if it was empty" in {

      coordinator ! GetCitizen(mainCitizenId)
      expectMsg(None)

      coordinator ! RegisterCommand(
        citizenId = mainCitizenId,
        email = "robb.stark@make.org",
        dateOfBirth = LocalDate.parse("1970-01-01"),
        firstName = "Robb",
        lastName = "Stark"
      )

      expectMsg(
        Some(
          Citizen(
            citizenId = mainCitizenId,
            email = "robb.stark@make.org",
            dateOfBirth = LocalDate.parse("1970-01-01"),
            firstName = "Robb",
            lastName = "Stark"
          )
        )
      )

      coordinator ! GetCitizen(mainCitizenId)

      expectMsg(
        Some(
          Citizen(
            citizenId = mainCitizenId,
            email = "robb.stark@make.org",
            dateOfBirth = LocalDate.parse("1970-01-01"),
            firstName = "Robb",
            lastName = "Stark"
          )
        )
      )

      coordinator ! KillCitizenShard(mainCitizenId)

      Thread.sleep(100)

      coordinator ! GetCitizen(mainCitizenId)

      expectMsg(
        Some(
          Citizen(
            citizenId = mainCitizenId,
            email = "robb.stark@make.org",
            dateOfBirth = LocalDate.parse("1970-01-01"),
            firstName = "Robb",
            lastName = "Stark"
          )
        )
      )
    }
  }


}

object CitizenActorTest {

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
      |
      |  test.single-expect-default = "5 seconds"
      |}
    """.stripMargin

  def actorSystem: ActorSystem = {
    val system = ActorSystem("test-system", ConfigFactory.parseString(configuration))
    val cluster = Cluster(system)
    cluster.join(cluster.selfAddress)
    system
  }

}


