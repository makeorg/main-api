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

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.query.EventEnvelope
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.StrictLogging
import io.circe.syntax._
import io.circe.{Decoder, Printer}
import org.make.api
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.proposal.ProposalCoordinatorServiceComponent
import org.make.api.question.{QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.technical.ReadJournalComponent
import org.make.api.technical.crm.ManageContactAction.{AddNoForce, Remove}
import org.make.api.user.{PersistentUserToAnonymizeServiceComponent, UserServiceComponent}
import org.make.api.userhistory._
import org.make.core.operation.OperationId
import org.make.core.question.Question
import org.make.core.reference.Country
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mdedetrich.akka.http.support.CirceHttpSupport

import scala.collection.immutable
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success, Try}

trait CrmService {
  def sendEmail(message: SendEmail): Future[Unit]
  def startCrmContactSynchronization(): Future[Unit]
  def getUsersMailFromList(listId: String, limit: Int, offset: Int): Future[GetUsersMail]
}
trait CrmServiceComponent {
  def crmService: CrmService
}

trait DefaultCrmServiceComponent extends CrmServiceComponent with StrictLogging {
  self: MailJetConfigurationComponent
    with ActorSystemComponent
    with OperationServiceComponent
    with QuestionServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with UserServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with ReadJournalComponent
    with ProposalCoordinatorServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with CirceHttpSupport =>

  class QuestionResolver(questions: Seq[Question], operations: Map[String, OperationId]) {

    def findQuestionWithOperation(predicate: Question => Boolean): Option[Question] =
      questions.filter(_.operationId.isDefined).find(predicate)

    def extractQuestionWithOperationFromRequestContext(requestContext: RequestContext): Option[Question] = {
      requestContext.questionId
        .flatMap(questionId => questions.find(_.questionId == questionId))
        .orElse {
          requestContext.operationId.flatMap { operationId =>
            questions.find(
              question =>
                // In old operations, the header contained the slug and not the id
                // also the old operations didn't all have a country or language
                (question.operationId.contains(operationId) ||
                  question.operationId == operations.get(operationId.value)) &&
                  requestContext.country.orElse(Some(Country("FR"))).contains(question.country) &&
                  requestContext.language.orElse(Some("fr")).contains(question.language)
            )
          }
        }
        .orElse {
          questions.find(_.themeId == requestContext.currentTheme)
        }
        .filter(_.operationId.isDefined)
    }
  }

  private lazy val batchSize: Int = mailJetConfiguration.userListBatchSize
  private val poolSize: Int = 10
  private val retrievePropertiesParallelism = 10
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

  override lazy val crmService: DefaultCrmService = new DefaultCrmService

  class DefaultCrmService extends CrmService {

    implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

    override def sendEmail(message: SendEmail): Future[Unit] = {
      val messages = SendMessages(message)
      sendEmailMailJetRequest(message = messages).map { response =>
        logMailjetSendEmailResponse(messages, response)
      }
    }

    private def synchronizeContacts(paging: Int => Future[Seq[User]],
                                    actions: Seq[ContactList],
                                    name: String,
                                    questionResolver: QuestionResolver): Future[Unit] = {

      asyncPageToPageSource(paging)
        .mapConcat(users => immutable.Seq(users: _*))
        .mapAsync(retrievePropertiesParallelism) { user =>
          getPropertiesFromUser(user, questionResolver).map { properties =>
            Contact(email = user.email, name = user.fullName.orElse(Some(user.email)), properties = Some(properties))
          }
        }
        .groupedWithin(batchSize, 5.seconds)
        .mapAsync(1) { contacts =>
          manageContactListMailJetRequest(
            manageContactList = ManageManyContacts(contacts = contacts, contactList = actions)
          )
        }
        .wireTap(logMailjetResponse(_, s"Add to $name list", None))
        .runForeach(_ => ())
        .map(_ => ())
    }

    private def optOut(questionResolver: QuestionResolver): Future[Unit] =
      synchronizeContacts(
        userService.getOptOutUsers(_, batchSize),
        Seq(
          ContactList(mailJetConfiguration.hardBounceListId, Remove),
          ContactList(mailJetConfiguration.unsubscribeListId, AddNoForce),
          ContactList(mailJetConfiguration.optInListId, Remove)
        ),
        "optOut",
        questionResolver
      )

    private def hardBounce(questionResolver: QuestionResolver): Future[Unit] =
      synchronizeContacts(
        userService.getUsersWithHardBounce(_, batchSize),
        Seq(
          ContactList(mailJetConfiguration.hardBounceListId, AddNoForce),
          ContactList(mailJetConfiguration.unsubscribeListId, Remove),
          ContactList(mailJetConfiguration.optInListId, Remove)
        ),
        "hardBounce",
        questionResolver
      )

    private def optIn(questionResolver: QuestionResolver): Future[Unit] =
      synchronizeContacts(
        userService.getOptInUsers(_, batchSize),
        Seq(
          ContactList(mailJetConfiguration.hardBounceListId, Remove),
          ContactList(mailJetConfiguration.unsubscribeListId, Remove),
          ContactList(mailJetConfiguration.optInListId, AddNoForce)
        ),
        "optIn",
        questionResolver
      )

    private def anonymize: Future[Seq[String]] =
      persistentUserToAnonymizeService
        .findAll()
        .flatMap(emails => hardRemoveEmailsFromAllLists(emails).map(_ => emails))

    private def validateAnonymized(emails: Seq[String]): Future[Done] =
      persistentUserToAnonymizeService.removeAllByEmails(emails).map(_ => Done)

    override def startCrmContactSynchronization(): Future[Unit] = {
      val startTime: Long = System.currentTimeMillis()

      def createQuestionResolver: Future[QuestionResolver] = {
        val operationsAsMap = operationService
          .findSimple()
          .map(_.map(operation => operation.slug -> operation.operationId).toMap)
        for {
          questions  <- questionService.searchQuestion(SearchQuestionRequest())
          operations <- operationsAsMap
        } yield new QuestionResolver(questions, operations)
      }

      (for {
        resolver <- createQuestionResolver
        _        <- optOut(resolver)
        _        <- hardBounce(resolver)
        _        <- optIn(resolver)
        emails   <- anonymize
        _        <- validateAnonymized(emails)
      } yield {}).onComplete {
        case Failure(exception) =>
          logger.error(s"Mailjet synchro failed:", exception)
        case Success(_) =>
          logger.info(s"Mailjet synchro succeeded in ${System.currentTimeMillis() - startTime}ms")
      }

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

    private def hardRemoveEmailsFromAllLists(emails: Seq[String]): Future[Unit] = {

      manageContactListMailJetRequest(
        manageContactList = ManageManyContacts(
          contacts = emails.map(email => Contact(email = email)),
          contactList = Seq(
            ContactList(mailJetConfiguration.hardBounceListId, Remove),
            ContactList(mailJetConfiguration.unsubscribeListId, Remove),
            ContactList(mailJetConfiguration.optInListId, Remove)
          )
        )
      ).map { response =>
        logMailjetResponse(response, "Hard remove emails from all lists", None)
      }

    }

    private val localDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00'Z'")
    private val dateFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    private val dayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyyyy").withZone(ZoneOffset.UTC)

    private def isLast30daysDate(date: ZonedDateTime): Boolean = {
      val days: Int = 30
      date.isAfter(DateHelper.now().minusDays(days))
    }

    final def getPropertiesFromUser(user: User, resolver: QuestionResolver): Future[ContactProperties] = {

      val decider: Supervision.Decider = { e =>
        logger.error(
          s"Error in stream getPropertiesFromUser for user ${user.userId}. Stream resumed by dropping this element: ",
          e
        )
        Supervision.Resume
      }

      implicit val materializer: ActorMaterializer =
        ActorMaterializer(ActorMaterializerSettings(actorSystem).withSupervisionStrategy(decider))(actorSystem)

      val events: Source[EventEnvelope, NotUsed] =
        userJournal.currentEventsByPersistenceId(
          persistenceId = user.userId.value,
          fromSequenceNr = 0,
          toSequenceNr = Long.MaxValue
        )

      val initialProperties = userPropertiesFromUser(user, resolver)

      val userProperties: Future[UserProperties] = events
        .runFoldAsync(initialProperties) { (accumulator: UserProperties, envelope: EventEnvelope) =>
          accumulateEvent(accumulator, envelope, resolver)
        }

      userProperties.map(properties => contactPropertiesFromUserProperties(properties.normalize()))
    }

    private def userPropertiesFromUser(user: User, questionResolver: QuestionResolver): UserProperties = {
      val question =
        questionResolver.findQuestionWithOperation { question =>
          user.profile.flatMap(_.registerQuestionId).contains(question.questionId)
        }

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
        countriesActivity = Seq(user.country.value),
        accountCreationSlug = question.map(_.slug)
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
        accountCreationSlug = userProperty.accountCreationSlug,
        accountCreationCountry = userProperty.accountCreationCountry,
        countriesActivity = Some(userProperty.countriesActivity.distinct.mkString(",")),
        lastCountryActivity = userProperty.lastCountryActivity,
        lastLanguageActivity = userProperty.lastLanguageActivity,
        totalProposals = Some(userProperty.totalNumberProposals.getOrElse(0)),
        totalVotes = Some(userProperty.totalNumbervotes.getOrElse(0)),
        firstContributionDate = userProperty.firstContributionDate.map(_.format(dateFormatter)),
        lastContributionDate = userProperty.lastContributionDate.map(_.format(dateFormatter)),
        operationActivity = Some(userProperty.questionActivity.distinct.mkString(",")),
        sourceActivity = Some(userProperty.sourceActivity.distinct.mkString(",")),
        activeCore = userProperty.activeCore,
        daysOfActivity = Some(userProperty.daysOfActivity.distinct.length),
        daysOfActivity30 = Some(userProperty.daysOfActivity30d.distinct.length),
        userType = if (userProperty.isOrganisation) {
          Some("B2B")
        } else {
          Some("B2C")
        },
        updatedAt = userProperty.updatedAt.map(_.format(dateFormatter))
      )
    }
    private def accumulateEvent(accumulator: UserProperties,
                                envelope: EventEnvelope,
                                questionResolver: QuestionResolver): Future[UserProperties] = {
      envelope.event match {
        case event: LogRegisterCitizenEvent =>
          Future.successful(accumulateLogRegisterCitizenEvent(accumulator, event, questionResolver))
        case event: LogUserProposalEvent =>
          Future.successful(accumulateLogUserProposalEvent(accumulator, event, questionResolver))
        case event: LogUserVoteEvent =>
          accumulateLogUserVoteEvent(accumulator, event, questionResolver)
        case event: LogUserUnvoteEvent =>
          accumulateLogUserUnvoteEvent(accumulator, event, questionResolver)
        case event: LogUserQualificationEvent =>
          accumulateLogUserQualificationEvent(accumulator, event, questionResolver)
        case event: LogUserUnqualificationEvent =>
          accumulateLogUserUnqualificationEvent(accumulator, event, questionResolver)
        case _ => Future.successful(accumulator)
      }
    }

    private def accumulateLogUserUnqualificationEvent(accumulator: UserProperties,
                                                      event: LogUserUnqualificationEvent,
                                                      questionResolver: QuestionResolver): Future[UserProperties] = {
      val futureQuestion: Future[Option[Question]] =
        questionResolver
          .extractQuestionWithOperationFromRequestContext(event.requestContext)
          .map(Some(_))
          .map(Future.successful)
          .getOrElse(
            proposalCoordinatorService
              .getProposal(event.action.arguments.proposalId)
              .map { maybeProposal =>
                questionResolver.findQuestionWithOperation { question =>
                  maybeProposal.flatMap(_.questionId).contains(question.questionId)
                }
              }
          )

      futureQuestion.map { maybeQuestion =>
        accumulator.copy(
          lastContributionDate = Some(event.action.date),
          lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
          lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug),
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity
          }
        )
      }
    }

    private def accumulateLogUserQualificationEvent(accumulator: UserProperties,
                                                    event: LogUserQualificationEvent,
                                                    questionResolver: QuestionResolver): Future[UserProperties] = {

      val futureQuestion: Future[Option[Question]] =
        questionResolver
          .extractQuestionWithOperationFromRequestContext(event.requestContext)
          .map(Some(_))
          .map(Future.successful)
          .getOrElse(
            proposalCoordinatorService
              .getProposal(event.action.arguments.proposalId)
              .map { maybeProposal =>
                questionResolver.findQuestionWithOperation { question =>
                  maybeProposal.flatMap(_.questionId).contains(question.questionId)
                }
              }
          )

      futureQuestion.map { maybeQuestion =>
        accumulator.copy(
          lastContributionDate = Some(event.action.date),
          lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
          lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug),
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          }
        )
      }
    }
    private def accumulateLogUserUnvoteEvent(accumulator: UserProperties,
                                             event: LogUserUnvoteEvent,
                                             questionResolver: QuestionResolver): Future[UserProperties] = {
      val futureQuestion: Future[Option[Question]] =
        questionResolver
          .extractQuestionWithOperationFromRequestContext(event.requestContext)
          .map(Some(_))
          .map(Future.successful)
          .getOrElse(
            proposalCoordinatorService
              .getProposal(event.action.arguments.proposalId)
              .map { maybeProposal =>
                questionResolver.findQuestionWithOperation { question =>
                  maybeProposal.flatMap(_.questionId).contains(question.questionId)
                }
              }
          )

      futureQuestion.map { question =>
        accumulator.copy(
          totalNumbervotes = accumulator.totalNumbervotes.map(_ - 1).orElse(Some(-1)),
          lastContributionDate = Some(event.action.date),
          lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
          lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ question.map(_.slug),
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          }
        )
      }
    }
    private def accumulateLogUserVoteEvent(accumulator: UserProperties,
                                           event: LogUserVoteEvent,
                                           questionResolver: QuestionResolver): Future[UserProperties] = {
      val futureQuestion: Future[Option[Question]] =
        questionResolver
          .extractQuestionWithOperationFromRequestContext(event.requestContext)
          .map(Some(_))
          .map(Future.successful)
          .getOrElse(
            proposalCoordinatorService
              .getProposal(event.action.arguments.proposalId)
              .map { maybeProposal =>
                questionResolver.findQuestionWithOperation { question =>
                  maybeProposal.flatMap(_.questionId).contains(question.questionId)
                }
              }
          )

      futureQuestion.map { maybeQuestion =>
        accumulator.copy(
          totalNumbervotes = accumulator.totalNumbervotes.map(_ + 1).orElse(Some(1)),
          lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
          lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          accountCreationSlug = accumulator.accountCreationSlug.orElse(maybeQuestion.map(_.slug)),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug),
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          firstContributionDate = accumulator.firstContributionDate.orElse(Option(event.action.date)),
          lastContributionDate = Some(event.action.date),
          activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          }
        )
      }
    }

    private def accumulateLogUserProposalEvent(accumulator: UserProperties,
                                               event: LogUserProposalEvent,
                                               questionResolver: QuestionResolver): UserProperties = {
      val maybeQuestion = questionResolver
        .extractQuestionWithOperationFromRequestContext(event.requestContext)

      accumulator.copy(
        totalNumberProposals = accumulator.totalNumberProposals.map(_ + 1).orElse(Some(1)),
        lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
        lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
        countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
        questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug),
        sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
        firstContributionDate = accumulator.firstContributionDate.orElse(Option(event.action.date)),
        lastContributionDate = Some(event.action.date),
        activeCore = event.requestContext.currentTheme.map(_ => true).orElse(accumulator.activeCore),
        daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
        daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
          accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
        } else {
          accumulator.daysOfActivity30d
        }
      )
    }

    private def accumulateLogRegisterCitizenEvent(accumulator: UserProperties,
                                                  event: LogRegisterCitizenEvent,
                                                  questionResolver: QuestionResolver): UserProperties = {

      val maybeQuestion = questionResolver
        .extractQuestionWithOperationFromRequestContext(event.requestContext)

      accumulator.copy(
        accountCreationSource = event.requestContext.source,
        accountCreationOrigin = event.requestContext.getParameters.map { parameters =>
          parameters.getOrElse("utm_source", "unknown")
        },
        accountCreationSlug = accumulator.accountCreationSlug.orElse(maybeQuestion.map(_.slug)),
        accountCreationCountry = event.requestContext.country.map(_.value),
        countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
        questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug),
        sourceActivity = accumulator.sourceActivity ++ event.requestContext.source
      )
    }

    def logMailjetResponse(result: HttpResponse, operationName: String, maybeUserEmail: Option[String]): Unit = {
      result match {
        case HttpResponse(code, _, _, _) if code.isSuccess() =>
          logger.debug(s"$operationName: call completed with status $code")
        case HttpResponse(code, _, entity, _) =>
          maybeUserEmail match {
            case Some(userEmail) =>
              logger.error(s"$operationName for user '$userEmail' failed with status $code: $entity")
            case _ => logger.error(s"$operationName failed with status $code: $entity")
          }

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

    override def getUsersMailFromList(listId: String, limit: Int, offset: Int): Future[GetUsersMail] = {
      implicit val system = actorSystem
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      val params = s"ContactsList=$listId&Limit=$limit&Offset=$offset"
      val request = HttpRequest(
        method = HttpMethods.GET,
        uri = Uri(s"${mailJetConfiguration.url}/v3/REST/contact?$params"),
        headers = immutable.Seq(authorization)
      )
      doHttpCall(request).flatMap {
        case HttpResponse(code, _, entity, _) if code.isSuccess() =>
          Unmarshal(entity).to[GetUsersMail]
        case HttpResponse(code, _, entity, _) =>
          logger.error(s"getUsersMailFromList failed with status $code: $entity")
          Future.successful(GetUsersMail(0, 0, Seq.empty))
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
                                accountCreationSlug: Option[String] = None,
                                accountCreationCountry: Option[String] = None,
                                countriesActivity: Seq[String] = Seq.empty,
                                lastCountryActivity: Option[String] = None,
                                lastLanguageActivity: Option[String] = None,
                                totalNumberProposals: Option[Int] = None,
                                totalNumbervotes: Option[Int] = None,
                                firstContributionDate: Option[ZonedDateTime] = None,
                                lastContributionDate: Option[ZonedDateTime] = None,
                                questionActivity: Seq[String] = Seq.empty,
                                sourceActivity: Seq[String] = Seq.empty,
                                activeCore: Option[Boolean] = None,
                                daysOfActivity: Seq[String] = Seq.empty,
                                daysOfActivity30d: Seq[String] = Seq.empty,
                                userType: Option[String] = None,
                                updatedAt: Option[ZonedDateTime]) {

  def normalize(): UserProperties = {
    normalizeUserPropertiesWhenNoRegisterEvent()
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
          lastLanguageActivity = lastLanguageActivity.orElse(Some("fr")),
          sourceActivity = (sourceActivity ++ Some("core")).distinct
        )
      case _ => this
    }
  }
}

final case class GetUsersMail(count: Int, total: Int, data: Seq[ContactMail])
object GetUsersMail {
  implicit val decoder: Decoder[GetUsersMail] = Decoder.forProduct3("Count", "Total", "Data")(GetUsersMail.apply)
}

final case class ContactMail(email: String)
object ContactMail {
  implicit val decoder: Decoder[ContactMail] = Decoder.forProduct1("Email")(ContactMail.apply)
}
