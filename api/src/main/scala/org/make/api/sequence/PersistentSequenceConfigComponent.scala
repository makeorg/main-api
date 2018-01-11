package org.make.api.sequence

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.sequence.DefaultPersistentSequenceConfigServiceComponent.PersistentSequenceConfig
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.sequence.SequenceId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentSequenceConfigComponent {
  def persistentSequenceConfigService: PersistentSequenceConfigService
}

trait PersistentSequenceConfigService {
  def findOne(sequenceId: SequenceId): Future[Option[SequenceConfiguration]]
  def findAll(): Future[Seq[SequenceConfiguration]]
  def persist(sequenceConfiguration: SequenceConfiguration): Future[SequenceConfiguration]
}

trait DefaultPersistentSequenceConfigServiceComponent extends PersistentSequenceConfigComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentSequenceConfigService: PersistentSequenceConfigService =
    new PersistentSequenceConfigService with ShortenedNames with StrictLogging {

      private val alias = PersistentSequenceConfig.alias
      private val column = PersistentSequenceConfig.column

      override def findOne(sequenceId: SequenceId): Future[Option[SequenceConfiguration]] = {
        implicit val context: EC = readExecutionContext
        val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL {
            select
              .from(PersistentSequenceConfig.as(alias))
              .where(sqls.eq(alias.sequenceId, sequenceId.value))
          }.map(PersistentSequenceConfig.apply()).single.apply
        })

        futurePersistentTag.map(_.map(_.toSequenceConfiguration))
      }

      override def findAll(): Future[Seq[SequenceConfiguration]] = {
        implicit val context: EC = readExecutionContext

        val futurePersistentSequenceConfig = Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL {
            select
              .from(PersistentSequenceConfig.as(alias))
          }.map(PersistentSequenceConfig.apply()).list.apply
        })

        futurePersistentSequenceConfig.map(_.map(_.toSequenceConfiguration))
      }

      def insertConfig(sequenceConfig: SequenceConfiguration): Future[SequenceConfiguration] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB('WRITE).retryableTx { implicit session =>
          withSQL {
            insert
              .into(PersistentSequenceConfig)
              .namedValues(
                column.sequenceId -> sequenceConfig.sequenceId.value,
                column.newProposalsRatio -> sequenceConfig.newProposalsRatio,
                column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold,
                column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold,
                column.banditEnabled -> sequenceConfig.banditEnabled,
                column.banditMinCount -> sequenceConfig.banditMinCount,
                column.banditProposalsRatio -> sequenceConfig.banditProposalsRatio,
                column.createdAt -> DateHelper.now,
                column.updatedAt -> DateHelper.now
              )
          }.execute().apply()
        }).map(_ => sequenceConfig)
      }

      def updateConfig(sequenceConfig: SequenceConfiguration): Future[SequenceConfiguration] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB('WRITE).retryableTx { implicit session =>
          withSQL {
            update(PersistentSequenceConfig)
              .set(column.newProposalsRatio -> sequenceConfig.newProposalsRatio)
              .set(column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold)
              .set(column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold)
              .set(column.banditEnabled -> sequenceConfig.banditEnabled)
              .set(column.banditMinCount -> sequenceConfig.banditMinCount)
              .set(column.banditProposalsRatio -> sequenceConfig.banditProposalsRatio)
              .set(column.updatedAt -> DateHelper.now)
              .where(
                sqls
                  .eq(column.sequenceId, sequenceConfig.sequenceId.value)
              )
          }.update().apply()
        }).map(_ => sequenceConfig)
      }

      override def persist(sequenceConfig: SequenceConfiguration): Future[SequenceConfiguration] = {
        findOne(sequenceConfig.sequenceId).flatMap {
          case Some(configuration) => updateConfig(sequenceConfig)
          case None                => insertConfig(sequenceConfig)
        }.map(_ => sequenceConfig)
      }
    }
}

object DefaultPersistentSequenceConfigServiceComponent {

  case class PersistentSequenceConfig(sequenceId: String,
                                      newProposalsRatio: Double,
                                      newProposalsVoteThreshold: Int,
                                      testedProposalsEngagementThreshold: Double,
                                      banditEnabled: Boolean,
                                      banditMinCount: Int,
                                      banditProposalsRatio: Double,
                                      createdAt: ZonedDateTime,
                                      updatedAt: ZonedDateTime) {
    def toSequenceConfiguration: SequenceConfiguration =
      SequenceConfiguration(
        sequenceId = SequenceId(sequenceId),
        newProposalsRatio = newProposalsRatio,
        newProposalsVoteThreshold = newProposalsVoteThreshold,
        testedProposalsEngagementThreshold = testedProposalsEngagementThreshold,
        banditEnabled = banditEnabled,
        banditMinCount = banditMinCount,
        banditProposalsRatio = banditProposalsRatio
      )
  }

  object PersistentSequenceConfig
      extends SQLSyntaxSupport[PersistentSequenceConfig]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] =
      Seq(
        "sequence_id",
        "newProposalsRatio",
        "newProposalsVoteThreshold",
        "testedProposalsEngagementThreshold",
        "banditEnabled",
        "banditMinCount",
        "banditProposalsRatio",
        "created_at",
        "updated_at"
      )

    override val tableName: String = "sequence_config"

    lazy val alias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentSequenceConfig], PersistentSequenceConfig] =
      syntax("sequence_config")

    def apply(
      resultName: ResultName[PersistentSequenceConfig] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentSequenceConfig = {
      PersistentSequenceConfig.apply(
        sequenceId = resultSet.string(resultName.sequenceId),
        newProposalsRatio = resultSet.double(resultName.newProposalsRatio),
        newProposalsVoteThreshold = resultSet.int(resultName.newProposalsVoteThreshold),
        testedProposalsEngagementThreshold = resultSet.double(resultName.testedProposalsEngagementThreshold),
        banditEnabled = resultSet.boolean(resultName.banditEnabled),
        banditMinCount = resultSet.int(resultName.banditMinCount),
        banditProposalsRatio = resultSet.double(resultName.banditProposalsRatio),
        createdAt = resultSet.zonedDateTime(resultName.createdAt),
        updatedAt = resultSet.zonedDateTime(resultName.updatedAt)
      )
    }
  }
}
