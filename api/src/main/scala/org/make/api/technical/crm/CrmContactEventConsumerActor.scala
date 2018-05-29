package org.make.api.technical.crm

import java.util.concurrent.Executors

import akka.NotUsed
import akka.actor.{ActorLogging, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.sksamuel.avro4s.RecordFormat
import org.make.api.extensions.{KafkaConfigurationExtension, MailJetConfiguration, MailJetConfigurationComponent}
import org.make.api.technical.KafkaConsumerActor
import org.make.api.technical.crm.PublishedCrmContactEvent._
import org.make.api.user.UserService
import org.make.core.user.User

import scala.concurrent.{ExecutionContext, Future}

class CrmContactEventConsumerActor(userService: UserService, crmService: CrmService)
    extends KafkaConsumerActor[CrmContactEventWrapper]
    with KafkaConfigurationExtension
    with MailJetConfigurationComponent
    with ActorLogging {

  override lazy val mailJetConfiguration: MailJetConfiguration = MailJetConfiguration(context.system)
  override protected val kafkaTopic: String = kafkaConfiguration.topics(CrmContactProducerActor.topicKey)
  override protected val format: RecordFormat[CrmContactEventWrapper] = RecordFormat[CrmContactEventWrapper]
  override def groupId: String = "crm-contact-event-consumer"
  private val httpThreads = 5
  implicit private val executionContext: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(httpThreads))
  private val batchSize: Int = mailJetConfiguration.userListBatchSize

  implicit private val mat: ActorMaterializer = ActorMaterializer()(context.system)

  override def handleMessage(message: CrmContactEventWrapper): Future[Unit] = {
    message.event.fold(ToCrmContactEvent) match {
      case event: CrmContactNew              => handleNewContactEvent(event)
      case event: CrmContactHardBounce       => handleHardBounceEvent(event)
      case event: CrmContactUnsubscribe      => handleUnsubscribeEvent(event)
      case event: CrmContactSubscribe        => handleSubscribeEvent(event)
      case event: CrmContactUpdateProperties => handleUpdatePropertiesEvent(event)
      case _: CrmContactListSync             => handleContactListSyncEvent()
      case event                             => doNothing(event)
    }
  }

  private def handleUpdatePropertiesEvent(event: CrmContactUpdateProperties): Future[Unit] = {
    userService
      .getUser(event.id)
      .map(_.map { user =>
        crmService.updateUserProperties(user)
      })
  }

  private def handleNewContactEvent(event: CrmContactNew): Future[Unit] = {
    userService
      .getUser(event.id)
      .map(_.map { user =>
        user.profile match {
          case Some(profile) if !profile.optInNewsletter => crmService.addUserToUnsubscribeList(user)
          case _                                         => crmService.addUserToOptInList(user)
        }
      })
  }

  private def handleHardBounceEvent(event: CrmContactHardBounce): Future[Unit] = {
    userService
      .getUser(event.id)
      .flatMap(_.map { user =>
        val removeFromOptin: Future[Unit] = crmService.removeUserFromOptInList(user)
        val removeFromUnsubscribe: Future[Unit] = crmService.removeUserFromUnsubscribeList(user)
        val addToBounceList: Future[Unit] = crmService.addUserToHardBounceList(user)
        for {
          _ <- removeFromOptin
          _ <- removeFromUnsubscribe
          _ <- addToBounceList
        } yield {}
      }.getOrElse(Future.successful {}))
  }

  private def handleUnsubscribeEvent(event: CrmContactUnsubscribe): Future[Unit] = {
    userService
      .getUser(event.id)
      .flatMap(_.map { user =>
        val removeFromOptin: Future[Unit] = crmService.removeUserFromOptInList(user)
        val addToSubscribe: Future[Unit] = crmService.addUserToUnsubscribeList(user)
        for {
          _ <- removeFromOptin
          _ <- addToSubscribe
        } yield {}
      }.getOrElse(Future.successful {}))
  }

  private def handleSubscribeEvent(event: CrmContactSubscribe): Future[Unit] = {
    userService
      .getUser(event.id)
      .flatMap(_.map { user =>
        val removeFromUnsubscribe: Future[Unit] = crmService.removeUserFromUnsubscribeList(user)
        val addToOptIn = if (!user.isHardBounce) {
          crmService.addUserToOptInList(user)
        } else {
          Future.successful {}
        }

        for {
          _ <- removeFromUnsubscribe
          _ <- addToOptIn
        } yield {}
      }.getOrElse(Future.successful {}))
  }

  private def handleContactListSyncEvent(): Future[Unit] = {
    val getHardBounceUsers: Int => Future[Seq[User]] = (page: Int) =>
      userService.getUsersWithHardBounce(limit = batchSize, page = page)
    val getOptOutUsers: Int => Future[Seq[User]] = (page: Int) =>
      userService.getOptOutUsers(limit = batchSize, page = page)
    val getOptInUsers: Int => Future[Seq[User]] = (page: Int) =>
      userService.getOptInUsers(limit = batchSize, page = page)

    asyncPageToPageSource(getOptOutUsers)
      .mapAsync(1)(crmService.addUsersToUnsubscribeList)
      .runForeach(_ => {})
    asyncPageToPageSource(getHardBounceUsers)
      .mapAsync(1)(crmService.addUsersToHardBounceList)
      .runForeach(_ => {})
    asyncPageToPageSource(getOptInUsers)
      .mapAsync(1)(crmService.addUsersToOptInList)
      .runForeach(_ => {})

    Future.successful {}
  }

  private def asyncPageToPageSource(pageFunc: Int => Future[Seq[User]]): Source[Seq[User], NotUsed] = {
    Source.unfoldAsync(1) { page =>
      val futureUsers: Future[Seq[User]] = pageFunc(page)
      futureUsers.map { users =>
        if (users.isEmpty) {
          None
        } else {
          Some((page + 1, users))
        }
      }

    }
  }
}

object CrmContactEventConsumerActor {
  def props(userService: UserService, crmService: CrmService): Props =
    Props(new CrmContactEventConsumerActor(userService, crmService))
  val name: String = "crm-contact-events-consumer"
}
