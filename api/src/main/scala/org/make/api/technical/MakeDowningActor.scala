package org.make.api.technical

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import de.heikoseeberger.constructr.coordination.Coordination
import org.make.api.technical.MakeDowningActor.AutoDown

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

class MakeDowningActor extends Actor with ActorLogging {

  val constructr = Coordination(context.system.name, context.system)

  override def preStart(): Unit = {
    context.system.scheduler.schedule(10.seconds, 10.seconds, self, AutoDown)
  }

  override def receive: Receive = {
    case AutoDown =>
      val cluster = Cluster(context.system)

      val members = cluster.state.members
      val actualMembers = constructr.getNodes()

      actualMembers.onComplete {
        case Success(nodes) =>
          members.foreach { member =>
            if (!nodes.contains(member.uniqueAddress.address)) {
              log.warning(
                "Downing node {} since it is no longer present in coordination",
                member.uniqueAddress.address.toString
              )
              cluster.down(member.uniqueAddress.address)
            }
          }

        case Failure(e) =>
          log.error(e, "Error while retrieving nodes")
      }
  }
}

object MakeDowningActor {

  val name: String = "MakeDowningActor"
  val props: Props = Props[MakeDowningActor]

  case object AutoDown
}