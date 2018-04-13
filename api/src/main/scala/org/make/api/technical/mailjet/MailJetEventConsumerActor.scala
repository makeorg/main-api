package org.make.api.technical.mailjet

import java.time.ZonedDateTime

import akka.actor.{ActorLogging, Props}
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.technical.KafkaConsumerActor
import org.make.api.user.UserService
import org.make.core.user.MailingErrorLog
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class MailJetEventConsumerActor(userService: UserService)
    extends KafkaConsumerActor[MailJetEventWrapper]
    with KafkaConfigurationExtension
    with ActorLogging {
  override protected val kafkaTopic: String = kafkaConfiguration.topics(MailJetCallbackProducerActor.topicKey)
  override protected val format: RecordFormat[MailJetEventWrapper] = RecordFormat[MailJetEventWrapper]
  override def groupId: String = "mailJet-event-consumer"

  override def handleMessage(message: MailJetEventWrapper): Future[Unit] = {
    message.event.fold(ToMailJetEvent) match {
      case event: MailJetBounceEvent      => handleBounceEvent(event, message.date).map(_ => {})
      case event: MailJetBlockedEvent     => doNothing(event)
      case event: MailJetSpamEvent        => handleSpamEvent(event).map(_ => {})
      case event: MailJetUnsubscribeEvent => handleUnsubscribeEvent(event).map(_ => {})
      case event                          => doNothing(event)
    }
  }

  def handleBounceEvent(event: MailJetBounceEvent, date: ZonedDateTime): Future[Boolean] = {
    for {
      resultUpdateBounce <- userService.updateIsHardBounce(email = event.email, isHardBounce = event.hardBounce)
      resultUpdateError  <- registerMailingError(email = event.email, error = event.error, date = date)
    } yield resultUpdateBounce && resultUpdateError
  }

  def handleSpamEvent(event: MailJetSpamEvent): Future[Boolean] = {
    userService.updateOptInNewsletter(email = event.email, optInNewsletter = false)
  }

  def handleUnsubscribeEvent(event: MailJetUnsubscribeEvent): Future[Boolean] = {
    userService.updateOptInNewsletter(email = event.email, optInNewsletter = false)
  }

  private def registerMailingError(email: String, error: Option[MailJetError], date: ZonedDateTime): Future[Boolean] = {
    error match {
      case None => Future.successful(false)
      case Some(error) =>
        userService.updateLastMailingError(
          email = email,
          lastMailingError = Some(MailingErrorLog(error = error.name, date = date))
        )
    }
  }
}

object MailJetEventConsumerActor {
  def props(userService: UserService): Props = Props(new MailJetEventConsumerActor(userService))
  val name: String = "mailJet-event-consumer"
}
