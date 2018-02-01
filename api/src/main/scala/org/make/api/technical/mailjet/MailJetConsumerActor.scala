package org.make.api.technical.mailjet

import java.util.concurrent.Executors

import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import com.sksamuel.avro4s.RecordFormat
import io.circe.Printer
import io.circe.syntax._
import org.make.api.extensions.MailJetConfigurationExtension
import org.make.api.technical.KafkaConsumerActor

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

class MailJetConsumerActor extends KafkaConsumerActor[SendEmail] with MailJetConfigurationExtension {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(MailJetProducerActor.topicKey)
  override protected val format: RecordFormat[SendEmail] = RecordFormat[SendEmail]
  val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
  private val httpThreads = 5
  implicit private val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(httpThreads))

  override def handleMessage(message: SendEmail): Future[Unit] = {

    Http(context.system)
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(s"${mailJetConfiguration.url}"),
          headers = immutable
            .Seq(Authorization(BasicHttpCredentials(mailJetConfiguration.apiKey, mailJetConfiguration.secretKey))),
          entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(SendMessages(message).asJson))
        )
      )
      .map { response =>
        log.info(s"sent email: $response")
      }

  }

  override val groupId = "send-email-consumer"
}

object MailJetConsumerActor {
  val props: Props = Props[MailJetConsumerActor]
  val name: String = "send-email-consumer"

}
