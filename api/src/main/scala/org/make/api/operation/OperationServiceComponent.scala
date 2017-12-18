package org.make.api.operation

import org.make.api.technical.ShortenedNames
import org.make.core.operation._
import org.make.core.user.UserId
import io.circe.syntax._
import org.make.api.MakeMain
import org.make.core.DateHelper
import org.make.core.sequence.SequenceId

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait OperationServiceComponent {
  def operationService: OperationService
}

trait OperationService extends ShortenedNames {
  def findAll(): Future[Seq[Operation]]
  def findOne(operationId: OperationId): Future[Option[Operation]]
  def create(userId: UserId,
             slug: String,
             translations: Seq[OperationTranslation] = Seq.empty,
             defaultLanguage: String,
             sequenceLandingId: SequenceId,
             countriesConfiguration: Seq[OperationCountryConfiguration]): Future[OperationId]
  def update(operationId: OperationId,
             userId: UserId,
             slug: Option[String] = None,
             translations: Option[Seq[OperationTranslation]] = None,
             defaultLanguage: Option[String] = None,
             sequenceLandingId: Option[SequenceId] = None,
             countriesConfiguration: Option[Seq[OperationCountryConfiguration]] = None,
             status: Option[OperationStatus] = None): Future[Option[OperationId]]
  def activate(operationId: OperationId, userId: UserId): Unit
  def archive(operationId: OperationId, userId: UserId): Unit
}

trait DefaultOperationServiceComponent extends OperationServiceComponent with ShortenedNames {
  this: PersistentOperationServiceComponent =>

  val operationService: OperationService = new OperationService {

    override def findAll(): Future[Seq[Operation]] = {
      persistentOperationService.findAll()
    }

    override def findOne(operationId: OperationId): Future[Option[Operation]] = {
      persistentOperationService.getById(operationId)
    }

    override def create(userId: UserId,
                        slug: String,
                        translations: Seq[OperationTranslation] = Seq.empty,
                        defaultLanguage: String,
                        sequenceLandingId: SequenceId,
                        countriesConfiguration: Seq[OperationCountryConfiguration]): Future[OperationId] = {
      val now = DateHelper.now()
      val operation: Operation = Operation(
        operationId = OperationId(MakeMain.idGenerator.nextId()),
        status = OperationStatus.Pending,
        slug = slug,
        translations = translations,
        defaultLanguage = defaultLanguage,
        sequenceLandingId = sequenceLandingId,
        countriesConfiguration = countriesConfiguration,
        events = List.empty,
        createdAt = Some(now),
        updatedAt = Some(now)
      )
      persistentOperationService
        .persist(
          operation.copy(
            events = List(
              OperationAction(
                makeUserId = userId,
                actionType = OperationCreateAction.name,
                arguments = Map("operation" -> operationToString(operation))
              )
            )
          )
        )
        .map(_.operationId)
    }

    override def update(operationId: OperationId,
                        userId: UserId,
                        slug: Option[String] = None,
                        translations: Option[Seq[OperationTranslation]] = None,
                        defaultLanguage: Option[String] = None,
                        sequenceLandingId: Option[SequenceId] = None,
                        countriesConfiguration: Option[Seq[OperationCountryConfiguration]] = None,
                        status: Option[OperationStatus] = None): Future[Option[OperationId]] = {

      val now = DateHelper.now()
      persistentOperationService
        .getById(operationId)
        .flatMap(_.map { registeredOperation =>
          val operationUpdated = registeredOperation.copy(
            slug = slug.getOrElse(registeredOperation.slug),
            translations = translations.getOrElse(registeredOperation.translations),
            defaultLanguage = defaultLanguage.getOrElse(registeredOperation.defaultLanguage),
            sequenceLandingId = sequenceLandingId.getOrElse(registeredOperation.sequenceLandingId),
            countriesConfiguration = countriesConfiguration.getOrElse(registeredOperation.countriesConfiguration),
            status = status.getOrElse(registeredOperation.status),
            updatedAt = Some(now)
          )
          persistentOperationService
            .modify(
              operationUpdated.copy(
                events = OperationAction(
                  makeUserId = userId,
                  actionType = OperationUpdateAction.name,
                  arguments = Map("operation" -> operationToString(operationUpdated))
                ) :: registeredOperation.events
              )
            )
            .map(operation => Some(operation.operationId))
        }.getOrElse(Future.successful(None)))
    }

    override def activate(operationId: OperationId, userId: UserId): Unit = {
      persistentOperationService
        .getById(operationId)
        .map(_.map { operation =>
          val operationUpdated: Operation =
            operation.copy(status = OperationStatus.Active, updatedAt = Some(DateHelper.now()))

          persistentOperationService.modify(
            operationUpdated.copy(
              events = OperationAction(
                makeUserId = userId,
                actionType = OperationActivateAction.name,
                arguments = Map("operation" -> operationToString(operationUpdated))
              ) :: operation.events
            )
          )
        })
    }

    override def archive(operationId: OperationId, userId: UserId): Unit = {
      persistentOperationService
        .getById(operationId)
        .map(_.map { operation =>
          val operationUpdated = operation.copy(status = OperationStatus.Archived, updatedAt = Some(DateHelper.now()))

          persistentOperationService.modify(
            operationUpdated.copy(
              events = OperationAction(
                makeUserId = userId,
                actionType = OperationArchiveAction.name,
                arguments = Map("operation" -> operationToString(operationUpdated))
              ) :: operation.events
            )
          )
        })
    }

    private def operationToString(operation: Operation): String = {
      Map(
        "operationId" -> operation.operationId.value,
        "status" -> operation.status.shortName,
        "translations" -> operation.translations
          .map(translation => s"${translation.language}:${translation.title}")
          .mkString(","),
        "defaultLanguage" -> operation.defaultLanguage,
        "sequenceLandingId" -> operation.sequenceLandingId.value,
        "countriesConfiguration" -> operation.countriesConfiguration
          .map(
            countryConfiguration =>
              s"${countryConfiguration.countryCode}:${countryConfiguration.tagIds.map(_.value).mkString("[", ",", "]")}"
          )
          .mkString(",")
      ).asJson.toString
    }
  }
}
