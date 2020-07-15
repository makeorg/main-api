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
import akka.http.scaladsl.model.StatusCodes
import akka.persistence.query.EventEnvelope
import akka.stream._
import akka.stream.alpakka.file.scaladsl.LogRotatorSink
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import enumeratum.values.{StringEnum, StringEnumEntry}
import eu.timepit.refined.auto._
import io.circe.Decoder
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.question.{QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.technical.RichFutures._
import org.make.api.technical.crm.BasicCrmResponse.ManageManyContactsResponse
import org.make.api.technical.crm.ManageContactAction.{AddNoForce, Remove}
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.api.technical.job.JobCoordinatorServiceComponent
import org.make.api.technical.{EventBusServiceComponent, ReadJournalComponent, StreamUtils, TimeSettings}
import org.make.api.user.{PersistentUserToAnonymizeServiceComponent, UserServiceComponent}
import org.make.api.userhistory._
import org.make.core.DateHelper.isLast30daysDate
import org.make.core.Validation.emailRegex
import org.make.core.job.Job.JobId.SyncCrmData
import org.make.core.question.Question
import org.make.core.user.{User, UserId, UserType}

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success, Try}
import org.make.core.DateHelper
import org.make.api.proposal.ProposalServiceComponent

trait CrmService {
  def sendEmail(message: SendEmail): Future[SendEmailResponse]
  def synchronizeList(formattedDate: String, list: CrmList, csvDirectory: Path): Future[Done]
  def createCrmUsers(): Future[Unit]
  def anonymize(): Future[Unit]
  def synchronizeContactsWithCrm(): Future[JobAcceptance]
  def getUsersMailFromList(
    listId: Option[String] = None,
    sort: Option[String] = None,
    order: Option[String] = None,
    countOnly: Option[Boolean] = None,
    limit: Int,
    offset: Int = 0
  ): Future[GetUsersMail]
  def deleteAllContactsBefore(maxUpdatedAt: ZonedDateTime, deleteEmptyProperties: Boolean): Future[Int]
}

trait CrmServiceComponent {
  def crmService: CrmService
}

trait DefaultCrmServiceComponent extends CrmServiceComponent with StrictLogging with ErrorAccumulatingCirceSupport {
  self: MailJetConfigurationComponent
    with ActorSystemComponent
    with OperationServiceComponent
    with QuestionServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with UserServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with ReadJournalComponent
    with ProposalServiceComponent
    with PersistentCrmUserServiceComponent
    with CrmClientComponent
    with EventBusServiceComponent
    with ErrorAccumulatingCirceSupport
    with JobCoordinatorServiceComponent =>

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

    private val localDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00")
    private val dateFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
    private val dayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("ddMMyyyy").withZone(ZoneOffset.UTC)

    private implicit val timeout: Timeout = TimeSettings.defaultTimeout

    override def sendEmail(message: SendEmail): Future[SendEmailResponse] = {
      val messages = SendMessages(message)
      val result = crmClient.sendEmail(message = messages)

      result.onComplete {
        case Success(response) =>
          logger.info(s"Sent email $messages with reponse $response")
        case Failure(e) =>
          logger.error(s"Sent email $messages failed", e)
          e match {
            case CrmClientException.RequestException.SendEmailException(StatusCodes.BadRequest, _) =>
            case _ =>
              actorSystem.scheduler.scheduleOnce(mailJetConfiguration.delayBeforeResend) {
                eventBusService.publish(message)
              }
          }
      }

      result
    }

    override def getUsersMailFromList(
      listId: Option[String] = None,
      sort: Option[String] = None,
      order: Option[String] = None,
      countOnly: Option[Boolean] = None,
      limit: Int,
      offset: Int = 0
    ): Future[GetUsersMail] = {
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
          case e: CrmClientException =>
            logger.error(e.message)
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
        .mapConcat(identity)
        .throttle(1, 1.second)
        .mapAsync(1) { obsoleteContactId =>
          crmClient
            .deleteContactById(obsoleteContactId)
            .map(_ => 1)
            .recoverWith {
              case e: CrmClientException =>
                logger.error(e.message)
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

    override def synchronizeContactsWithCrm(): Future[JobAcceptance] = {
      val startTime: Long = System.currentTimeMillis()
      val synchronizationTime = DateHelper.now().format(dateFormatter)

      jobCoordinatorService.start(SyncCrmData) { report =>
        val crmSynchronization =
          for {
            _ <- createCrmUsers()
            _ <- report(49d)
            _ <- initializeDirectories()
            _ <- report(50d)
            _ <- synchronizeList(
              formattedDate = synchronizationTime,
              list = CrmList.OptIn,
              csvDirectory = CrmList.OptIn.targetDirectory(baseCsvDirectory)
            )
            _ <- report(65d)
            _ <- synchronizeList(
              formattedDate = synchronizationTime,
              list = CrmList.OptOut,
              csvDirectory = CrmList.OptOut.targetDirectory(baseCsvDirectory)
            )
            _ <- report(80d)
            _ <- synchronizeList(
              formattedDate = synchronizationTime,
              list = CrmList.HardBounce,
              csvDirectory = CrmList.HardBounce.targetDirectory(baseCsvDirectory)
            )
            _ <- report(95d)
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
        .mapConcat(identity)
        .map { crmUser =>
          Contact(
            email = crmUser.email,
            name = Some(crmUser.fullName),
            properties = Some(crmUser.toContactProperties(Some(formattedDate)))
          ).toStringCsv
        }
        .map(ByteString.apply(_, StandardCharsets.UTF_8))
        .runWith(LogRotatorSink(fileSizeTriggerCreator(csvDirectory)))
        .map(_ => Files.list(csvDirectory).collect(Collectors.toList[Path]).asScala.toSeq)
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
        .future(createCsv(formattedDate, list, csvDirectory))
        .mapConcat(identity)
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

    private def verifyJobCompletion(
      responseHardBounce: Long,
      responseOptIn: Long,
      responseUnsubscribe: Long
    ): Future[Unit] = {
      val jobIds = Seq(responseHardBounce, responseOptIn, responseUnsubscribe)
      val promise = Promise[Unit]()
      actorSystem.actorOf(CrmSynchroCsvMonitor.props(crmClient, jobIds, promise, mailJetConfiguration.tickInterval))
      promise.future
    }

    private def computeAndPersistCrmUsers(questionResolver: QuestionResolver): Future[Unit] = {
      val start = System.currentTimeMillis()
      StreamUtils
        .asyncPageToPageSource(userService.findUsersForCrmSynchro(None, None, _, batchSize))
        .mapConcat(identity)
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
      Source(emails)
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
        Source(emails)
          .map(email => Contact(email = email))
          .groupedWithin(batchSize, 10.seconds)
          .throttle(200, 1.hour)
          .mapAsync(1) { grouppedEmails =>
            crmClient
              .manageContactList(manageContactList = ManageManyContacts(
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

      val events: Source[EventEnvelope, NotUsed] =
        userJournal
          .currentEventsByPersistenceId(
            persistenceId = user.userId.value,
            fromSequenceNr = 0,
            toSequenceNr = Long.MaxValue
          )
          .withAttributes(ActorAttributes.supervisionStrategy(decider))

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
        userB2B = user.userType != UserType.UserTypeUser,
        updatedAt = Some(DateHelper.now()),
        accountCreationCountry = Some(user.country.value),
        lastLanguageActivity = Some(user.language.value),
        lastCountryActivity = Some(user.country.value),
        countriesActivity = Seq(user.country.value),
        accountCreationSlug = question.map(_.slug),
        accountType = Some(user.userType.value)
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
        accountCreationLocation = userProperty.accountCreationLocation,
        countriesActivity = Some(userProperty.countriesActivity.distinct.mkString(",")),
        lastCountryActivity = userProperty.lastCountryActivity,
        lastLanguageActivity = userProperty.lastLanguageActivity,
        totalProposals = Some(userProperty.totalNumberProposals.getOrElse(0)),
        totalVotes = Some(userProperty.totalNumbervotes.getOrElse(0)),
        firstContributionDate = userProperty.firstContributionDate.map(_.format(dateFormatter)),
        lastContributionDate = userProperty.lastContributionDate.map(_.format(dateFormatter)),
        operationActivity = Some(userProperty.questionActivity.distinct.mkString(",")),
        sourceActivity = Some(userProperty.sourceActivity.distinct.mkString(",")),
        daysOfActivity = Some(userProperty.daysOfActivity.distinct.length),
        daysOfActivity30 = Some(userProperty.daysOfActivity30d.distinct.length),
        userType = if (userProperty.userB2B) {
          Some("B2B")
        } else {
          Some("B2C")
        },
        accountType = userProperty.accountType,
        updatedAt = userProperty.updatedAt.map(_.format(dateFormatter))
      )
    }
    private def accumulateEvent(
      accumulator: UserProperties,
      envelope: EventEnvelope,
      questionResolver: QuestionResolver
    ): Future[UserProperties] = {
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

    private def accumulateLogUserUnqualificationEvent(
      accumulator: UserProperties,
      event: LogUserUnqualificationEvent,
      questionResolver: QuestionResolver
    ): Future[UserProperties] = {
      val futureQuestion: Future[Option[Question]] =
        proposalService.resolveQuestionFromVoteEvent(
          questionResolver,
          event.requestContext,
          event.action.arguments.proposalId
        )

      futureQuestion.map { maybeQuestion =>
        accumulator.copy(
          lastContributionDate = Some(event.action.date),
          lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
          lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toSeq,
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity
          }
        )
      }
    }

    private def accumulateLogUserQualificationEvent(
      accumulator: UserProperties,
      event: LogUserQualificationEvent,
      questionResolver: QuestionResolver
    ): Future[UserProperties] = {

      val futureQuestion: Future[Option[Question]] =
        proposalService.resolveQuestionFromVoteEvent(
          questionResolver,
          event.requestContext,
          event.action.arguments.proposalId
        )

      futureQuestion.map { maybeQuestion =>
        accumulator.copy(
          lastContributionDate = Some(event.action.date),
          lastCountryActivity = event.requestContext.country.map(_.value).orElse(accumulator.lastCountryActivity),
          lastLanguageActivity = event.requestContext.language.map(_.value).orElse(accumulator.lastLanguageActivity),
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toSeq,
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          }
        )
      }
    }
    private def accumulateLogUserUnvoteEvent(
      accumulator: UserProperties,
      event: LogUserUnvoteEvent,
      questionResolver: QuestionResolver
    ): Future[UserProperties] = {
      val futureQuestion: Future[Option[Question]] =
        proposalService.resolveQuestionFromVoteEvent(
          questionResolver,
          event.requestContext,
          event.action.arguments.proposalId
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
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          }
        )
      }
    }
    private def accumulateLogUserVoteEvent(
      accumulator: UserProperties,
      event: LogUserVoteEvent,
      questionResolver: QuestionResolver
    ): Future[UserProperties] = {
      val futureQuestion: Future[Option[Question]] =
        proposalService.resolveQuestionFromVoteEvent(
          questionResolver,
          event.requestContext,
          event.action.arguments.proposalId
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
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          }
        )
      }
    }

    private def accumulateLogUserProposalEvent(
      accumulator: UserProperties,
      event: LogUserProposalEvent,
      questionResolver: QuestionResolver
    ): Future[UserProperties] = {
      val futureMaybeQuestion: Future[Option[Question]] =
        proposalService.resolveQuestionFromUserProposal(
          questionResolver,
          event.requestContext,
          accumulator.userId,
          event.action.date
        )

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
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          }
        )
      }
    }

    private def accumulateLogRegisterCitizenEvent(
      accumulator: UserProperties,
      event: LogRegisterCitizenEvent,
      questionResolver: QuestionResolver
    ): UserProperties = {

      val maybeQuestion = questionResolver
        .extractQuestionWithOperationFromRequestContext(event.requestContext)

      accumulator.copy(
        accountCreationSource = event.requestContext.source,
        accountCreationOrigin = event.requestContext.getParameters.map { parameters =>
          parameters.getOrElse("utm_source", "unknown")
        },
        accountCreationSlug = accumulator.accountCreationSlug.orElse(maybeQuestion.map(_.slug)),
        accountCreationCountry = event.requestContext.country.map(_.value).orElse(accumulator.accountCreationCountry),
        accountCreationLocation = event.requestContext.location,
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

final case class UserProperties(
  userId: UserId,
  firstname: String,
  zipCode: Option[String],
  dateOfBirth: Option[LocalDate],
  emailValidationStatus: Boolean,
  emailHardBounceStatus: Boolean,
  unsubscribeStatus: Boolean,
  accountCreationDate: Option[ZonedDateTime],
  userB2B: Boolean,
  accountCreationSource: Option[String] = None,
  accountCreationOrigin: Option[String] = None,
  accountCreationSlug: Option[String] = None,
  accountCreationCountry: Option[String] = None,
  accountCreationLocation: Option[String] = None,
  countriesActivity: Seq[String] = Seq.empty,
  lastCountryActivity: Option[String] = None,
  lastLanguageActivity: Option[String] = None,
  totalNumberProposals: Option[Int] = None,
  totalNumbervotes: Option[Int] = None,
  firstContributionDate: Option[ZonedDateTime] = None,
  lastContributionDate: Option[ZonedDateTime] = None,
  questionActivity: Seq[String] = Seq.empty,
  sourceActivity: Seq[String] = Seq.empty,
  daysOfActivity: Seq[String] = Seq.empty,
  daysOfActivity30d: Seq[String] = Seq.empty,
  accountType: Option[String] = None,
  updatedAt: Option[ZonedDateTime]
) {

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

sealed abstract class CrmList(val value: String) extends StringEnumEntry {
  def hardBounced: Boolean
  def unsubscribed: Option[Boolean]

  def targetDirectory(csvDirectory: String): Path = {
    Paths.get(csvDirectory, value)
  }

  def actionOnHardBounce: ManageContactAction
  def actionOnOptIn: ManageContactAction
  def actionOnOptOut: ManageContactAction
}

object CrmList extends StringEnum[CrmList] {
  case object HardBounce extends CrmList("hardBounce") {
    override val hardBounced: Boolean = true
    override val unsubscribed: Option[Boolean] = None

    override val actionOnHardBounce: ManageContactAction = AddNoForce
    override val actionOnOptIn: ManageContactAction = Remove
    override val actionOnOptOut: ManageContactAction = Remove
  }

  case object OptIn extends CrmList("optIn") {
    override val hardBounced: Boolean = false
    override val unsubscribed: Option[Boolean] = Some(false)

    override val actionOnHardBounce: ManageContactAction = Remove
    override val actionOnOptIn: ManageContactAction = AddNoForce
    override val actionOnOptOut: ManageContactAction = Remove
  }

  case object OptOut extends CrmList("optOut") {
    override val hardBounced: Boolean = false
    override val unsubscribed: Option[Boolean] = Some(true)

    override val actionOnHardBounce: ManageContactAction = Remove
    override val actionOnOptIn: ManageContactAction = Remove
    override val actionOnOptOut: ManageContactAction = AddNoForce
  }

  override def values: IndexedSeq[CrmList] = findValues
}
