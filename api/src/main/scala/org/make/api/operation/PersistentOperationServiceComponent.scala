package org.make.api.operation

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.operation.DefaultPersistentOperationServiceComponent.{
  PersistentOperation,
  PersistentOperationAction,
  PersistentOperationCountryConfiguration,
  PersistentOperationTranslation
}
import org.make.api.tag.DefaultPersistentTagServiceComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.reference.TagId
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId
import scalikejdbc._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.Future

trait PersistentOperationServiceComponent {
  def persistentOperationService: PersistentOperationService
}

trait PersistentOperationService {
  def find(slug: Option[String] = None): Future[Seq[Operation]]
  def findSimpleOperation(slug: Option[String] = None): Future[Seq[SimpleOperation]]
  def getById(operationId: OperationId): Future[Option[Operation]]
  def getBySlug(slug: String): Future[Option[Operation]]
  def persist(operation: Operation): Future[Operation]
  def modify(operation: Operation): Future[Operation]
  def addTranslationToOperation(translation: OperationTranslation, operation: Operation): Future[Boolean]
  def addActionToOperation(action: OperationAction, operation: Operation): Future[Boolean]
  def addCountryConfigurationToOperation(countryConfiguration: OperationCountryConfiguration,
                                         operation: Operation): Future[Boolean]
}

trait DefaultPersistentOperationServiceComponent extends PersistentOperationServiceComponent {
  this: MakeDBExecutionContextComponent with DefaultPersistentTagServiceComponent =>

  override lazy val persistentOperationService: PersistentOperationService = new PersistentOperationService
  with ShortenedNames with StrictLogging {

    private val operationAlias = PersistentOperation.operationAlias
    private val operationTranslationAlias = PersistentOperationTranslation.operationTranslationAlias
    private val operationActionAlias = PersistentOperationAction.operationActionAlias
    private val operationCountryConfigurationAlias =
      PersistentOperationCountryConfiguration.operationCountryConfigurationAlias
    private val column = PersistentOperation.column
    private val operationTranslationColumn = PersistentOperationTranslation.column
    private val operationActionColumn = PersistentOperationAction.column
    private val operationCountryConfigurationColumn = PersistentOperationCountryConfiguration.column
    private val baseSelect = select
      .from(PersistentOperation.as(operationAlias))
      .leftJoin(PersistentOperationTranslation.as(operationTranslationAlias))
      .on(operationAlias.uuid, operationTranslationAlias.operationUuid)
      .leftJoin(PersistentOperationAction.as(operationActionAlias))
      .on(operationAlias.uuid, operationActionAlias.operationUuid)
      .leftJoin(PersistentOperationCountryConfiguration.as(operationCountryConfigurationAlias))
      .on(operationAlias.uuid, operationCountryConfigurationAlias.operationUuid)

    override def find(slug: Option[String] = None): Future[Seq[Operation]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentOperations: Future[List[PersistentOperation]] = Future(NamedDB('READ).retryableTx {
        implicit session =>
          withSQL {
            baseSelect
              .copy()
              .where(sqls.toAndConditionOpt(slug.map(slug => sqls.eq(operationAlias.slug, slug))))
          }.one(PersistentOperation.apply())
            .toManies(
              resultSet => PersistentOperationTranslation.opt(operationTranslationAlias)(resultSet),
              resultSet => PersistentOperationAction.opt(operationActionAlias)(resultSet),
              resultSet => PersistentOperationCountryConfiguration.opt(operationCountryConfigurationAlias)(resultSet)
            )
            .map {
              (operation: PersistentOperation,
               translations: Seq[PersistentOperationTranslation],
               actions: Seq[PersistentOperationAction],
               countryConfigurations: Seq[PersistentOperationCountryConfiguration]) =>
                operation.copy(
                  operationActions = actions,
                  operationTranslations = translations,
                  operationCountryConfigurations = countryConfigurations
                )
            }
            .list
            .apply()
      })

      futurePersistentOperations.map(_.map(_.toOperation))
    }

    override def findSimpleOperation(slug: Option[String] = None): Future[Seq[SimpleOperation]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentOperations: Future[List[PersistentOperation]] = Future(NamedDB('READ).retryableTx {
        implicit session =>
          withSQL {
            baseSelect
              .copy()
              .where(sqls.toAndConditionOpt(slug.map(slug => sqls.eq(operationAlias.slug, slug))))
          }.map(PersistentOperation.apply()).list.apply()
      })

      futurePersistentOperations.map(_.map(_.toSimpleOperation))
    }

    override def persist(operation: Operation): Future[Operation] = {
      implicit val context: EC = writeExecutionContext
      val nowDate: ZonedDateTime = DateHelper.now()
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentOperation)
            .namedValues(
              column.uuid -> operation.operationId.value,
              column.status -> operation.status.shortName,
              column.slug -> operation.slug,
              column.defaultLanguage -> operation.defaultLanguage,
              column.createdAt -> nowDate,
              column.updatedAt -> nowDate
            )
        }.execute().apply()
      }).flatMap { _ =>
        for {
          resultTranslation <- Future.traverse(operation.translations)(
            translation => addTranslationToOperation(translation = translation, operation = operation)
          )
          resultAction <- Future.traverse(operation.events)(
            event => addActionToOperation(action = event, operation = operation)
          )
          resultCountryConfiguration <- Future.traverse(operation.countriesConfiguration)(
            countryConfiguration =>
              addCountryConfigurationToOperation(countryConfiguration = countryConfiguration, operation = operation)
          )
        } yield (resultTranslation ++ resultAction ++ resultCountryConfiguration).reduce(_ & _)

      }.map(_ => operation.copy(createdAt = Some(nowDate), updatedAt = Some(nowDate)))
    }

    override def getById(operationId: OperationId): Future[Option[Operation]] = {
      implicit val context: EC = readExecutionContext
      val futureMaybePersistentOperation: Future[Option[PersistentOperation]] = Future(NamedDB('READ).retryableTx {
        implicit session =>
          withSQL {
            baseSelect
              .copy()
              .where(sqls.eq(operationAlias.uuid, operationId.value))
          }.one(PersistentOperation.apply())
            .toManies(
              resultSet => PersistentOperationTranslation.opt(operationTranslationAlias)(resultSet),
              resultSet => PersistentOperationAction.opt(operationActionAlias)(resultSet),
              resultSet => PersistentOperationCountryConfiguration.opt(operationCountryConfigurationAlias)(resultSet)
            )
            .map {
              (operation: PersistentOperation,
               translations: Seq[PersistentOperationTranslation],
               actions: Seq[PersistentOperationAction],
               countryConfigurations: Seq[PersistentOperationCountryConfiguration]) =>
                operation.copy(
                  operationActions = actions,
                  operationTranslations = translations,
                  operationCountryConfigurations = countryConfigurations
                )
            }
            .single
            .apply()
      })

      futureMaybePersistentOperation.map(_.map(_.toOperation))
    }

    override def getBySlug(slug: String): Future[Option[Operation]] = {
      implicit val context: EC = readExecutionContext
      val futureMaybePersistentOperation: Future[Option[PersistentOperation]] = Future(NamedDB('READ).retryableTx {
        implicit session =>
          withSQL {
            baseSelect
              .copy()
              .where(sqls.eq(operationAlias.slug, slug))
          }.one(PersistentOperation.apply())
            .toManies(
              resultSet => PersistentOperationTranslation.opt(operationTranslationAlias)(resultSet),
              resultSet => PersistentOperationAction.opt(operationActionAlias)(resultSet),
              resultSet => PersistentOperationCountryConfiguration.opt(operationCountryConfigurationAlias)(resultSet)
            )
            .map {
              (operation: PersistentOperation,
               translations: Seq[PersistentOperationTranslation],
               actions: Seq[PersistentOperationAction],
               countryConfigurations: Seq[PersistentOperationCountryConfiguration]) =>
                operation.copy(
                  operationActions = actions,
                  operationTranslations = translations,
                  operationCountryConfigurations = countryConfigurations
                )
            }
            .single
            .apply()
      })

      futureMaybePersistentOperation.map(_.map(_.toOperation))
    }

    override def modify(operation: Operation): Future[Operation] = {
      implicit val ctx: EC = writeExecutionContext
      val nowDate: ZonedDateTime = DateHelper.now()
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentOperation)
            .set(
              column.status -> operation.status.shortName,
              column.slug -> operation.slug,
              column.defaultLanguage -> operation.defaultLanguage,
              column.updatedAt -> nowDate
            )
            .where(
              sqls
                .eq(column.uuid, operation.operationId.value)
            )
        }.executeUpdate().apply()
      }).flatMap {
        case 1 =>
          for {
            updatedTranslationCount          <- updateTranslationsOfOperation(operation = operation)
            updatedActionCount               <- updateActionOfOperation(operation = operation)
            updatedCountryConfigurationCount <- updateCountryConfigurationOfOperation(operation = operation)
          } yield (updatedTranslationCount, updatedActionCount, updatedCountryConfigurationCount)
        case 0 =>
          logger.error(s"Operation '${operation.operationId.value}' not found")
          Future.successful(false)
        case _ =>
          logger.error(s"update of operation '${operation.operationId.value}' failed - not found")
          Future.successful(false)
      }.map {
        case (updatedTranslationCount, updatedActionCount, updatedCountryConfigurationCount) =>
          if (updatedTranslationCount != operation.translations.size) {
            throw new Exception(
              s"""Expected ${operation.translations.size} translations updated and get $updatedTranslationCount"""
            )
          }
          if (updatedActionCount != operation.events.size) {
            throw new Exception(s"""Expected ${operation.events.size} events updated and get $updatedActionCount""")
          }
          if (updatedCountryConfigurationCount != operation.countriesConfiguration.size) {
            throw new Exception(s"""Expected ${operation.countriesConfiguration.size}
                 | events updated and get $updatedCountryConfigurationCount""".stripMargin)
          }
          operation.copy(updatedAt = Some(nowDate))
      }
    }

    override def addActionToOperation(action: OperationAction, operation: Operation): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentOperationAction)
            .namedValues(
              operationActionColumn.operationUuid -> operation.operationId.value,
              operationActionColumn.makeUserUuid -> action.makeUserId.value,
              operationActionColumn.actionDate -> action.date,
              operationActionColumn.actionType -> action.actionType,
              operationActionColumn.arguments -> action.arguments.toJson.compactPrint
            )
        }.execute().apply()
      })
    }

    override def addCountryConfigurationToOperation(countryConfiguration: OperationCountryConfiguration,
                                                    operation: Operation): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentOperationCountryConfiguration)
            .namedValues(
              operationCountryConfigurationColumn.operationUuid -> operation.operationId.value,
              operationCountryConfigurationColumn.country -> countryConfiguration.countryCode,
              operationCountryConfigurationColumn.tagIds -> countryConfiguration.tagIds
                .map(_.value)
                .mkString(PersistentOperationCountryConfiguration.TAG_SEPARATOR.toString),
              operationCountryConfigurationColumn.landingSequenceId -> countryConfiguration.landingSequenceId.value,
              operationCountryConfigurationColumn.createdAt -> DateHelper.now(),
              operationCountryConfigurationColumn.updatedAt -> DateHelper.now()
            )
        }.execute().apply()
      })
    }

    override def addTranslationToOperation(translation: OperationTranslation, operation: Operation): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentOperationTranslation)
            .namedValues(
              operationTranslationColumn.operationUuid -> operation.operationId.value,
              operationTranslationColumn.title -> translation.title,
              operationTranslationColumn.language -> translation.language
            )
        }.execute().apply()
      })
    }

    private def updateTranslationsOfOperation(operation: Operation): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentOperationTranslation)
            .where(
              sqls
                .eq(operationTranslationColumn.operationUuid, operation.operationId.value)
            )
        }.execute().apply()
      }).flatMap(
          _ =>
            Future.traverse(operation.translations)(
              translation => addTranslationToOperation(translation = translation, operation = operation)
          )
        )
        .map(_.size)
    }

    private def updateActionOfOperation(operation: Operation): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentOperationAction)
            .where(
              sqls
                .eq(operationActionColumn.operationUuid, operation.operationId.value)
            )
        }.execute().apply()
      }).flatMap(
          _ => Future.traverse(operation.events)(action => addActionToOperation(action = action, operation = operation))
        )
        .map(_.size)
    }

    private def updateCountryConfigurationOfOperation(operation: Operation): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          delete
            .from(PersistentOperationCountryConfiguration)
            .where(
              sqls
                .eq(operationCountryConfigurationColumn.operationUuid, operation.operationId.value)
            )
        }.execute().apply()
      }).flatMap(
          _ =>
            Future.traverse(operation.countriesConfiguration)(
              countryConfiguration =>
                addCountryConfigurationToOperation(countryConfiguration = countryConfiguration, operation = operation)
          )
        )
        .map(_.size)
    }
  }
}

object DefaultPersistentOperationServiceComponent {

  case class PersistentOperationTranslation(operationUuid: String,
                                            title: String,
                                            language: String,
                                            createdAt: ZonedDateTime,
                                            updatedAt: ZonedDateTime)

  case class PersistentOperationCountryConfiguration(operationUuid: String,
                                                     country: String,
                                                     tagIds: Option[String],
                                                     landingSequenceId: String,
                                                     createdAt: ZonedDateTime,
                                                     updatedAt: ZonedDateTime)

  case class PersistentOperationAction(operationUuid: String,
                                       makeUserUuid: String,
                                       actionDate: ZonedDateTime,
                                       actionType: String,
                                       arguments: Option[String])

  case class PersistentOperation(uuid: String,
                                 operationTranslations: Seq[PersistentOperationTranslation],
                                 operationCountryConfigurations: Seq[PersistentOperationCountryConfiguration],
                                 operationActions: Seq[PersistentOperationAction],
                                 status: String,
                                 slug: String,
                                 defaultLanguage: String,
                                 createdAt: ZonedDateTime,
                                 updatedAt: ZonedDateTime) {

    def toOperation: Operation =
      Operation(
        operationId = OperationId(uuid),
        createdAt = Some(createdAt),
        updatedAt = Some(updatedAt),
        status = OperationStatus.statusMap(status),
        slug = slug,
        defaultLanguage = defaultLanguage,
        events = operationActions
          .map(
            action =>
              OperationAction(
                date = action.actionDate,
                makeUserId = UserId(action.makeUserUuid),
                actionType = action.actionType,
                arguments = action.arguments.getOrElse("{}").parseJson.convertTo[Map[String, String]]
            )
          )
          .toList,
        countriesConfiguration = operationCountryConfigurations.map(
          countryConfiguration =>
            OperationCountryConfiguration(
              countryCode = countryConfiguration.country,
              tagIds = countryConfiguration.tagIds
                .getOrElse("")
                .split(PersistentOperationCountryConfiguration.TAG_SEPARATOR)
                .map(tagId => TagId(tagId))
                .filter(value => value != TagId("")),
              landingSequenceId = SequenceId(countryConfiguration.landingSequenceId)
          )
        ),
        translations =
          operationTranslations.map(trans => OperationTranslation(title = trans.title, language = trans.language))
      )

    def toSimpleOperation: SimpleOperation =
      SimpleOperation(
        operationId = OperationId(uuid),
        status = OperationStatus.statusMap(status),
        slug = slug,
        defaultLanguage = defaultLanguage,
        createdAt = Some(createdAt),
        updatedAt = Some(updatedAt)
      )
  }

  object PersistentOperationTranslation
      extends SQLSyntaxSupport[PersistentOperationTranslation]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] = Seq("operation_uuid", "title", "language", "created_at", "updated_at")

    override val tableName: String = "operation_translation"

    lazy val operationTranslationAlias
      : QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentOperationTranslation], PersistentOperationTranslation] =
      syntax("ot")

    def opt(
      operationTranslation: SyntaxProvider[PersistentOperationTranslation]
    )(resultSet: WrappedResultSet): Option[PersistentOperationTranslation] =
      resultSet
        .stringOpt(operationTranslation.resultName.operationUuid)
        .map(_ => PersistentOperationTranslation(operationTranslation.resultName)(resultSet))

    def apply(operationTranslationResultName: ResultName[PersistentOperationTranslation] =
                operationTranslationAlias.resultName)(resultSet: WrappedResultSet): PersistentOperationTranslation = {
      PersistentOperationTranslation.apply(
        operationUuid = resultSet.string(operationTranslationResultName.operationUuid),
        title = resultSet.string(operationTranslationResultName.title),
        language = resultSet.string(operationTranslationResultName.language),
        createdAt = resultSet.zonedDateTime(operationTranslationResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(operationTranslationResultName.updatedAt)
      )
    }
  }

  object PersistentOperationCountryConfiguration
      extends SQLSyntaxSupport[PersistentOperationCountryConfiguration]
      with ShortenedNames
      with StrictLogging {

    val TAG_SEPARATOR = '|'

    override val columnNames: Seq[String] =
      Seq("operation_uuid", "country", "tag_ids", "landing_sequence_id", "created_at", "updated_at")

    override val tableName: String = "operation_country_configuration"

    lazy val operationCountryConfigurationAlias
      : QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentOperationCountryConfiguration],
                               PersistentOperationCountryConfiguration] =
      syntax("occ")

    def opt(
      operationCountryConfiguration: SyntaxProvider[PersistentOperationCountryConfiguration]
    )(resultSet: WrappedResultSet): Option[PersistentOperationCountryConfiguration] =
      resultSet
        .stringOpt(operationCountryConfiguration.resultName.operationUuid)
        .map(_ => PersistentOperationCountryConfiguration(operationCountryConfiguration.resultName)(resultSet))

    def apply(
      operationCountryConfigurationResultName: ResultName[PersistentOperationCountryConfiguration] =
        operationCountryConfigurationAlias.resultName
    )(resultSet: WrappedResultSet): PersistentOperationCountryConfiguration = {
      PersistentOperationCountryConfiguration.apply(
        operationUuid = resultSet.string(operationCountryConfigurationResultName.operationUuid),
        country = resultSet.string(operationCountryConfigurationResultName.country),
        tagIds = resultSet.stringOpt(operationCountryConfigurationResultName.tagIds),
        landingSequenceId = resultSet.string(operationCountryConfigurationResultName.landingSequenceId),
        createdAt = resultSet.zonedDateTime(operationCountryConfigurationResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(operationCountryConfigurationResultName.updatedAt)
      )
    }
  }

  object PersistentOperationAction
      extends SQLSyntaxSupport[PersistentOperationAction]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] =
      Seq("operation_uuid", "make_user_uuid", "action_date", "action_type", "arguments")

    override val tableName: String = "operation_action"

    lazy val operationActionAlias
      : QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentOperationAction], PersistentOperationAction] =
      syntax("oa")

    def opt(
      operationAction: SyntaxProvider[PersistentOperationAction]
    )(resultSet: WrappedResultSet): Option[PersistentOperationAction] =
      resultSet
        .stringOpt(operationAction.resultName.operationUuid)
        .map(_ => PersistentOperationAction(operationAction.resultName)(resultSet))

    def apply(
      operationActionResultName: ResultName[PersistentOperationAction] = operationActionAlias.resultName
    )(resultSet: WrappedResultSet): PersistentOperationAction = {
      PersistentOperationAction.apply(
        operationUuid = resultSet.string(operationActionResultName.operationUuid),
        makeUserUuid = resultSet.string(operationActionResultName.makeUserUuid),
        actionDate = resultSet.zonedDateTime(operationActionResultName.actionDate),
        actionType = resultSet.string(operationActionResultName.actionType),
        arguments = resultSet.stringOpt(operationActionResultName.arguments)
      )
    }
  }

  object PersistentOperation extends SQLSyntaxSupport[PersistentOperation] with ShortenedNames with StrictLogging {
    override val columnNames: Seq[String] =
      Seq("uuid", "status", "slug", "default_language", "created_at", "updated_at")

    override val tableName: String = "operation"

    lazy val operationAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentOperation], PersistentOperation] =
      syntax("op")

    def apply(
      operationResultName: ResultName[PersistentOperation] = operationAlias.resultName
    )(resultSet: WrappedResultSet): PersistentOperation = {
      PersistentOperation.apply(
        uuid = resultSet.string(operationResultName.uuid),
        status = resultSet.string(operationResultName.status),
        slug = resultSet.string(operationResultName.slug),
        defaultLanguage = resultSet.string(operationResultName.defaultLanguage),
        operationActions = Seq.empty,
        operationTranslations = Seq.empty,
        operationCountryConfigurations = Seq.empty,
        createdAt = resultSet.zonedDateTime(operationResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(operationResultName.updatedAt)
      )
    }
  }

}
