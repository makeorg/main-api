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
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
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
import org.make.api.operation.OperationServiceComponent
import org.make.api.technical.ReadJournalComponent
import org.make.api.userhistory._
import org.make.core.DateHelper
import org.make.core.operation.Operation
import org.make.core.user.{User, UserId}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
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

  def getPropertiesFromUser(user: User): Future[ContactProperties]
}
trait CrmServiceComponent {
  def crmService: CrmService
}

trait DefaultCrmServiceComponent extends CrmServiceComponent with StrictLogging {
  self: MailJetConfigurationComponent
    with ActorSystemComponent
    with OperationServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent =>

  private val poolSize: Int = 10
  implicit val executionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(poolSize, new ThreadFactory {
      val counter = new AtomicInteger()
      override def newThread(runnable: Runnable): Thread =
        new Thread(runnable, "crm-batch-" + counter.getAndIncrement())
    }))

  lazy val printer: Printer = Printer.noSpaces.copy(dropNullValues = false)
  lazy val url = new URL(mailJetConfiguration.url)
  val httpPort: Int = 443

  lazy val httpFlow
    : Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), Http.HostConnectionPool] =
    Http(actorSystem).cachedHostConnectionPoolHttps[Promise[HttpResponse]](host = url.getHost, port = httpPort)

  private lazy val bufferSize = mailJetConfiguration.httpBufferSize

  lazy val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] = Source
    .queue[(HttpRequest, Promise[HttpResponse])](bufferSize = bufferSize, OverflowStrategy.backpressure)
    .via(httpFlow)
    .withAttributes(ActorAttributes.dispatcher(api.mailJetDispatcher))
    .toMat(Sink.foreach {
      case (Success(resp), p) => p.success(resp)
      case (Failure(e), p)    => p.failure(e)
    })(Keep.left)
    .run()(ActorMaterializer()(actorSystem))

  private lazy val authorization = Authorization(
    BasicHttpCredentials(mailJetConfiguration.campaignApiKey, mailJetConfiguration.campaignSecretKey)
  )

  def manageContactMailJetRequest(listId: String, manageContact: ManageContact): Future[HttpResponse] = {
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contactslist/$listId/managecontact"),
      headers = immutable.Seq(authorization),
      entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(manageContact.asJson))
    )
    doHttpCall(request)
  }

  def manageContactListMailJetRequest(manageContactList: ManageManyContacts): Future[HttpResponse] = {
    val request =
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact/managemanycontacts"),
        headers = immutable.Seq(authorization),
        entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(manageContactList.asJson))
      )
    doHttpCall(request)
  }

  def updateContactProperties(contactData: ContactData, email: String): Future[HttpResponse] = {
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

  def sendEmailMailJetRequest(message: SendMessages): Future[HttpResponse] = {
    val request: HttpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(s"${mailJetConfiguration.url}/v3.1/send"),
      headers = immutable
        .Seq(Authorization(BasicHttpCredentials(mailJetConfiguration.apiKey, mailJetConfiguration.secretKey))),
      entity = HttpEntity(ContentTypes.`application/json`, printer.pretty(message.asJson))
    )
    doHttpCall(request)
  }

  private def doHttpCall(request: HttpRequest): Future[HttpResponse] = {
    val promise = Promise[HttpResponse]()
    queue.offer((request, promise)).flatMap {
      case QueueOfferResult.Enqueued    => promise.future
      case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("Queue overflowed. Try again later."))
      case QueueOfferResult.Failure(ex) => Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future
          .failed(new RuntimeException("Queue was closed (pool shut down) while running the request. Try again later."))
    }
  }

  override lazy val crmService: CrmService = new CrmService {

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
        ).map { response =>
          logMailjetResponse(response, "Add single to optin list")
        }
      }
    }

    override def removeUserFromOptInList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.optInListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.Remove)
      ).map { response =>
        logMailjetResponse(response, "Remove single from optin list")
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
        ).map { response =>
          logMailjetResponse(response, "Add single to hardbounce list")
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
        ).map { response =>
          logMailjetResponse(response, "Add single to unsubscribe list")
        }
      }
    }

    override def sendEmail(message: SendEmail): Future[Unit] = {
      val messages = SendMessages(message)
      sendEmailMailJetRequest(message = messages).map { response =>
        logMailjetSendEmailResponse(messages, response)
      }
    }

    override def removeUserFromHardBounceList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.hardBounceListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.Remove)
      ).map { response =>
        logMailjetResponse(response, "Remove from hardbounce list")
      }
    }

    override def removeUserFromUnsubscribeList(user: User): Future[Unit] = {
      manageContactMailJetRequest(
        listId = mailJetConfiguration.unsubscribeListId,
        manageContact =
          ManageContact(user.email, user.fullName.getOrElse(user.email), action = ManageContactAction.Remove)
      ).map { response =>
        logMailjetResponse(response, "Remove from unsubscribe list")
      }
    }

    override def addUsersToOptInList(users: Seq[User]): Future[Unit] = {
      if (users.isEmpty) {
        Future.successful {}
      }

      val properties: Future[Map[UserId, ContactProperties]] = Future
        .traverse(users) { user =>
          getPropertiesFromUser(user).map { properties =>
            user.userId -> properties
          }
        }
        .map(_.toMap)

      properties.map { properties =>
        val contacts: ManageManyContacts = ManageManyContacts(
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
        manageContactListMailJetRequest(manageContactList = contacts).map { response =>
          logMailjetResponse(response, "Add to optin list")
        }

      }
    }

    override def addUsersToUnsubscribeList(users: Seq[User]): Future[Unit] = {
      val properties: Future[Map[UserId, ContactProperties]] = Future
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
        ).map { response =>
          logMailjetResponse(response, "Add to unsubscribe list")
        }
      }
    }

    override def addUsersToHardBounceList(users: Seq[User]): Future[Unit] = {
      val properties: Future[Map[UserId, ContactProperties]] = Future
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
        ).map { response =>
          logMailjetResponse(response, "Add to hardbounce list")
        }

      }
    }

    override def updateUserProperties(user: User): Future[Unit] = {

      getPropertiesFromUser(user).flatMap { properties =>
        val contactData = properties.toContactPropertySeq

        updateContactProperties(ContactData(data = contactData), user.email).map { response =>
          logMailjetResponse(response, "Update user properties")
        }
      }

      Future.successful {}
    }

    private val localDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00'Z'")
    private val dateFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    private val dayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyyyy").withZone(ZoneOffset.UTC)

    private def isLast30daysDate(date: ZonedDateTime): Boolean = {
      val days: Int = 30
      date.isAfter(DateHelper.now().minusDays(days))
    }

    override def getPropertiesFromUser(user: User): Future[ContactProperties] = {

      implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

      val events: Source[EventEnvelope, NotUsed] =
        userJournal.currentEventsByPersistenceId(
          persistenceId = user.userId.value,
          fromSequenceNr = 0,
          toSequenceNr = Long.MaxValue
        )

      val userProperties: Future[UserProperties] = events
        .runFold(userPropertiesFromUser(user)) { (accumulator: UserProperties, envelope: EventEnvelope) =>
          accumulateEvent(accumulator, envelope)
        }

      for {
        properties <- userProperties
        operations <- operationService.find(slug = None, country = None, maybeSource = None, openAt = None)
      } yield {
        contactPropertiesFromUserProperties(properties.normalize(operations))
      }
    }

    private def userPropertiesFromUser(user: User): UserProperties = {
      UserProperties(
        userId = user.userId,
        firstname = user.firstName.getOrElse(""),
        zipCode = user.profile.flatMap(_.postalCode),
        dateOfBirth = user.profile.flatMap(_.dateOfBirth),
        emailValidationStatus = user.emailVerified,
        emailHardBounceStatus = user.isHardBounce,
        unsubscribeStatus = user.profile.exists(!_.optInNewsletter),
        accountCreationDate = user.createdAt,
        isOrganisation = user.isOrganisation,
        updatedAt = Some(DateHelper.now()),
        accountCreationCountry = Some(user.country.value),
        lastLanguageActivity = Some(user.language.value),
        lastCountryActivity = Some(user.country.value),
        countriesActivity = Seq(user.country.value)
      )
    }
    private def contactPropertiesFromUserProperties(userProperty: UserProperties): ContactProperties = {
      ContactProperties(
        userId = Some(userProperty.userId),
        firstName = Some(userProperty.firstname),
        postalCode = userProperty.zipCode,
        dateOfBirth = userProperty.dateOfBirth.map(_.format(localDateFormatter)),
        emailValidationStatus = Some(userProperty.emailValidationStatus),
        emailHardBounceValue = Some(userProperty.emailHardBounceStatus),
        unsubscribeStatus = Some(userProperty.unsubscribeStatus),
        accountCreationDate = userProperty.accountCreationDate.map(_.format(dateFormatter)),
        accountCreationSource = userProperty.accountCreationSource,
        accountCreationOrigin = userProperty.accountCreationOrigin,
        accountCreationOperation = userProperty.accountCreationOperation,
        accountCreationCountry = userProperty.accountCreationCountry,
        countriesActivity = Some(userProperty.countriesActivity.distinct.mkString(",")),
        lastCountryActivity = userProperty.lastCountryActivity,
        lastLanguageActivity = userProperty.lastLanguageActivity,
        totalProposals = Some(userProperty.totalNumberProposals.getOrElse(0)),
        totalVotes = Some(userProperty.totalNumbervotes.getOrElse(0)),
        firstContributionDate = userProperty.firstContributionDate.map(_.format(dateFormatter)),
        lastContributionDate = userProperty.lastContributionDate.map(_.format(dateFormatter)),
        operationActivity = Some(userProperty.operationActivity.distinct.mkString(",")),
        activeCore = userProperty.activeCore,
        daysOfActivity = Some(userProperty.daysOfActivity.distinct.length),
        daysOfActivity30 = Some(userProperty.daysOfActivity30d.distinct.length),
        numberOfThemes = Some(userProperty.themes.distinct.length),
        userType = if (userProperty.isOrganisation) {
          Some("B2B")
        } else {
          Some("B2C")
        },
        updatedAt = userProperty.updatedAt.map(_.format(dateFormatter))
      )
    }
    private def accumulateEvent(accumulator: UserProperties, envelope: EventEnvelope): UserProperties = {
      envelope.event match {
        case event: LogRegisterCitizenEvent     => accumulateLogRegisterCitizenEvent(accumulator, event)
        case event: LogUserProposalEvent        => accumulateLogUserProposalEvent(accumulator, event)
        case event: LogUserVoteEvent            => accumulateLogUserVoteEvent(accumulator, event)
        case event: LogUserUnvoteEvent          => accumulateLogUserUnvoteEvent(accumulator, event)
        case event: LogUserQualificationEvent   => accumulateLogUserQualificationEvent(accumulator, event)
        case event: LogUserUnqualificationEvent => accumulateLogUserUnqualificationEvent(accumulator, event)

        case _ => accumulator
      }
    }

    private def accumulateLogUserUnqualificationEvent(accumulator: UserProperties,
                                                      event: LogUserUnqualificationEvent): UserProperties = {
      accumulator.copy(
        lastContributionDate = Some(event.action.date),
        lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
        lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
        countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
        operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
        activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
        daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
        daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
          accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
        } else {
          accumulator.daysOfActivity
        },
        themes = event.requestContext.currentTheme
          .map(theme => accumulator.themes ++ Seq(theme.value))
          .getOrElse(accumulator.themes)
      )
    }
    private def accumulateLogUserQualificationEvent(accumulator: UserProperties,
                                                    event: LogUserQualificationEvent): UserProperties = {
      accumulator.copy(
        lastContributionDate = Some(event.action.date),
        lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
        lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
        countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
        operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
        activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
        daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
        daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
          accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
        } else {
          accumulator.daysOfActivity30d
        },
        themes = event.requestContext.currentTheme
          .map(theme => accumulator.themes ++ Seq(theme.value))
          .getOrElse(accumulator.themes)
      )
    }
    private def accumulateLogUserUnvoteEvent(accumulator: UserProperties, event: LogUserUnvoteEvent): UserProperties = {
      accumulator.copy(
        totalNumbervotes = accumulator.totalNumbervotes.map(_ - 1).orElse(Some(-1)),
        lastContributionDate = Some(event.action.date),
        lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
        lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
        countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
        operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
        activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
        daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
        daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
          accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
        } else {
          accumulator.daysOfActivity30d
        },
        themes = event.requestContext.currentTheme
          .map(theme => accumulator.themes ++ Seq(theme.value))
          .getOrElse(accumulator.themes)
      )
    }
    private def accumulateLogUserVoteEvent(accumulator: UserProperties, event: LogUserVoteEvent): UserProperties = {
      accumulator.copy(
        totalNumbervotes = accumulator.totalNumbervotes.map(_ + 1).orElse(Some(1)),
        lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
        lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
        countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
        operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
        firstContributionDate = accumulator.firstContributionDate.orElse(Option(event.action.date)),
        lastContributionDate = Some(event.action.date),
        activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
        daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
        daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
          accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
        } else {
          accumulator.daysOfActivity30d
        },
        themes = event.requestContext.currentTheme
          .map(theme => accumulator.themes ++ Seq(theme.value))
          .getOrElse(accumulator.themes)
      )
    }

    private def accumulateLogUserProposalEvent(accumulator: UserProperties,
                                               event: LogUserProposalEvent): UserProperties = {
      accumulator.copy(
        totalNumberProposals = accumulator.totalNumberProposals.map(_ + 1).orElse(Some(1)),
        lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
        lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
        countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
        operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value),
        firstContributionDate = accumulator.firstContributionDate.orElse(Option(event.action.date)),
        lastContributionDate = Some(event.action.date),
        activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
        daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
        daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
          accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
        } else {
          accumulator.daysOfActivity30d
        },
        themes = event.requestContext.currentTheme
          .map(theme => accumulator.themes ++ Seq(theme.value))
          .getOrElse(accumulator.themes)
      )
    }

    private def accumulateLogRegisterCitizenEvent(accumulator: UserProperties,
                                                  event: LogRegisterCitizenEvent): UserProperties = {
      accumulator.copy(
        accountCreationSource = event.requestContext.source,
        accountCreationOrigin = event.requestContext.getParameters.map { parameters =>
          parameters.getOrElse("utm_source", "unknown")
        },
        accountCreationOperation = event.requestContext.operationId.map(_.value),
        accountCreationCountry = event.requestContext.country.map(_.value),
        countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
        operationActivity = accumulator.operationActivity ++ event.requestContext.operationId.map(_.value)
      )
    }

    def logMailjetResponse(result: HttpResponse, operationName: String): Unit = {
      result match {
        case HttpResponse(code, _, _, _) if code.isSuccess() =>
          logger.debug(s"$operationName: call completed with status $code")
        case HttpResponse(code, _, entity, _) =>
          logger.error(s"$operationName failed with status $code: $entity")
      }
    }

    val printer: Printer = Printer.noSpaces

    def logMailjetSendEmailResponse(message: SendMessages, result: HttpResponse): Unit = {
      result match {
        case HttpResponse(code, _, entity, _) if code.isSuccess() =>
          logger.info(s"Sent email: ${printer.pretty(message.asJson)} ->  $entity")
        case HttpResponse(code, _, entity, _) =>
          logger.error(s"send email failed with status $code: $entity")
      }
    }
  }
}

final case class UserProperties(userId: UserId,
                                firstname: String,
                                zipCode: Option[String],
                                dateOfBirth: Option[LocalDate],
                                emailValidationStatus: Boolean,
                                emailHardBounceStatus: Boolean,
                                unsubscribeStatus: Boolean,
                                accountCreationDate: Option[ZonedDateTime],
                                isOrganisation: Boolean,
                                accountCreationSource: Option[String] = None,
                                accountCreationOrigin: Option[String] = None,
                                accountCreationOperation: Option[String] = None,
                                accountCreationCountry: Option[String] = None,
                                countriesActivity: Seq[String] = Seq.empty,
                                lastCountryActivity: Option[String] = None,
                                lastLanguageActivity: Option[String] = None,
                                totalNumberProposals: Option[Int] = None,
                                totalNumbervotes: Option[Int] = None,
                                firstContributionDate: Option[ZonedDateTime] = None,
                                lastContributionDate: Option[ZonedDateTime] = None,
                                operationActivity: Seq[String] = Seq.empty,
                                activeCore: Option[Boolean] = None,
                                daysOfActivity: Seq[String] = Seq.empty,
                                daysOfActivity30d: Seq[String] = Seq.empty,
                                themes: Seq[String] = Seq.empty,
                                userType: Option[String] = None,
                                updatedAt: Option[ZonedDateTime]) {

  def normalize(operations: Seq[Operation]): UserProperties = {
    normalizeUserPropertiesWhenNoRegisterEvent().normalizeOperationActivity(operations)
  }

  /*
   * Replace operation slug present in some events by operationId
   */
  private def normalizeOperationActivity(operations: Seq[Operation]): UserProperties = {
    val operationIds: Seq[String] = operationActivity
      .map(
        operationIdOrSlug =>
          operations
            .find(operation => operation.operationId.value == operationIdOrSlug)
            .map(_.operationId.value)
            .orElse(operations.find(_.slug == operationIdOrSlug).map(_.operationId.value))
            .getOrElse(operationIdOrSlug)
      )
      .distinct

    this.copy(operationActivity = operationIds)
  }

  /*
   * Fix properties for user with no register event (previous bug that has been resolved)
   */
  private def normalizeUserPropertiesWhenNoRegisterEvent(): UserProperties = {
    val sourceFixDate: ZonedDateTime = ZonedDateTime.parse("2018-09-01T00:00:00Z")
    (accountCreationSource, accountCreationDate) match {
      case (None, Some(date)) if date.isBefore(sourceFixDate) =>
        this.copy(
          accountCreationSource = Some("core"),
          accountCreationCountry = accountCreationCountry.orElse(Some("FR")),
          countriesActivity = if (countriesActivity.isEmpty) Seq("FR") else countriesActivity,
          lastCountryActivity = lastCountryActivity.orElse(Some("FR")),
          lastLanguageActivity = lastLanguageActivity.orElse(Some("fr"))
        )
      case _ => this
    }
  }
}
