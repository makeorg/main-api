package org.make.api.technical.crm

import java.net.URL
import java.time.format.DateTimeFormatter

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Printer
import io.circe.syntax._
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.userhistory.UserHistoryActor.UserHistory
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.user.User

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

trait CrmService {
  def addUserToOptInList(user: User): Future[Unit]
  def removeUserFromOptInList(user: User): Future[Unit]
  def removeUserFromHardBounceList(user: User): Future[Unit]
  def removeUserFromUnsubscribeList(user: User): Future[Unit]
  def addUserToHardBounceList(user: User): Future[Unit]
  def addUserToUnsubscribeList(user: User): Future[Unit]
  def sendEmail(message: SendEmail): Future[Unit]

  def addUsersToOptInList(users: Seq[User]): Future[Unit]
  def addUsersToUnsubscribeList(users: Seq[User]): Future[Unit]
  def addUsersToHardBounceList(users: Seq[User]): Future[Unit]
}
trait CrmServiceComponent {
  def crmService: CrmService
}

trait DefaultCrmServiceComponent extends CrmServiceComponent with StrictLogging {
  self: MailJetConfigurationComponent with ActorSystemComponent with UserHistoryCoordinatorServiceComponent =>

  lazy val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
  lazy val url = new URL(mailJetConfiguration.url)
  lazy val httpFlow: Flow[(HttpRequest, HttpRequest), (Try[HttpResponse], HttpRequest), NotUsed] = {
    Flow[(HttpRequest, HttpRequest)]
      .via(Http(actorSystem).cachedHostConnectionPoolHttps(host = url.getHost, port = 443))
  }
  private val bufferSize = mailJetConfiguration.httpBufferSize

  lazy val queue: SourceQueueWithComplete[(HttpRequest, HttpRequest)] = Source
    .queue[(HttpRequest, HttpRequest)](bufferSize = bufferSize, OverflowStrategy.backpressure)
    .via(httpFlow)
    .toMat(Sink.foreach({
      case ((Success(r), request)) =>
        if (r.status.isSuccess()) {
          logger.debug(s"CRM HTTP request succeed. response: ${r.toString()} request: {${request.toString}}")
        } else {
          logger.error(s"CRM HTTP request failed. status: ${r.status.toString}  body:${r.entity
            .toStrict(3.seconds)(ActorMaterializer()(actorSystem))
            .map { _.data }
            .map(_.utf8String)} request:{${request.toString}}")
        }
      case ((Failure(e), request)) => logger.warn(s"failed request: ${request.toString}", e)

    }))(Keep.left)
    .run()(ActorMaterializer()(actorSystem))

  private lazy val authorization = Authorization(
    BasicHttpCredentials(mailJetConfiguration.campaignApiKey, mailJetConfiguration.campaignSecretKey)
  )

  def manageContactMailJetRequest(listId: String, manageContact: ManageContact): Future[QueueOfferResult] = {
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(s"${mailJetConfiguration.url}/contactslist/$listId/managecontact"),
      headers = immutable.Seq(authorization),
      entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(manageContact.asJson))
    )
    doHttpCall(request)
  }

  def manageContactListMailJetRequest(manageContactList: ManageManyContacts): Future[QueueOfferResult] = {
    val request =
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"${mailJetConfiguration.url}/contact/managemanycontacts"),
        headers = immutable.Seq(authorization),
        entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(manageContactList.asJson))
      )
    doHttpCall(request)
  }

  def sendEmailMailJetRequest(message: SendEmail): Future[QueueOfferResult] = {
    val request: HttpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(s"${mailJetConfiguration.url}/send"),
      headers = immutable
        .Seq(Authorization(BasicHttpCredentials(mailJetConfiguration.apiKey, mailJetConfiguration.secretKey))),
      entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(SendMessages(message).asJson))
    )
    doHttpCall(request)
  }

  private def doHttpCall(request: HttpRequest): Future[QueueOfferResult] = {
    queue.offer((request, request))
  }

  override def crmService: CrmService = new CrmService {

    override def addUserToOptInList(user: User): Future[Unit] = {

      manageContactMailJetRequest(
        listId = mailJetConfiguration.optInListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.AddNoForce)
      ).map { response =>
        logger.info(s"adding user to optin list: $response")
      }
    }

    override def removeUserFromOptInList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.optInListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.Remove)
      ).map { response =>
        logger.info(s"remove user to optin list: $response")
      }
    }

    override def addUserToHardBounceList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.hardBounceListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.AddNoForce)
      ).map { response =>
        logger.info(s"adding user to hard bounce list: $response")
      }
    }

    override def addUserToUnsubscribeList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.unsubscribeListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.AddNoForce)
      ).map { response =>
        logger.info(s"adding user to unsubscribe list: $response")
      }
    }

    override def sendEmail(message: SendEmail): Future[Unit] = {
      sendEmailMailJetRequest(message = message).map { response =>
        logger.info(s"sent email: $response")
      }
    }

    override def removeUserFromHardBounceList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.hardBounceListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.Remove)
      ).map { response =>
        logger.info(s"remove user from hardbounce list: $response")
      }
    }

    override def removeUserFromUnsubscribeList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.unsubscribeListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.Remove)
      ).map { response =>
        logger.info(s"remove user from unsubscribe list: $response")
      }
    }

    override def addUsersToOptInList(users: Seq[User]): Future[Unit] = {
      manageContactListMailJetRequest(
        manageContactList = ManageManyContacts(
          contacts = users.map { user =>
            Contact(
              email = user.email,
              name = user.fullName.getOrElse(user.email),
              properties = getPropertiesFromUser(user)
            )
          },
          contactList = Seq(
            ContactList(mailJetConfiguration.hardBounceListId, ManageContactAction.Remove),
            ContactList(mailJetConfiguration.unsubscribeListId, ManageContactAction.Remove),
            ContactList(mailJetConfiguration.optInListId, ManageContactAction.AddNoForce)
          )
        )
      ).map { response =>
        logger.info(s"add users to optin list: $response")
      }

      Future.successful {}
    }

    override def addUsersToUnsubscribeList(users: Seq[User]): Future[Unit] = {
      manageContactListMailJetRequest(
        manageContactList = ManageManyContacts(
          contacts = users.map { user =>
            Contact(email = user.email, name = user.fullName.getOrElse(user.email))
          },
          contactList = Seq(
            ContactList(mailJetConfiguration.hardBounceListId, ManageContactAction.Remove),
            ContactList(mailJetConfiguration.unsubscribeListId, ManageContactAction.AddNoForce),
            ContactList(mailJetConfiguration.optInListId, ManageContactAction.Remove)
          )
        )
      ).map { response =>
        logger.info(s"add users to unsubscribe list: $response")
      }

      Future.successful {}
    }

    override def addUsersToHardBounceList(users: Seq[User]): Future[Unit] = {
      manageContactListMailJetRequest(
        manageContactList = ManageManyContacts(
          contacts = users.map { user =>
            Contact(email = user.email, name = user.fullName.getOrElse(user.email))
          },
          contactList = Seq(
            ContactList(mailJetConfiguration.hardBounceListId, ManageContactAction.AddNoForce),
            ContactList(mailJetConfiguration.unsubscribeListId, ManageContactAction.Remove),
            ContactList(mailJetConfiguration.optInListId, ManageContactAction.Remove)
          )
        )
      ).map { response =>
        logger.info(s"add users to hard bounce list: $response")
      }

      Future.successful {}
    }
  }

  private def getPropertiesFromUser(user: User): Option[Map[String, String]] = {

    val userHistory: Future[UserHistory] = userHistoryCoordinatorService.userHistory(user.userId)
    userHistory.map(_.events.map(_.action.actionType))
    Some(
      Map(
        "UserID " -> user.userId.value,
        "Firstname" -> user.firstName.getOrElse(""),
        "Zipcode" -> user.profile.flatMap(_.postalCode).getOrElse(""),
        "Date_Of_Birth" -> user.profile.flatMap(_.dateOfBirth.map(_.toString)).getOrElse(""),
        "Email_Validation_Status" -> user.verified.toString,
        "Email_Hardbounce_Status" -> user.isHardBounce.toString,
        "Unsubscribe_Status" -> user.profile.map(_.optInNewsletter.toString).getOrElse(""),
        "Account_Creation_Date" -> user.createdAt
          .map(DateTimeFormatter.ofPattern("dd/MM/yyyy - hh:mm").format(_))
          .getOrElse(""),
        "Account_Creation_Source" -> "", // toDo
        "Account_Creation_Operation" -> "", // toDo
        "Account_Creation_Country" -> user.country,
        "Countries_activity" -> "", // toDo
        "Last_country_activity" -> "", // toDo
        "Last_language_activity" -> "", // toDo
        "Total_Number_Proposals" -> "", // toDo
        "Total number votes" -> "", // toDo
        "First_Contribution_Date" -> "", // toDo
        "Last_Contribution_Date" -> "", // toDo
        "Operation_activity" -> "", // toDo
        "Active_core" -> "", // toDo
        "Days_of_Activity" -> "", // toDo
        "Days_of_Activity_30d" -> "", // toDo
        "Number_of_themes" -> "" // toDo
      )
    )
  }
}
