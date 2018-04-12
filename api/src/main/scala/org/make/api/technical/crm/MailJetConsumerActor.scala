package org.make.api.technical.crm

import java.util.concurrent.Executors

import akka.actor.Props
import com.sksamuel.avro4s.RecordFormat
import io.circe.Printer
import org.make.api.extensions.MailJetConfigurationExtension
import org.make.api.technical.KafkaConsumerActor

import scala.concurrent.{ExecutionContext, Future}

class MailJetConsumerActor(crmService: CrmService)
    extends KafkaConsumerActor[SendEmail]
    with MailJetConfigurationExtension {

  override protected lazy val kafkaTopic: String = kafkaConfiguration.topics(MailJetProducerActor.topicKey)
  override protected val format: RecordFormat[SendEmail] = RecordFormat[SendEmail]
  val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
  private val httpThreads = 5
  implicit private val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(httpThreads))

  override def handleMessage(message: SendEmail): Future[Unit] = {
    crmService.sendEmail(message).map { response =>
      log.info(s"sent email: $response")
    }
  }

  override val groupId = "send-email-consumer"
}

object MailJetConsumerActor {
  def props(crmService: CrmService): Props = Props(new MailJetConsumerActor(crmService))
  val name: String = "send-email-consumer"

}
