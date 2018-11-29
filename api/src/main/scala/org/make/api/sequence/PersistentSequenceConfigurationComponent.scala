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

package org.make.api.sequence

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.sequence.DefaultPersistentSequenceConfigurationServiceComponent.PersistentSequenceConfiguration
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.question.QuestionId
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
                column.questionId -> sequenceConfig.questionId.value,
                column.maxAvailableProposals -> sequenceConfig.maxAvailableProposals,
                column.newProposalsRatio -> sequenceConfig.newProposalsRatio,
                column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold,
                column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold,
                column.testedProposalsScoreThreshold -> sequenceConfig.testedProposalsScoreThreshold,
                column.testedProposalsControversyThreshold -> sequenceConfig.testedProposalsControversyThreshold,
                column.testedProposalsMaxVotesThreshold -> sequenceConfig.testedProposalsMaxVotesThreshold,
                column.banditEnabled -> sequenceConfig.banditEnabled,
                column.banditMinCount -> sequenceConfig.banditMinCount,
                column.banditProposalsRatio -> sequenceConfig.banditProposalsRatio,
                column.ideaCompetitionEnabled -> sequenceConfig.ideaCompetitionEnabled,
                column.ideaCompetitionTargetCount -> sequenceConfig.ideaCompetitionTargetCount,
                column.ideaCompetitionControversialRatio -> sequenceConfig.ideaCompetitionControversialRatio,
                column.ideaCompetitionControversialCount -> sequenceConfig.ideaCompetitionControversialCount,
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
                column.maxAvailableProposals -> sequenceConfig.maxAvailableProposals,
                column.newProposalsRatio -> sequenceConfig.newProposalsRatio,
                column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold,
                column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold,
                column.testedProposalsScoreThreshold -> sequenceConfig.testedProposalsScoreThreshold,
                column.testedProposalsControversyThreshold -> sequenceConfig.testedProposalsControversyThreshold,
                column.testedProposalsMaxVotesThreshold -> sequenceConfig.testedProposalsMaxVotesThreshold,
                column.banditEnabled -> sequenceConfig.banditEnabled,
                column.banditMinCount -> sequenceConfig.banditMinCount,
                column.banditProposalsRatio -> sequenceConfig.banditProposalsRatio,
                column.ideaCompetitionEnabled -> sequenceConfig.ideaCompetitionEnabled,
                column.ideaCompetitionTargetCount -> sequenceConfig.ideaCompetitionTargetCount,
                column.ideaCompetitionControversialRatio -> sequenceConfig.ideaCompetitionControversialRatio,
                column.ideaCompetitionControversialCount -> sequenceConfig.ideaCompetitionControversialCount,
                column.updatedAt -> DateHelper.now
              )
              .where(
                sqls
                  .eq(column.sequenceId, sequenceConfig.sequenceId.value)
                  .or(sqls.eq(column.questionId, sequenceConfig.questionId.value))
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
                                             questionId: String,
                                             maxAvailableProposals: Int,
                                             newProposalsRatio: Double,
                                             newProposalsVoteThreshold: Int,
                                             testedProposalsEngagementThreshold: Double,
                                             testedProposalsScoreThreshold: Double,
                                             testedProposalsControversyThreshold: Double,
                                             testedProposalsMaxVotesThreshold: Int,
                                             banditEnabled: Boolean,
                                             banditMinCount: Int,
                                             banditProposalsRatio: Double,
                                             ideaCompetitionEnabled: Boolean,
                                             ideaCompetitionTargetCount: Int,
                                             ideaCompetitionControversialRatio: Double,
                                             ideaCompetitionControversialCount: Int,
                                             maxTestedProposalCount: Int,
                                             sequenceSize: Int,
                                             maxVotes: Int,
                                             createdAt: ZonedDateTime,
                                             updatedAt: ZonedDateTime) {
    def toSequenceConfiguration: SequenceConfiguration =
      SequenceConfiguration(
        sequenceId = SequenceId(sequenceId),
        questionId = QuestionId(questionId),
        maxAvailableProposals = maxAvailableProposals,
        newProposalsRatio = newProposalsRatio,
        newProposalsVoteThreshold = newProposalsVoteThreshold,
        testedProposalsEngagementThreshold = testedProposalsEngagementThreshold,
        testedProposalsScoreThreshold = testedProposalsScoreThreshold,
        testedProposalsControversyThreshold = testedProposalsControversyThreshold,
        testedProposalsMaxVotesThreshold = testedProposalsMaxVotesThreshold,
        banditEnabled = banditEnabled,
        banditMinCount = banditMinCount,
        banditProposalsRatio = banditProposalsRatio,
        ideaCompetitionEnabled = ideaCompetitionEnabled,
        ideaCompetitionTargetCount = ideaCompetitionTargetCount,
        ideaCompetitionControversialRatio = ideaCompetitionControversialRatio,
        ideaCompetitionControversialCount = ideaCompetitionControversialCount,
        maxTestedProposalCount = maxTestedProposalCount,
        sequenceSize = sequenceSize,
        maxVotes = maxVotes
      )
  }

  object PersistentSequenceConfiguration
      extends SQLSyntaxSupport[PersistentSequenceConfiguration]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] =
      Seq(
        "sequence_id",
        "question_id",
        "max_available_proposals",
        "new_proposals_ratio",
        "new_proposals_vote_threshold",
        "tested_proposals_engagement_threshold",
        "tested_proposals_score_threshold",
        "tested_proposals_controversy_threshold",
        "tested_proposals_max_votes_threshold",
        "bandit_enabled",
        "bandit_min_count",
        "bandit_proposals_ratio",
        "idea_competition_enabled",
        "idea_competition_target_count",
        "idea_competition_controversial_ratio",
        "idea_competition_controversial_count",
        "maxTestedProposalCount",
        "sequenceSize",
        "maxVotes",
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
        questionId = resultSet.string(resultName.questionId),
        maxAvailableProposals = resultSet.int(resultName.maxAvailableProposals),
        newProposalsRatio = resultSet.double(resultName.newProposalsRatio),
        newProposalsVoteThreshold = resultSet.int(resultName.newProposalsVoteThreshold),
        testedProposalsEngagementThreshold = resultSet.double(resultName.testedProposalsEngagementThreshold),
        testedProposalsScoreThreshold = resultSet.double(resultName.testedProposalsScoreThreshold),
        testedProposalsControversyThreshold = resultSet.double(resultName.testedProposalsControversyThreshold),
        testedProposalsMaxVotesThreshold = resultSet.int(resultName.testedProposalsMaxVotesThreshold),
        banditEnabled = resultSet.boolean(resultName.banditEnabled),
        banditMinCount = resultSet.int(resultName.banditMinCount),
        banditProposalsRatio = resultSet.double(resultName.banditProposalsRatio),
        ideaCompetitionEnabled = resultSet.boolean(resultName.ideaCompetitionEnabled),
        ideaCompetitionTargetCount = resultSet.int(resultName.ideaCompetitionTargetCount),
        ideaCompetitionControversialRatio = resultSet.double(resultName.ideaCompetitionControversialRatio),
        ideaCompetitionControversialCount = resultSet.int(resultName.ideaCompetitionControversialCount),
        maxTestedProposalCount = resultSet.int(resultName.maxTestedProposalCount),
        sequenceSize = resultSet.int(resultName.sequenceSize),
        maxVotes = resultSet.int(resultName.maxVotes),
        createdAt = resultSet.zonedDateTime(resultName.createdAt),
        updatedAt = resultSet.zonedDateTime(resultName.updatedAt)
      )
    }
  }
}
