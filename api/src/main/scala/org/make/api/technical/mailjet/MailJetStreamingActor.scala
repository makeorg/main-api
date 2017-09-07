package org.make.api.technical.mailjet

import java.util.concurrent.Executors

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.pattern.{Backoff, BackoffSupervisor}
import akka.stream.ActorMaterializer
import org.make.api.extensions.MailJetConfigurationExtension

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class MailJetStreamingActor extends Actor with ActorLogging with MailJetConfigurationExtension {

  override def preStart(): Unit = {
    implicit val executor: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))

    val futureFlow = MailJet.run(mailJetConfiguration.url, mailJetConfiguration.apiKey, mailJetConfiguration.secretKey)(
      context.system,
      ActorMaterializer(),
      executor
    )

    futureFlow.onComplete {
      case Success(result) => log.debug("Stream processed: {}", result)
      case Failure(e) =>
        log.error("Error in proposal streaming: {}", e)
        self ! PoisonPill
    }
  }

  override def receive: Receive = {
    case event => log.debug(s"received $event")
  }
}

object MailJetStreamingActor {
  val name: String = "mailjet-consumer-backoff"
  val props: Props = BackoffSupervisor.props(
    Backoff
      .onStop(
        childProps = Props[MailJetStreamingActor],
        childName = "mailjet-streaming-consumer",
        minBackoff = 3.seconds,
        maxBackoff = 20.minutes,
        randomFactor = 0.2
      )
  )

}
