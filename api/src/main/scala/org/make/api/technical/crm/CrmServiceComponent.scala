/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.technical.crm

import java.net.{URL, URLEncoder}
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.persistence.query.EventEnvelope
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorAttributes, ActorMaterializer, OverflowStrategy, QueueOfferResult}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Printer
import io.circe.syntax._
import org.make.api
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.technical.ReadJournalComponent
import org.make.api.userhistory._
import org.make.core.user.{User, UserId}

import scala.collection.immutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

trait CrmService {
  def addUserToOptInList(user: User): Future[Unit]
  def removeUserFromOptInList(user: User): Future[Unit]
  def removeUserFromHardBounceList(user: User): Future[Unit]
  def removeUserFromUnsubscribeList(user: User): Future[Unit]
  def addUserToHardBounceList(user: User): Future[Unit]
  def addUserToUnsubscribeList(user: User): Future[Unit]
  def sendEmail(message: SendEmail): Future[Unit]
  def updateUserProperties(user: User): Future[Unit]

  def addUsersToOptInList(users: Seq[User]): Future[Unit]
  def addUsersToUnsubscribeList(users: Seq[User]): Future[Unit]
  def addUsersToHardBounceList(users: Seq[User]): Future[Unit]

  def getPropertiesFromUser(user: User): Future[Map[String, String]]
}
trait CrmServiceComponent {
  def crmService: CrmService
}

trait DefaultCrmServiceComponent extends CrmServiceComponent with StrictLogging {
  self: MailJetConfigurationComponent
    with ActorSystemComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent =>

  implicit val executionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10, new ThreadFactory {
      val counter = new AtomicInteger()
      override def newThread(runnable: Runnable): Thread =
        new Thread(runnable, "crm-batch-" + counter.getAndIncrement())
    }))

  lazy val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
  lazy val url = new URL(mailJetConfiguration.url)
  lazy val httpFlow: Flow[(HttpRequest, HttpRequest), (Try[HttpResponse], HttpRequest), NotUsed] = {
    Flow[(HttpRequest, HttpRequest)]
      .via(Http(actorSystem).cachedHostConnectionPoolHttps(host = url.getHost, port = 443))
  }
  private lazy val bufferSize = mailJetConfiguration.httpBufferSize

  lazy val queue: SourceQueueWithComplete[(HttpRequest, HttpRequest)] = Source
    .queue[(HttpRequest, HttpRequest)](bufferSize = bufferSize, OverflowStrategy.backpressure)
    .via(httpFlow)
    .withAttributes(ActorAttributes.dispatcher(api.mailJetDispatcher))
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
      uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contactslist/$listId/managecontact"),
      headers = immutable.Seq(authorization),
      entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(manageContact.asJson))
    )
    doHttpCall(request)
  }

  def manageContactListMailJetRequest(manageContactList: ManageManyContacts): Future[QueueOfferResult] = {
    val request =
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/managemanycontacts"),
        headers = immutable.Seq(authorization),
        entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(manageContactList.asJson))
      )
    doHttpCall(request)
  }

  def updateContactProperties(contactData: ContactData, email: String): Future[QueueOfferResult] = {
    val encodedEmail: String = URLEncoder.encode(email, "UTF-8")
    val request =
      HttpRequest(
        method = HttpMethods.PUT,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contactdata/$encodedEmail"),
        headers = immutable.Seq(authorization),
        entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(contactData.asJson))
      )
    doHttpCall(request)
  }

  def sendEmailMailJetRequest(message: SendEmail): Future[QueueOfferResult] = {
    val request: HttpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(s"${mailJetConfiguration.url}/v3.1/send"),
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
      getPropertiesFromUser(user).flatMap { properties =>
        manageContactMailJetRequest(
          listId = mailJetConfiguration.optInListId,
          manageContact = ManageContact(
            user.email,
            user.fullName.getOrElse(user.email),
            action = ManageContactAction.AddNoForce,
            properties = Some(properties)
          )
        ).map { queueOfferResult =>
          logQueueOfferResult(queueOfferResult, "Add single to optin list")
        }
      }
    }

    override def removeUserFromOptInList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.optInListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.Remove)
      ).map { queueOfferResult =>
        logQueueOfferResult(queueOfferResult, "Remove single from optin list")
      }
    }

    override def addUserToHardBounceList(user: User): Future[Unit] = {
      getPropertiesFromUser(user).flatMap { properties =>
        manageContactMailJetRequest(
          listId = mailJetConfiguration.hardBounceListId,
          manageContact = ManageContact(
            user.email,
            user.fullName.getOrElse(user.email),
            action = ManageContactAction.AddNoForce,
            properties = Some(properties)
          )
        ).map { queueOfferResult =>
          logQueueOfferResult(queueOfferResult, "Add single to hardbounce list")
        }
      }
    }

    override def addUserToUnsubscribeList(user: User): Future[Unit] = {
      getPropertiesFromUser(user).flatMap { properties =>
        manageContactMailJetRequest(
          listId = mailJetConfiguration.unsubscribeListId,
          manageContact = ManageContact(
            user.email,
            user.fullName.getOrElse(user.email),
            action = ManageContactAction.AddNoForce,
            properties = Some(properties)
          )
        ).map { queueOfferResult =>
          logQueueOfferResult(queueOfferResult, "Add single to unsubscribe list")
        }
      }
    }

    override def sendEmail(message: SendEmail): Future[Unit] = {
      sendEmailMailJetRequest(message = message).map { queueOfferResult =>
        logQueueOfferResult(queueOfferResult, "Sent email")
      }
    }

    override def removeUserFromHardBounceList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.hardBounceListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.Remove)
      ).map { queueOfferResult =>
        logQueueOfferResult(queueOfferResult, "Remove from hardbounce list")
      }
    }

    override def removeUserFromUnsubscribeList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.unsubscribeListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.Remove)
      ).map { queueOfferResult =>
        logQueueOfferResult(queueOfferResult, "Remove from unsubscribe list")
      }
    }

    override def addUsersToOptInList(users: Seq[User]): Future[Unit] = {
      if (users.isEmpty) {
        Future.successful {}
      }

      val properties: Future[Map[UserId, Map[String, String]]] = Future
        .traverse(users) { user =>
          getPropertiesFromUser(user).map { properties =>
            user.userId -> properties
          }
        }
        .map(_.toMap)

      properties.map { properties =>
        manageContactListMailJetRequest(
          manageContactList = ManageManyContacts(
            contacts = users.map { user =>
              Contact(
                email = user.email,
                name = user.fullName.getOrElse(user.email),
                properties = properties.get(user.userId)
              )
            },
            contactList = Seq(
              ContactList(mailJetConfiguration.hardBounceListId, ManageContactAction.Remove),
              ContactList(mailJetConfiguration.unsubscribeListId, ManageContactAction.Remove),
              ContactList(mailJetConfiguration.optInListId, ManageContactAction.AddNoForce)
            )
          )
        ).map { queueOfferResult =>
          logQueueOfferResult(queueOfferResult, "Add to optin list")
        }

      }
    }

    override def addUsersToUnsubscribeList(users: Seq[User]): Future[Unit] = {
      val properties: Future[Map[UserId, Map[String, String]]] = Future
        .traverse(users) { user =>
          getPropertiesFromUser(user).map { properties =>
            user.userId -> properties
          }
        }
        .map(_.toMap)

      properties.map { properties =>
        manageContactListMailJetRequest(
          manageContactList = ManageManyContacts(
            contacts = users.map { user =>
              Contact(
                email = user.email,
                name = user.fullName.getOrElse(user.email),
                properties = properties.get(user.userId)
              )
            },
            contactList = Seq(
              ContactList(mailJetConfiguration.hardBounceListId, ManageContactAction.Remove),
              ContactList(mailJetConfiguration.unsubscribeListId, ManageContactAction.AddNoForce),
              ContactList(mailJetConfiguration.optInListId, ManageContactAction.Remove)
            )
          )
        ).map { queueOfferResult =>
          logQueueOfferResult(queueOfferResult, "Add to unsubscribe list")
        }
      }
    }

    override def addUsersToHardBounceList(users: Seq[User]): Future[Unit] = {
      val properties: Future[Map[UserId, Map[String, String]]] = Future
        .traverse(users) { user =>
          getPropertiesFromUser(user).map { properties =>
            user.userId -> properties
          }
        }
        .map(_.toMap)

      properties.map { properties =>
        manageContactListMailJetRequest(
          manageContactList = ManageManyContacts(
            contacts = users.map { user =>
              Contact(
                email = user.email,
                name = user.fullName.getOrElse(user.email),
                properties = properties.get(user.userId)
              )
            },
            contactList = Seq(
              ContactList(mailJetConfiguration.hardBounceListId, ManageContactAction.AddNoForce),
              ContactList(mailJetConfiguration.unsubscribeListId, ManageContactAction.Remove),
              ContactList(mailJetConfiguration.optInListId, ManageContactAction.Remove)
            )
          )
        ).map { queueOfferResult =>
          logQueueOfferResult(queueOfferResult, "Add to hardbounce list")
        }

      }
    }

    override def updateUserProperties(user: User): Future[Unit] = {

      getPropertiesFromUser(user).flatMap { properties =>
        val contactData = properties.map {
          case (name, value) => ContactProperty(name = name, value = value)
        }.toSeq

        updateContactProperties(ContactData(data = contactData), user.email).map { queueOfferResult =>
          logQueueOfferResult(queueOfferResult, "Update user properties")
        }
      }

      Future.successful {}
    }

    override def getPropertiesFromUser(user: User): Future[Map[String, String]] = {

      val events: Source[EventEnvelope, NotUsed] =
        userJournal.currentEventsByPersistenceId(user.userId.value, 0, Long.MaxValue)
      implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

      val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - hh:mm")
      val onlyDayDateFormatter = DateTimeFormatter.ofPattern("dd")
      val dayDateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

      val userProperties: Future[UserProperties] = events.runFold(UserProperties()) {
        (accumulator: UserProperties, enveloppe: EventEnvelope) =>
          enveloppe.event match {
            case event: LogRegisterCitizenEvent =>
              accumulator.copy(
                accountCreationSource = event.requestContext.source,
                accountCreationOperation = event.requestContext.operationId.map(_.value),
                accountCreationCountry = event.requestContext.country.map(_.value),
                countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
                operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value)
              )
            case event: LogUserProposalEvent =>
              accumulator.copy(
                totalNumberProposals = accumulator.totalNumberProposals.map(_ + 1).orElse(Some(1)),
                lastCountryActivity = event.requestContext.country.map(_.value),
                lastLanguageActivity = event.requestContext.language.map(_.value),
                countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
                operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
                firstContributionDate = if (accumulator.firstContributionDate.isEmpty) {
                  Some(event.action.date.format(dateFormatter))
                } else {
                  accumulator.firstContributionDate
                },
                lastContributionDate = Some(event.action.date.format(dateFormatter)),
                activeCore = if (accumulator.activeCore.isEmpty && event.requestContext.source.contains("core")) {
                  Some(true)
                } else {
                  accumulator.activeCore
                },
                daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
                daysOfActivity30d = if (event.action.date.compareTo(ZonedDateTime.now().minusMonths(1)) == 1) {
                  accumulator.daysOfActivity30d ++ Some(event.action.date.format(onlyDayDateFormatter))
                } else {
                  accumulator.daysOfActivity30d
                },
                themes = if (event.requestContext.currentTheme.nonEmpty) {
                  accumulator.themes ++ event.requestContext.currentTheme.map(_.value)
                } else {
                  accumulator.themes
                }
              )
            case event: LogUserVoteEvent =>
              accumulator.copy(
                totalNumbervotes = accumulator.totalNumbervotes.map(_ + 1).orElse(Some(1)),
                lastContributionDate = Some(event.action.date.format(dateFormatter)),
                lastCountryActivity = event.requestContext.country.map(_.value),
                lastLanguageActivity = event.requestContext.language.map(_.value),
                countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
                operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
                activeCore = if (accumulator.activeCore.isEmpty && event.requestContext.source.contains("core")) {
                  Some(true)
                } else {
                  accumulator.activeCore
                },
                daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
                daysOfActivity30d = if (event.action.date.compareTo(ZonedDateTime.now().minusMonths(1)) == 1) {
                  accumulator.daysOfActivity30d ++ Some(event.action.date.format(onlyDayDateFormatter))
                } else {
                  accumulator.daysOfActivity30d
                },
                themes = if (event.requestContext.currentTheme.nonEmpty) {
                  accumulator.themes ++ event.requestContext.currentTheme.map(_.value)
                } else {
                  accumulator.themes
                }
              )
            case event: LogUserUnvoteEvent =>
              accumulator.copy(
                lastContributionDate = Some(event.action.date.format(dateFormatter)),
                lastCountryActivity = event.requestContext.country.map(_.value),
                lastLanguageActivity = event.requestContext.language.map(_.value),
                countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
                operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
                activeCore = if (accumulator.activeCore.isEmpty && event.requestContext.source.contains("core")) {
                  Some(true)
                } else {
                  accumulator.activeCore
                },
                daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
                daysOfActivity30d = if (event.action.date.compareTo(ZonedDateTime.now().minusMonths(1)) == 1) {
                  accumulator.daysOfActivity30d ++ Some(event.action.date.format(onlyDayDateFormatter))
                } else {
                  accumulator.daysOfActivity30d
                },
                themes = if (event.requestContext.currentTheme.nonEmpty) {
                  accumulator.themes ++ event.requestContext.currentTheme.map(_.value)
                } else {
                  accumulator.themes
                }
              )
            case event: LogUserQualificationEvent =>
              accumulator.copy(
                lastContributionDate = Some(event.action.date.format(dateFormatter)),
                lastCountryActivity = event.requestContext.country.map(_.value),
                lastLanguageActivity = event.requestContext.language.map(_.value),
                countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
                operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
                activeCore = if (accumulator.activeCore.isEmpty && event.requestContext.source.contains("core")) {
                  Some(true)
                } else {
                  accumulator.activeCore
                },
                daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
                daysOfActivity30d = if (event.action.date.compareTo(ZonedDateTime.now().minusMonths(1)) == 1) {
                  accumulator.daysOfActivity30d ++ Some(event.action.date.format(onlyDayDateFormatter))
                } else {
                  accumulator.daysOfActivity30d
                },
                themes = if (event.requestContext.currentTheme.nonEmpty) {
                  accumulator.themes ++ event.requestContext.currentTheme.map(_.value)
                } else {
                  accumulator.themes
                }
              )
            case event: LogUserUnqualificationEvent =>
              accumulator.copy(
                lastContributionDate = Some(event.action.date.format(dateFormatter)),
                lastCountryActivity = event.requestContext.country.map(_.value),
                lastLanguageActivity = event.requestContext.language.map(_.value),
                countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
                operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
                activeCore = if (accumulator.activeCore.isEmpty && event.requestContext.source.contains("core")) {
                  Some(true)
                } else {
                  accumulator.activeCore
                },
                daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
                daysOfActivity30d = if (event.action.date.compareTo(ZonedDateTime.now().minusMonths(1)) == 1) {
                  accumulator.daysOfActivity30d ++ Some(event.action.date.format(onlyDayDateFormatter))
                } else {
                  accumulator.daysOfActivity
                },
                themes = if (event.requestContext.currentTheme.nonEmpty) {
                  accumulator.themes ++ event.requestContext.currentTheme.map(_.value)
                } else {
                  accumulator.themes
                }
              )

            case _ => accumulator
          }
      }

      userProperties.map { userProperty =>
        Map[String, String](
          "UserId" -> user.userId.value,
          "Firstname" -> user.firstName.getOrElse(""),
          "Zipcode" -> user.profile.flatMap(_.postalCode).getOrElse(""),
          "Date_Of_Birth" -> user.profile.flatMap(_.dateOfBirth.map(_.toString)).getOrElse(""),
          "Email_Validation_Status" -> user.emailVerified.toString,
          "Email_Hardbounce_Status" -> user.isHardBounce.toString,
          "Unsubscribe_Status" -> user.profile
            .map(profile => (!profile.optInNewsletter).toString)
            .getOrElse("false"),
          "Account_Creation_Date" -> user.createdAt
            .map(dateFormatter.format(_))
            .getOrElse(""),
          "Account_creation_source" -> userProperty.accountCreationSource.getOrElse(""),
          "Account_Creation_Operation" -> userProperty.accountCreationOperation.getOrElse(""),
          "Account_Creation_Country" -> userProperty.accountCreationCountry.getOrElse(""),
          "Countries_activity" -> userProperty.countriesActivity.distinct.mkString(","),
          "Last_country_activity" -> userProperty.lastCountryActivity.getOrElse(""),
          "Last_language_activity" -> userProperty.lastLanguageActivity.getOrElse(""),
          "Total_Number_Proposals" -> userProperty.totalNumberProposals.map(_.toString).getOrElse(""),
          "Total_number_votes" -> userProperty.totalNumbervotes.map(_.toString).getOrElse(""),
          "First_Contribution_Date" -> userProperty.firstContributionDate.getOrElse(""),
          "Last_Contribution_Date" -> userProperty.lastContributionDate.getOrElse(""),
          "Operation_activity" -> userProperty.operationActivity.distinct.mkString(","),
          "Active_core" -> userProperty.activeCore.map(_.toString).getOrElse(""),
          "Days_of_Activity" -> userProperty.daysOfActivity.distinct.length.toString,
          "Days_of_Activity_30d" -> userProperty.daysOfActivity30d.distinct.length.toString,
          "Number_of_themes" -> userProperty.themes.distinct.length.toString,
          "User_type" -> {
            if (user.isOrganisation) { "B2B" } else { "B2C" }
          }
        ).filter {
          case (_, value) if value.isEmpty => false
          case _                           => true
        }
      }

    }
  }

  def logQueueOfferResult(queueOfferResult: QueueOfferResult, operationName: String): Unit = {
    queueOfferResult match {
      case QueueOfferResult.Enqueued    => logger.debug(s"$operationName: element has been consumed")
      case QueueOfferResult.Dropped     => logger.error(s"$operationName: element has been ignored because of backpressure")
      case QueueOfferResult.QueueClosed => logger.error(s"$operationName: the queue upstream has terminated")
      case QueueOfferResult.Failure(e) =>
        logger.error(s"$operationName: the queue upstream has failed with an exception (${e.getMessage})")
    }
  }

}

final case class UserProperties(accountCreationSource: Option[String] = None,
                                accountCreationOperation: Option[String] = None,
                                accountCreationCountry: Option[String] = None,
                                countriesActivity: Seq[String] = Seq.empty,
                                lastCountryActivity: Option[String] = None,
                                lastLanguageActivity: Option[String] = None,
                                totalNumberProposals: Option[Int] = None,
                                totalNumbervotes: Option[Int] = None,
                                firstContributionDate: Option[String] = None,
                                lastContributionDate: Option[String] = None,
                                operationActivity: Seq[String] = Seq.empty,
                                activeCore: Option[Boolean] = None,
                                daysOfActivity: Seq[String] = Seq.empty,
                                daysOfActivity30d: Seq[String] = Seq.empty,
                                themes: Seq[String] = Seq.empty,
                                userType: Option[String] = None)
