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
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}
import java.util.stream.Collectors
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.persistence.query.EventEnvelope
import akka.stream._
import akka.stream.alpakka.file.scaladsl.LogRotatorSink
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import grizzled.slf4j.Logging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import eu.timepit.refined.auto._
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.question.{QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.technical.ExecutorServiceHelper._
import org.make.api.technical.Futures._
import org.make.api.technical.crm.BasicCrmResponse.ManageManyContactsResponse
import org.make.api.technical.crm.ManageContactAction.Remove
import org.make.api.technical.job.JobActor.Protocol.Response.JobAcceptance
import org.make.api.technical.job.JobCoordinatorServiceComponent
import org.make.api.technical.{ActorSystemComponent, _}
import org.make.api.technical.crm.CrmClient.{Account, Marketing, Transactional}
import org.make.api.user.PersistentCrmSynchroUserService.CrmSynchroUser
import org.make.api.user.{PersistentCrmSynchroUserServiceComponent, PersistentUserToAnonymizeServiceComponent}
import org.make.api.userhistory._
import org.make.core.DateHelper.isLast30daysDate
import org.make.core.Validation.emailRegex
import org.make.core.job.Job.JobId.SyncCrmData
import org.make.core.question.Question
import org.make.core.session.SessionId
import org.make.core.user.{HasUserType, UserId}
import org.make.core.user.UserType._
import org.make.core.{DateHelper, Order}

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.{Failure, Success}

trait DefaultCrmServiceComponent extends CrmServiceComponent with Logging with ErrorAccumulatingCirceSupport {
  self: MailJetConfigurationComponent
    with ActorSystemComponent
    with PersistentCrmSynchroUserServiceComponent
    with OperationServiceComponent
    with QuestionServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with ReadJournalComponent
    with ProposalServiceComponent
    with PersistentCrmUserServiceComponent
    with CrmClientComponent
    with EventBusServiceComponent
    with ErrorAccumulatingCirceSupport
    with JobCoordinatorServiceComponent
    with SpawnActorServiceComponent =>

  override lazy val crmService: DefaultCrmService = new DefaultCrmService

  class DefaultCrmService extends CrmService {

    private lazy val batchSize: Int = mailJetConfiguration.userListBatchSize
    private lazy val baseCsvDirectory: String = mailJetConfiguration.csvDirectory
    private lazy val csvSize: Int = mailJetConfiguration.csvSize
    private val retrievePropertiesParallelism = 10
    private val persistCrmUsersParallelism = 5

    private val poolSize: Int = 10
    implicit private val executionContext: ExecutionContext =
      Executors
        .newFixedThreadPool(
          poolSize,
          new ThreadFactory {
            val counter = new AtomicInteger()
            override def newThread(runnable: Runnable): Thread =
              new Thread(runnable, "crm-batchs-" + counter.getAndIncrement())
          }
        )
        .instrument("crm-batchs")
        .toExecutionContext

    implicit val crmSynchroUserUserType: HasUserType[CrmSynchroUser] = _.userType

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
          logger.info(s"Sent email ${messages.toString} with reponse ${response.toString}")
        case Failure(e) =>
          logger.error(s"Sent email $messages failed", e)
          e match {
            case CrmClientException.RequestException.SendEmailException(StatusCodes.BadRequest, _) =>
            case _ =>
              actorSystem.scheduler
                .scheduleOnce(mailJetConfiguration.delayBeforeResend, () => eventBusService.publish(message))
          }
      }

      result
    }

    override def getUsersMailFromList(
      listId: Option[String] = None,
      sort: Option[String] = None,
      order: Option[Order] = None,
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

    override def createCrmUsers(): Future[Unit] = {
      for {
        _        <- persistentCrmUserService.truncateCrmUsers()
        resolver <- createQuestionResolver
        _        <- computeAndPersistCrmUsers(resolver)
      } yield ()
    }

    private def resetDirectory(directory: Path): Unit = {
      if (Files.exists(directory)) {
        Files.list(directory).forEach { file =>
          Files.deleteIfExists(file)
          ()
        }
      } else {
        Files.createDirectories(directory)
      }
      ()
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
          val path = Files.createFile(csvDirectory.resolve(s"${UUID.randomUUID().toString}.csv"))
          size = element.size
          Some(path)
        } else {
          size += element.size
          None
        }
    }

    def createCsv(formattedDate: String, list: CrmList, csvDirectory: Path): Future[Seq[Path]] = {

      def toContactProperties(user: PersistentCrmUser): ContactProperties = {
        ContactProperties(
          userId = Some(UserId(user.userId)),
          firstName = Some(user.firstname),
          postalCode = user.zipcode,
          dateOfBirth = user.dateOfBirth,
          emailValidationStatus = Some(user.emailValidationStatus),
          emailHardBounceValue = Some(user.emailHardbounceStatus),
          unsubscribeStatus = Some(user.unsubscribeStatus),
          accountCreationDate = user.accountCreationDate,
          accountCreationSource = user.accountCreationSource,
          accountCreationOrigin = user.accountCreationOrigin,
          accountCreationSlug = user.accountCreationOperation,
          accountCreationCountry = user.accountCreationCountry,
          accountCreationLocation = user.accountCreationLocation,
          countriesActivity = user.countriesActivity,
          lastCountryActivity = user.lastCountryActivity,
          totalProposals = user.totalNumberProposals,
          totalVotes = user.totalNumberVotes,
          firstContributionDate = user.firstContributionDate,
          lastContributionDate = user.lastContributionDate,
          operationActivity = user.operationActivity,
          sourceActivity = user.sourceActivity,
          daysOfActivity = user.daysOfActivity,
          daysOfActivity30 = user.daysOfActivity30d,
          userType = user.userType,
          accountType = user.accountType,
          updatedAt = Some(formattedDate),
          daysBeforeDeletion = user.daysBeforeDeletion,
          lastActivityDate = user.lastActivityDate,
          sessionsCount = user.sessionsCount,
          eventsCount = user.eventsCount
        )
      }

      StreamUtils
        .asyncPageToPageSource(persistentCrmUserService.list(list.unsubscribed, list.hardBounced, _, batchSize))
        .mapConcat(identity)
        .map { crmUser =>
          Contact(email = crmUser.email, name = Some(crmUser.fullName), properties = Some(toContactProperties(crmUser))).toStringCsv
        }
        .map(ByteString.apply(_, StandardCharsets.UTF_8))
        .runWith(LogRotatorSink(fileSizeTriggerCreator(csvDirectory)))
        .map(_ => Files.list(csvDirectory).collect(Collectors.toList[Path]).asScala.toSeq)
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
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

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
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

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
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
            responseHardBounce  <- sendCsvToHardBounceList(csv, list)
            responseOptIn       <- sendCsvToOptInList(csv, list)
            responseUnsubscribe <- sendCsvToUnsubscribeList(csv, list)
            result              <- verifyJobCompletion(responseHardBounce, responseOptIn, responseUnsubscribe)
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
      spawnActorService
        .spawn(
          behavior = CrmSynchroCsvMonitor(crmClient, promise, mailJetConfiguration.tickInterval)(jobIds),
          name = "CrmSynchroCsvMonitor"
        )
        .flatMap(_ => promise.future)
    }

    private def computeAndPersistCrmUsers(questionResolver: QuestionResolver): Future[Unit] = {
      val start = System.currentTimeMillis()
      StreamUtils
        .asyncPageToPageSource(persistentCrmSynchroUserService.findUsersForCrmSynchro(None, None, _, batchSize))
        .mapConcat(identity)
        .mapAsync(retrievePropertiesParallelism) { user =>
          getPropertiesFromUser(user, questionResolver).map { properties =>
            (user.email, user.fullName.getOrElse(user.email), properties)
          }
        }
        .groupedWithin(batchSize, 5.seconds)
        .map { contacts =>
          contacts.map {
            case (email, fullName, properties) => properties.toPersistentCrmUser(email, fullName)
          }
        }
        .mapAsync(persistCrmUsersParallelism) { crmUsers =>
          persistentCrmUserService.persist(crmUsers)
        }
        .runWith(Sink.ignore)
        .map(_ => logger.info(s"Crm users creation completed in ${System.currentTimeMillis() - start} ms"))
    }

    final def deleteAnonymizedContacts(emails: Seq[String], account: Account): Future[Unit] = {
      Source(emails)
        .mapAsync(3) { email =>
          if (email.matches(emailRegex.regex)) {
            crmClient.deleteContactByEmail(email, account).map(res => email -> res).withoutFailure
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
        .toUnit
    }

    override def anonymize(): Future[Unit] = {
      for {
        foundEmails <- persistentUserToAnonymizeService.findAll()
        _           <- hardRemoveEmailsFromAllLists(foundEmails)
        _           <- deleteAnonymizedContacts(foundEmails, Marketing)
        _           <- deleteAnonymizedContacts(foundEmails, Transactional)
      } yield ()
    }

    private def hardRemoveEmailsFromAllLists(emails: Seq[String]): Future[Unit] = {
      if (emails.isEmpty) {
        Future.unit
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

    final def getPropertiesFromUser(user: CrmSynchroUser, resolver: QuestionResolver): Future[ContactProperties] = {

      val decider: Supervision.Decider = { e =>
        logger.error(
          s"Error in stream getPropertiesFromUser for user ${user.uuid.value}. Stream resumed by dropping this element: ",
          e
        )
        Supervision.Resume
      }

      val events: Source[EventEnvelope, NotUsed] =
        userJournal
          .currentEventsByPersistenceId(
            persistenceId = user.uuid.value,
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

    private def userPropertiesFromUser(user: CrmSynchroUser, questionResolver: QuestionResolver): UserProperties = {
      val question =
        questionResolver.findQuestionWithOperation { question =>
          user.registerQuestionId.contains(question.questionId)
        }

      UserProperties(
        userId = user.uuid,
        firstname = user.firstName.getOrElse(""),
        zipCode = user.postalCode,
        dateOfBirth = user.dateOfBirth,
        emailValidationStatus = user.emailVerified,
        emailHardBounceStatus = user.isHardBounce,
        unsubscribeStatus = !user.optInNewsletter,
        accountCreationDate = user.createdAt,
        userB2B = user.isB2B,
        updatedAt = Some(DateHelper.now()),
        accountCreationCountry = Some(user.country.value),
        lastCountryActivity = Some(user.country.value),
        countriesActivity = Seq(user.country.value),
        accountCreationSlug = question.map(_.slug),
        accountType = Some(user.userType.value),
        lastActivityDate = user.lastConnection,
        sessionsIds = Set.empty,
        eventsCount = 0
      )
    }

    private def getDaysBeforeDeletionFromLastActivityDate(properties: UserProperties): Option[Int] = {
      properties.lastActivityDate.map { date =>
        val deletionDate = date.plusYears(if (properties.userB2B) 4 else 2).plusMonths(11)
        ChronoUnit.DAYS.between(ZonedDateTime.now(), deletionDate).toInt
      }
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
        accountCreationDate = Some(userProperty.accountCreationDate.format(dateFormatter)),
        accountCreationSource = userProperty.accountCreationSource,
        accountCreationOrigin = userProperty.accountCreationOrigin,
        accountCreationSlug = userProperty.accountCreationSlug,
        accountCreationCountry = userProperty.accountCreationCountry,
        accountCreationLocation = userProperty.accountCreationLocation,
        countriesActivity = Some(userProperty.countriesActivity.distinct.mkString(",")),
        lastCountryActivity = userProperty.lastCountryActivity,
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
        updatedAt = userProperty.updatedAt.map(_.format(dateFormatter)),
        daysBeforeDeletion = getDaysBeforeDeletionFromLastActivityDate(userProperty),
        lastActivityDate = userProperty.lastActivityDate.map(_.format(dateFormatter)),
        sessionsCount = Some(userProperty.sessionsIds.size),
        eventsCount = Some(userProperty.eventsCount)
      )
    }

    private def accumulateEvent(
      accumulator: UserProperties,
      envelope: EventEnvelope,
      questionResolver: QuestionResolver
    ): Future[UserProperties] = {
      envelope.event match {
        case event: LogUserConnectedEvent =>
          Future.successful(accumulateLogUserConnectedEvent(accumulator, event))
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
        case event: LogUserStartSequenceEvent =>
          Future.successful(accumulateLogUserStartSequenceEvent(accumulator, event))
        case _ => Future.successful(accumulator)
      }
    }

    private def accumulateLogUserConnectedEvent(
      accumulator: UserProperties,
      event: LogUserConnectedEvent
    ): UserProperties = {

      accumulator.copy(
        lastActivityDate = accumulator.lastActivityDate.max(Some(event.action.date)),
        sessionsIds = accumulator.sessionsIds ++ Set(event.requestContext.sessionId),
        eventsCount = accumulator.eventsCount + 1
      )
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
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toList,
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity
          },
          lastActivityDate = accumulator.lastActivityDate.max(Some(event.action.date)),
          sessionsIds = accumulator.sessionsIds ++ Set(event.requestContext.sessionId),
          eventsCount = accumulator.eventsCount + 1
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
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toList,
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          },
          lastActivityDate = accumulator.lastActivityDate.max(Some(event.action.date)),
          sessionsIds = accumulator.sessionsIds ++ Set(event.requestContext.sessionId),
          eventsCount = accumulator.eventsCount + 1
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
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toList,
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          },
          lastActivityDate = accumulator.lastActivityDate.max(Some(event.action.date)),
          sessionsIds = accumulator.sessionsIds ++ Set(event.requestContext.sessionId),
          eventsCount = accumulator.eventsCount + 1
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
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value),
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toList,
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
          firstContributionDate = accumulator.firstContributionDate.orElse(Option(event.action.date)),
          lastContributionDate = Some(event.action.date),
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          },
          lastActivityDate = accumulator.lastActivityDate.max(Some(event.action.date)),
          sessionsIds = accumulator.sessionsIds ++ Set(event.requestContext.sessionId),
          eventsCount = accumulator.eventsCount + 1
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
          countriesActivity = accumulator.countriesActivity ++ event.requestContext.country.map(_.value).toList,
          questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toList,
          sourceActivity = accumulator.sourceActivity ++ event.requestContext.source.toList,
          firstContributionDate = accumulator.firstContributionDate.orElse(Option(event.action.date)),
          lastContributionDate = Some(event.action.date),
          daysOfActivity = accumulator.daysOfActivity ++ Some(event.action.date.format(dayDateFormatter)),
          daysOfActivity30d = if (isLast30daysDate(event.action.date)) {
            accumulator.daysOfActivity30d ++ Some(event.action.date.format(dayDateFormatter))
          } else {
            accumulator.daysOfActivity30d
          },
          lastActivityDate = accumulator.lastActivityDate.max(Some(event.action.date)),
          sessionsIds = accumulator.sessionsIds ++ Set(event.requestContext.sessionId),
          eventsCount = accumulator.eventsCount + 1
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
        questionActivity = accumulator.questionActivity ++ maybeQuestion.map(_.slug).toList,
        sourceActivity = accumulator.sourceActivity ++ event.requestContext.source,
        lastActivityDate = accumulator.lastActivityDate.max(Some(event.action.date)),
        sessionsIds = accumulator.sessionsIds ++ Set(event.requestContext.sessionId),
        eventsCount = accumulator.eventsCount + 1
      )
    }

    private def accumulateLogUserStartSequenceEvent(
      accumulator: UserProperties,
      event: LogUserStartSequenceEvent
    ): UserProperties = {
      accumulator.copy(
        lastActivityDate = accumulator.lastActivityDate.max(Some(event.action.date)),
        sessionsIds = accumulator.sessionsIds ++ Set(event.requestContext.sessionId),
        eventsCount = accumulator.eventsCount + 1
      )
    }

    private def logMailjetResponse(result: Either[Throwable, ManageManyContactsResponse], listName: String): Unit = {
      result match {
        case Right(ok) => logger.debug(s"Synchronizing list $listName answered ${ok.toString}")
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
  accountCreationDate: ZonedDateTime,
  userB2B: Boolean,
  accountCreationSource: Option[String] = None,
  accountCreationOrigin: Option[String] = None,
  accountCreationSlug: Option[String] = None,
  accountCreationCountry: Option[String] = None,
  accountCreationLocation: Option[String] = None,
  countriesActivity: Seq[String] = Seq.empty,
  lastCountryActivity: Option[String] = None,
  totalNumberProposals: Option[Int] = None,
  totalNumbervotes: Option[Int] = None,
  firstContributionDate: Option[ZonedDateTime] = None,
  lastContributionDate: Option[ZonedDateTime] = None,
  questionActivity: Seq[String] = Seq.empty,
  sourceActivity: Seq[String] = Seq.empty,
  daysOfActivity: Seq[String] = Seq.empty,
  daysOfActivity30d: Seq[String] = Seq.empty,
  accountType: Option[String] = None,
  updatedAt: Option[ZonedDateTime],
  lastActivityDate: Option[ZonedDateTime],
  sessionsIds: Set[SessionId],
  eventsCount: Int
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
      case (None, date) if date.isBefore(sourceFixDate) =>
        this.copy(
          accountCreationSource = Some("core"),
          accountCreationCountry = accountCreationCountry.orElse(Some("FR")),
          countriesActivity = if (countriesActivity.isEmpty) Seq("FR") else countriesActivity,
          lastCountryActivity = lastCountryActivity.orElse(Some("FR")),
          sourceActivity = (sourceActivity ++ Some("core")).distinct
        )
      case _ => this
    }
  }
}
