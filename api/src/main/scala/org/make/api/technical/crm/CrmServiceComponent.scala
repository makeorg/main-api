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

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}
import java.util.stream.Collectors

import akka.{Done, NotUsed}
import akka.persistence.query.EventEnvelope
import akka.stream._
import akka.stream.alpakka.file.scaladsl.LogRotatorSink
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.Decoder
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.proposal.{ProposalCoordinatorServiceComponent, ProposalSearchEngineComponent}
import org.make.api.question.{QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.technical.RichFutures._
import org.make.api.technical.crm.BasicCrmResponse.ManageManyContactsResponse
import org.make.api.technical.crm.ManageContactAction.{AddNoForce, Remove}
import org.make.api.technical.{ReadJournalComponent, StreamUtils}
import org.make.api.user.{PersistentUserToAnonymizeServiceComponent, UserServiceComponent}
import org.make.api.userhistory._
import org.make.core.DateHelper.isLast30daysDate
import org.make.core.Validation.emailRegex
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.indexed.ProposalsSearchResult
import org.make.core.question.Question
import org.make.core.reference.{Country, Language}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success, Try}

trait CrmService {
  def sendEmail(message: SendEmail): Future[Unit]
  def synchronizeList(formattedDate: String, list: CrmList, csvDirectory: Path): Future[Done]
  def createCrmUsers(): Future[Unit]
  def anonymize(): Future[Unit]
  def synchronizeContactsWithCrm(): Future[Unit]
  def getUsersMailFromList(listId: Option[String] = None,
                           sort: Option[String] = None,
                           order: Option[String] = None,
                           countOnly: Option[Boolean] = None,
                           limit: Int,
                           offset: Int = 0): Future[GetUsersMail]
  def deleteAllContactsBefore(maxUpdatedAt: ZonedDateTime, deleteEmptyProperties: Boolean): Future[Int]
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
    with ProposalSearchEngineComponent
    with PersistentUserToAnonymizeServiceComponent
    with PersistentCrmUserServiceComponent
    with CrmClientComponent
    with ErrorAccumulatingCirceSupport =>

  class QuestionResolver(questions: Seq[Question], operations: Map[String, OperationId]) {

    private val questionsWithOperation: Seq[Question] = questions.filter(_.operationId.isDefined)

    def findQuestionWithOperation(predicate: Question => Boolean): Option[Question] =
      questionsWithOperation.find(predicate)

    def extractQuestionWithOperationFromRequestContext(requestContext: RequestContext): Option[Question] = {
      requestContext.questionId
        .flatMap(questionId => questionsWithOperation.find(_.questionId == questionId))
        .orElse {
          requestContext.operationId.flatMap { operationId =>
            questionsWithOperation.find(
              question =>
                // In old operations, the header contained the slug and not the id
                // also the old operations didn't all have a country or language
                (question.operationId.contains(operationId) ||
                  question.operationId == operations.get(operationId.value)) &&
                  requestContext.country.orElse(Some(Country("FR"))).contains(question.country) &&
                  requestContext.language.orElse(Some(Language("fr"))).contains(question.language)
            )
          }
        }
    }
  }

  override lazy val crmService: DefaultCrmService = new DefaultCrmService

  class DefaultCrmService extends CrmService {

    private lazy val batchSize: Int = mailJetConfiguration.userListBatchSize
    private lazy val baseCsvDirectory: String = mailJetConfiguration.csvDirectory
    private lazy val csvSize: Int = mailJetConfiguration.csvSize
    private val retrievePropertiesParallelism = 10
    private val persistCrmUsersParallelism = 5

    private val poolSize: Int = 10
    implicit private val executionContext: ExecutionContextExecutor =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(poolSize, new ThreadFactory {
        val counter = new AtomicInteger()
        override def newThread(runnable: Runnable): Thread =
          new Thread(runnable, "crm-batchs-" + counter.getAndIncrement())
      }))

    implicit private val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

    private val localDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00")
    private val dateFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
    private val dayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyyyy").withZone(ZoneOffset.UTC)

    override def sendEmail(message: SendEmail): Future[Unit] = {
      val messages = SendMessages(message)
      val result = crmClient.sendEmail(message = messages)

      result.onComplete {
        case Success(response) =>
          logger.info(s"Sent email $messages with reponse $response")
        case Failure(e) =>
          logger.error(s"Sent email $messages failed", e)
      }

      result.map(_ => ())
    }

    override def getUsersMailFromList(listId: Option[String] = None,
                                      sort: Option[String] = None,
                                      order: Option[String] = None,
                                      countOnly: Option[Boolean] = None,
                                      limit: Int,
                                      offset: Int = 0): Future[GetUsersMail] = {
      crmClient
        .getUsersInformationMailFromList(listId, sort, order, countOnly, limit, offset)
        .map { response =>
          GetUsersMail(
            count = response.count,
            total = response.total,
            data = response.data.map(data => ContactMail(contactId = data.id, email = data.email))
          )
        }
        .recoverWith {
          case CrmClientException(message) =>
            logger.error(message)
            Future.successful(GetUsersMail(0, 0, Seq.empty))
          case e => Future.failed(e)
        }
    }

    override def deleteAllContactsBefore(maxUpdatedAt: ZonedDateTime, deleteEmptyProperties: Boolean): Future[Int] = {
      def isBefore(updatedAt: String): Boolean =
        Try(ZonedDateTime.parse(updatedAt)).toOption.forall(_.isBefore(maxUpdatedAt))

      StreamUtils
        .asyncPageToPageSource(crmClient.getContactsProperties(_, batchSize).map(_.data))
        .map(_.filter { contacts =>
          deleteEmptyProperties && contacts.properties.isEmpty ||
          contacts.properties.find(_.name == "updated_at").exists(updatedAt => isBefore(updatedAt.value))
        }.map(_.contactId.toString))
        .mapConcat(_.toVector)
        .throttle(1, 1.second)
        .mapAsync(1) { obsoleteContactId =>
          crmClient
            .deleteContactById(obsoleteContactId)
            .map(_ => 1)
            .recoverWith {
              case CrmClientException(message) =>
                logger.error(message)
                Future.successful(0)
              case e => Future.failed(e)
            }
        }
        .runFold(0)(_ + _)
        .map { total =>
          logger.info(s"$total contacts has been removed from mailjet.")
          total
        }
    }

    override def createCrmUsers(): Future[Unit] = {
      for {
        _        <- persistentCrmUserService.truncateCrmUsers()
        resolver <- createQuestionResolver
        _        <- computeAndPersistCrmUsers(resolver)
      } yield ()
    }

    private def resetDirectory(directory: Path): Unit = {
      if (Files.exists(directory)) {
        Files.list(directory).forEach(file => Files.deleteIfExists(file))
      } else {
        Files.createDirectories(directory)
      }
    }

    def initializeDirectories(): Future[Unit] = {
      Future[Unit] {
        resetDirectory(CrmList.HardBounce.targetDirectory(baseCsvDirectory))
        resetDirectory(CrmList.OptOut.targetDirectory(baseCsvDirectory))
        resetDirectory(CrmList.OptIn.targetDirectory(baseCsvDirectory))
      }
    }

    override def synchronizeContactsWithCrm(): Future[Unit] = {
      val startTime: Long = System.currentTimeMillis()
      val synchronizationTime = DateHelper.now().format(dateFormatter)

      val crmSynchronization =
        for {
          _ <- createCrmUsers()
          _ <- initializeDirectories()
          _ <- synchronizeList(
            formattedDate = synchronizationTime,
            list = CrmList.OptIn,
            csvDirectory = CrmList.OptIn.targetDirectory(baseCsvDirectory)
          )
          _ <- synchronizeList(
            formattedDate = synchronizationTime,
            list = CrmList.OptOut,
            csvDirectory = CrmList.OptOut.targetDirectory(baseCsvDirectory)
          )
          _ <- synchronizeList(
            formattedDate = synchronizationTime,
            list = CrmList.HardBounce,
            csvDirectory = CrmList.HardBounce.targetDirectory(baseCsvDirectory)
          )
          _ <- anonymize()
        } yield {}

      crmSynchronization.onComplete {
        case Failure(exception) =>
          logger.error(s"Mailjet synchro failed:", exception)
        case Success(_) =>
          logger.info(s"Mailjet synchro succeeded in ${System.currentTimeMillis() - startTime}ms")
      }

      crmSynchronization
    }

    private def fileSizeTriggerCreator(csvDirectory: Path): () => ByteString => Option[Path] = () => {
      val max = csvSize
      var size: Long = max
      element: ByteString =>
        if (size + element.size > max) {
          val path = Files.createFile(csvDirectory.resolve(s"${DateHelper.now.toString}.csv"))
          size = element.size
          Some(path)
        } else {
          size += element.size
          None
        }
    }

    def createCsv(formattedDate: String, list: CrmList, csvDirectory: Path): Future[Seq[Path]] = {
      StreamUtils
        .asyncPageToPageSource(persistentCrmUserService.list(list.unsubscribed, list.hardBounced, _, batchSize))
        .mapConcat(_.toVector)
        .map { crmUser =>
          Contact(
            email = crmUser.email,
            name = Some(crmUser.fullName),
            properties = Some(crmUser.toContactProperties(Some(formattedDate)))
          ).toStringCsv
        }
        .map(ByteString.apply(_, StandardCharsets.UTF_8))
        .runWith(LogRotatorSink(fileSizeTriggerCreator(csvDirectory)))
        .map(_ => Files.list(csvDirectory).collect(Collectors.toList[Path]).asScala)
    }

    private def sendCsvToHardBounceList(csv: Path, list: CrmList): Future[Long] = {
      for {
        csvId <- crmClient.sendCsv(mailJetConfiguration.hardBounceListId, csv)
        response <- crmClient.manageContactListWithCsv(
          CsvImport(
            mailJetConfiguration.hardBounceListId,
            csvId.csvId.toString,
            list.actionOnHardBounce,
            ImportOptions("yyyy-mm-dd hh:nn:ss").toString
          )
        )
      } yield {
        response.data.head.jobId
      }
    }

    private def sendCsvToOptInList(csv: Path, list: CrmList): Future[Long] = {
      for {
        csvId <- crmClient.sendCsv(mailJetConfiguration.optInListId, csv)
        response <- crmClient.manageContactListWithCsv(
          CsvImport(
            mailJetConfiguration.optInListId,
            csvId.csvId.toString,
            list.actionOnOptIn,
            ImportOptions("yyyy-mm-dd hh:nn:ss").toString
          )
        )
      } yield {
        response.data.head.jobId
      }
    }

    private def sendCsvToUnsubscribeList(csv: Path, list: CrmList): Future[Long] = {
      for {
        csvId <- crmClient.sendCsv(mailJetConfiguration.unsubscribeListId, csv)
        response <- crmClient.manageContactListWithCsv(
          CsvImport(
            mailJetConfiguration.unsubscribeListId,
            csvId.csvId.toString,
            list.actionOnOptOut,
            ImportOptions("yyyy-mm-dd hh:nn:ss").toString
          )
        )
      } yield {
        response.data.head.jobId
      }
    }

    override def synchronizeList(formattedDate: String, list: CrmList, csvDirectory: Path): Future[Done] = {
      Source
        .fromFuture(createCsv(formattedDate, list, csvDirectory))
        .mapConcat(_.toVector)
        .filter(Files.size(_) > 0)
        .mapAsync(1) { csv =>
          for {
            responseHardBouunce <- sendCsvToHardBounceList(csv, list)
            responseOptIn       <- sendCsvToOptInList(csv, list)
            responseUnsubscribe <- sendCsvToUnsubscribeList(csv, list)
            result              <- verifyJobCompletion(responseHardBouunce, responseOptIn, responseUnsubscribe)
          } yield result
        }
        .runWith(Sink.ignore)
    }

    private def verifyJobCompletion(responseHardBouunce: Long,
                                    responseOptIn: Long,
                                    responseUnsubscribe: Long): Future[Unit] = {
      val jobIds = Seq(responseHardBouunce, responseOptIn, responseUnsubscribe)
      val promise = Promise[Unit]()
      actorSystem.actorOf(CrmSynchroCsvMonitor.props(crmClient, jobIds, promise, mailJetConfiguration.tickInterval))
      promise.future
    }

    private def computeAndPersistCrmUsers(questionResolver: QuestionResolver): Future[Unit] = {
      val start = System.currentTimeMillis()
      StreamUtils
        .asyncPageToPageSource(userService.findUsersForCrmSynchro(None, None, _, batchSize))
        .mapConcat(_.toVector)
        .mapAsync(retrievePropertiesParallelism) { user =>
          getPropertiesFromUser(user, questionResolver).map { properties =>
            (user.email, user.fullName.getOrElse(user.email), properties)
          }
        }
        .groupedWithin(batchSize, 5.seconds)
        .map { contacts =>
          contacts.map {
            case (email, fullName, properties) => PersistentCrmUser.fromContactProperty(email, fullName, properties)
          }
        }
        .mapAsync(persistCrmUsersParallelism) { crmUsers =>
          persistentCrmUserService.persist(crmUsers)
        }
        .runWith(Sink.ignore)
        .map(_ => logger.info(s"Crm users creation completed in ${System.currentTimeMillis() - start} ms"))
    }

    final def deleteAnonymizedContacts(emails: Seq[String]): Future[Unit] = {
      Source
        .fromIterator(() => emails.toIterator)
        .mapAsync(3) { email =>
          if (email.matches(emailRegex.regex)) {
            crmClient.deleteContactByEmail(email).map(res => email -> res).withoutFailure
          } else {
            // If email is invalid, delete it from user to anonymize
            Future.successful(Right(email -> true))
          }
        }
        .collect { case Right((email, isAnon)) if isAnon => email }
        // Delete from user to anonymize in the flow in case the table is full
        .groupedWithin(100, 2.seconds)
        .mapAsync(1) { emails =>
          if (emails.nonEmpty) {
            persistentUserToAnonymizeService.removeAllByEmails(emails)
          } else {
            Future.successful(0)
          }
        }
        .runWith(Sink.ignore)
        .map(_ => ())
    }

    override def anonymize(): Future[Unit] = {
      for {
        foundEmails <- persistentUserToAnonymizeService.findAll()
        _           <- hardRemoveEmailsFromAllLists(foundEmails)
        _           <- deleteAnonymizedContacts(foundEmails)
      } yield ()
    }

    private def hardRemoveEmailsFromAllLists(emails: Seq[String]): Future[Unit] = {
      if (emails.isEmpty) {
        Future.successful {}
      } else {
        Source(emails.toVector)
          .map(email => Contact(email = email))
          .groupedWithin(batchSize, 10.seconds)
          .throttle(200, 1.hour)
          .mapAsync(1) { grouppedEmails =>
            crmClient
              .manageContactList(
                manageContactList = ManageManyContacts(
                  contacts = grouppedEmails,
                  contactList = Seq(
                    ContactList(mailJetConfiguration.hardBounceListId, Remove),
                    ContactList(mailJetConfiguration.unsubscribeListId, Remove),
                    ContactList(mailJetConfiguration.optInListId, Remove)
                  )
                )
              )
              .map(Right(_))
              .recoverWith { case e => Future.successful(Left(e)) }
              .map { response =>
                logMailjetResponse(response, "all lists")
              }
          }
          .runWith(Sink.last)
      }
    }

    private def createQuestionResolver: Future[QuestionResolver] = {
      val operationsAsMap = operationService
        .findSimple()
        .map(_.map(operation => operation.slug -> operation.operationId).toMap)
      for {
        questions  <- questionService.searchQuestion(SearchQuestionRequest())
        operations <- operationsAsMap
      } yield new QuestionResolver(questions, operations)
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
          accumulateLogUserProposalEvent(accumulator, event, questionResolver)
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
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toSeq,
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
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toSeq,
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

      futureQuestion.map { maybeQuestion =>
        accumulator.copy(
          totalNumbervotes = accumulator.totalNumbervotes.map(_ - 1).orElse(Some(-1)),
          lastContributionDate = Some(event.action.date),
          lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
          lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toSeq,
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
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toSeq,
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

    private def searchUserProposals(userId: UserId): Future[ProposalsSearchResult] = {
      val filters = SearchFilters(
        user = Some(UserSearchFilter(userId)),
        status = Some(StatusSearchFilter(ProposalStatus.statusMap.values.toSeq))
      )
      elasticsearchProposalAPI
        .countProposals(SearchQuery(filters = Some(filters)))
        .flatMap { count =>
          if (count == 0) {
            Future.successful(ProposalsSearchResult(0L, Seq.empty))
          } else {
            elasticsearchProposalAPI
              .searchProposals(SearchQuery(filters = Some(filters), limit = Some(count.intValue())))
          }

        }
    }

    private def accumulateLogUserProposalEvent(accumulator: UserProperties,
                                               event: LogUserProposalEvent,
                                               questionResolver: QuestionResolver): Future[UserProperties] = {
      val futureMaybeQuestion: Future[Option[Question]] =
        questionResolver
          .extractQuestionWithOperationFromRequestContext(event.requestContext) match {
          case Some(question) => Future.successful(Some(question))
          case None           =>
            // If we can't resolve the question, retrieve the user proposals,
            // and search for the one proposed at the event date
            searchUserProposals(event.userId).map { proposalResult =>
              proposalResult.results
                .find(_.createdAt == event.action.date)
                .flatMap(_.question.map(_.questionId))
                .flatMap { questionId =>
                  questionResolver
                    .findQuestionWithOperation(question => questionId == question.questionId)
                }
            }
        }

      futureMaybeQuestion.map { maybeQuestion =>
        accumulator.copy(
          totalNumberProposals = accumulator.totalNumberProposals.map(_ + 1).orElse(Some(1)),
          lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
          lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value).toSeq,
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toSeq,
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source.toSeq,
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
        questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toSeq,
        sourceActivity = accumulator.sourceActivity ++ event.requestContext.source
      )
    }

    private def logMailjetResponse(result: Either[Throwable, ManageManyContactsResponse], listName: String): Unit = {
      result match {
        case Right(ok) => logger.debug(s"Synchronizing list $listName answered $ok")
        case Left(e)   => logger.error(s"Error when synchronizing list $listName", e)
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

final case class ContactMail(email: String, contactId: Long)
object ContactMail {
  implicit val decoder: Decoder[ContactMail] = Decoder.forProduct2("Email", "ID")(ContactMail.apply)
}

sealed trait CrmList {
  def name: String
  def hardBounced: Boolean
  def unsubscribed: Option[Boolean]

  def targetDirectory(csvDirectory: String): Path = {
    Paths.get(csvDirectory, name)
  }

  def actionOnHardBounce: ManageContactAction
  def actionOnOptIn: ManageContactAction
  def actionOnOptOut: ManageContactAction
}

object CrmList {
  case object HardBounce extends CrmList {
    override val name: String = "hardBounce"
    override val hardBounced: Boolean = true
    override val unsubscribed: Option[Boolean] = None

    override val actionOnHardBounce: ManageContactAction = AddNoForce
    override val actionOnOptIn: ManageContactAction = Remove
    override val actionOnOptOut: ManageContactAction = Remove
  }

  case object OptIn extends CrmList {
    override val name: String = "optIn"
    override val hardBounced: Boolean = false
    override val unsubscribed: Option[Boolean] = Some(false)

    override val actionOnHardBounce: ManageContactAction = Remove
    override val actionOnOptIn: ManageContactAction = AddNoForce
    override val actionOnOptOut: ManageContactAction = Remove
  }

  case object OptOut extends CrmList {
    override val name: String = "optOut"
    override val hardBounced: Boolean = false
    override val unsubscribed: Option[Boolean] = Some(true)

    override val actionOnHardBounce: ManageContactAction = Remove
    override val actionOnOptIn: ManageContactAction = Remove
    override val actionOnOptOut: ManageContactAction = AddNoForce
  }
}
