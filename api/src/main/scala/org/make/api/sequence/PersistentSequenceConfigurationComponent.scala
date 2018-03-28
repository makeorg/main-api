package org.make.api.sequence

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.sequence.DefaultPersistentSequenceConfigurationServiceComponent.PersistentSequenceConfiguration
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.sequence.SequenceId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentSequenceConfigurationComponent {
  def persistentSequenceConfigurationService: PersistentSequenceConfigurationService
}

trait PersistentSequenceConfigurationService {
  def findOne(sequenceId: SequenceId): Future[Option[SequenceConfiguration]]
  def findAll(): Future[Seq[SequenceConfiguration]]
  def persist(sequenceConfiguration: SequenceConfiguration): Future[Boolean]
}

trait DefaultPersistentSequenceConfigurationServiceComponent extends PersistentSequenceConfigurationComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentSequenceConfigurationService: PersistentSequenceConfigurationService =
    new PersistentSequenceConfigurationService with ShortenedNames with StrictLogging {

      private val alias = PersistentSequenceConfiguration.alias
      private val column = PersistentSequenceConfiguration.column

      override def findOne(sequenceId: SequenceId): Future[Option[SequenceConfiguration]] = {
        implicit val context: EC = readExecutionContext
        val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL {
            select
              .from(PersistentSequenceConfiguration.as(alias))
              .where(sqls.eq(alias.sequenceId, sequenceId.value))
          }.map(PersistentSequenceConfiguration.apply()).single.apply
        })

        futurePersistentTag.map(_.map(_.toSequenceConfiguration))
      }

      override def findAll(): Future[Seq[SequenceConfiguration]] = {
        implicit val context: EC = readExecutionContext

        val futurePersistentSequenceConfig = Future(NamedDB('READ).retryableTx { implicit session =>
          withSQL {
            select
              .from(PersistentSequenceConfiguration.as(alias))
          }.map(PersistentSequenceConfiguration.apply()).list.apply
        })

        futurePersistentSequenceConfig.map(_.map(_.toSequenceConfiguration))
      }

      def insertConfig(sequenceConfig: SequenceConfiguration): Future[Boolean] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB('WRITE).retryableTx { implicit session =>
          withSQL {
            insert
              .into(PersistentSequenceConfiguration)
              .namedValues(
                column.sequenceId -> sequenceConfig.sequenceId.value,
                column.newProposalsRatio -> sequenceConfig.newProposalsRatio,
                column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold,
                column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold,
                column.testedProposalsScoreThreshold -> sequenceConfig.testedProposalsScoreThreshold,
                column.testedProposalsControversyThreshold -> sequenceConfig.testedProposalsControversyThreshold,
                column.banditEnabled -> sequenceConfig.banditEnabled,
                column.banditMinCount -> sequenceConfig.banditMinCount,
                column.banditProposalsRatio -> sequenceConfig.banditProposalsRatio,
                column.createdAt -> DateHelper.now,
                column.updatedAt -> DateHelper.now
              )
          }.execute().apply()
        })
      }

      def updateConfig(sequenceConfig: SequenceConfiguration): Future[Int] = {
        implicit val context: EC = writeExecutionContext
        Future(NamedDB('WRITE).retryableTx { implicit session =>
          withSQL {
            update(PersistentSequenceConfiguration)
              .set(
                column.newProposalsRatio -> sequenceConfig.newProposalsRatio,
                column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold,
                column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold,
                column.testedProposalsScoreThreshold -> sequenceConfig.testedProposalsScoreThreshold,
                column.testedProposalsControversyThreshold -> sequenceConfig.testedProposalsControversyThreshold,
                column.banditEnabled -> sequenceConfig.banditEnabled,
                column.banditMinCount -> sequenceConfig.banditMinCount,
                column.banditProposalsRatio -> sequenceConfig.banditProposalsRatio,
                column.updatedAt -> DateHelper.now
              )
              .where(
                sqls
                  .eq(column.sequenceId, sequenceConfig.sequenceId.value)
              )
          }.update().apply()
        })
      }

      override def persist(sequenceConfig: SequenceConfiguration): Future[Boolean] = {
        implicit val context: EC = readExecutionContext
        findOne(sequenceConfig.sequenceId).flatMap {
          case Some(_) => updateConfig(sequenceConfig)
          case None    => insertConfig(sequenceConfig)
        }.map {
          case result: Boolean => result
          case result: Int     => result == 1
        }
      }
    }
}

object DefaultPersistentSequenceConfigurationServiceComponent {

  case class PersistentSequenceConfiguration(sequenceId: String,
                                             newProposalsRatio: Double,
                                             newProposalsVoteThreshold: Int,
                                             testedProposalsEngagementThreshold: Double,
                                             testedProposalsScoreThreshold: Double,
                                             testedProposalsControversyThreshold: Double,
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
        testedProposalsScoreThreshold = testedProposalsScoreThreshold,
        testedProposalsControversyThreshold = testedProposalsControversyThreshold,
        banditEnabled = banditEnabled,
        banditMinCount = banditMinCount,
        banditProposalsRatio = banditProposalsRatio
      )
  }

  object PersistentSequenceConfiguration
      extends SQLSyntaxSupport[PersistentSequenceConfiguration]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] =
      Seq(
        "sequence_id",
        "new_proposals_ratio",
        "new_proposals_vote_threshold",
        "tested_proposals_engagement_threshold",
        "tested_proposals_score_threshold",
        "tested_proposals_controversy_threshold",
        "bandit_enabled",
        "bandit_min_count",
        "bandit_proposals_ratio",
        "created_at",
        "updated_at"
      )

    override val tableName: String = "sequence_configuration"

    lazy val alias
      : QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentSequenceConfiguration], PersistentSequenceConfiguration] =
      syntax("sequence_configuration")

    def apply(
      resultName: ResultName[PersistentSequenceConfiguration] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentSequenceConfiguration = {
      PersistentSequenceConfiguration.apply(
        sequenceId = resultSet.string(resultName.sequenceId),
        newProposalsRatio = resultSet.double(resultName.newProposalsRatio),
        newProposalsVoteThreshold = resultSet.int(resultName.newProposalsVoteThreshold),
        testedProposalsEngagementThreshold = resultSet.double(resultName.testedProposalsEngagementThreshold),
        testedProposalsScoreThreshold = resultSet.double(resultName.testedProposalsScoreThreshold),
        testedProposalsControversyThreshold = resultSet.double(resultName.testedProposalsControversyThreshold),
        banditEnabled = resultSet.boolean(resultName.banditEnabled),
        banditMinCount = resultSet.int(resultName.banditMinCount),
        banditProposalsRatio = resultSet.double(resultName.banditProposalsRatio),
        createdAt = resultSet.zonedDateTime(resultName.createdAt),
        updatedAt = resultSet.zonedDateTime(resultName.updatedAt)
      )
    }
  }
}
