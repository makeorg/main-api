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

import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.ScalikeSupport._
import org.make.api.proposal.SelectionAlgorithmName
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
  def findOne(questionId: QuestionId): Future[Option[SequenceConfiguration]]
  def findAll(): Future[Seq[SequenceConfiguration]]
  def persist(sequenceConfiguration: SequenceConfiguration): Future[Boolean]
  def delete(questionId: QuestionId): Future[Unit]
}

trait DefaultPersistentSequenceConfigurationServiceComponent extends PersistentSequenceConfigurationComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentSequenceConfigurationService: PersistentSequenceConfigurationService =
    new DefaultPersistentSequenceConfigurationService

  class DefaultPersistentSequenceConfigurationService
      extends PersistentSequenceConfigurationService
      with ShortenedNames
      with Logging {

    private val alias = PersistentSequenceConfiguration.alias
    private val column = PersistentSequenceConfiguration.column

    private def findOne[A: ParameterBinderFactory](name: SQLSyntax, value: A) = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTag = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentSequenceConfiguration.as(alias))
            .where(sqls.eq(name, value))
        }.map(PersistentSequenceConfiguration.apply()).single().apply()
      })

      futurePersistentTag.map(_.map(_.toSequenceConfiguration))
    }

    override def findOne(sequenceId: SequenceId): Future[Option[SequenceConfiguration]] =
      findOne(alias.sequenceId, sequenceId)

    override def findOne(questionId: QuestionId): Future[Option[SequenceConfiguration]] =
      findOne(alias.questionId, questionId)

    override def findAll(): Future[Seq[SequenceConfiguration]] = {
      implicit val context: EC = readExecutionContext

      val futurePersistentSequenceConfig = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentSequenceConfiguration.as(alias))
        }.map(PersistentSequenceConfiguration.apply()).list().apply()
      })

      futurePersistentSequenceConfig.map(_.map(_.toSequenceConfiguration))
    }

    def insertConfig(sequenceConfig: SequenceConfiguration): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentSequenceConfiguration)
            .namedValues(
              column.sequenceId -> sequenceConfig.sequenceId.value,
              column.questionId -> sequenceConfig.questionId.value,
              column.newProposalsRatio -> sequenceConfig.newProposalsRatio,
              column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold,
              column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold,
              column.testedProposalsScoreThreshold -> sequenceConfig.testedProposalsScoreThreshold,
              column.testedProposalsControversyThreshold -> sequenceConfig.testedProposalsControversyThreshold,
              column.testedProposalsMaxVotesThreshold -> sequenceConfig.testedProposalsMaxVotesThreshold,
              column.intraIdeaEnabled -> sequenceConfig.intraIdeaEnabled,
              column.intraIdeaMinCount -> sequenceConfig.intraIdeaMinCount,
              column.intraIdeaProposalsRatio -> sequenceConfig.intraIdeaProposalsRatio,
              column.interIdeaCompetitionEnabled -> sequenceConfig.interIdeaCompetitionEnabled,
              column.interIdeaCompetitionTargetCount -> sequenceConfig.interIdeaCompetitionTargetCount,
              column.interIdeaCompetitionControversialRatio -> sequenceConfig.interIdeaCompetitionControversialRatio,
              column.interIdeaCompetitionControversialCount -> sequenceConfig.interIdeaCompetitionControversialCount,
              column.maxTestedProposalCount -> sequenceConfig.maxTestedProposalCount,
              column.sequenceSize -> sequenceConfig.sequenceSize,
              column.selectionAlgorithmName -> sequenceConfig.selectionAlgorithmName,
              column.createdAt -> DateHelper.now(),
              column.updatedAt -> DateHelper.now(),
              column.nonSequenceVotesWeight -> sequenceConfig.nonSequenceVotesWeight
            )
        }.execute().apply()
      })
    }

    def updateConfig(sequenceConfig: SequenceConfiguration): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentSequenceConfiguration)
            .set(
              column.newProposalsRatio -> sequenceConfig.newProposalsRatio,
              column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold,
              column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold,
              column.testedProposalsScoreThreshold -> sequenceConfig.testedProposalsScoreThreshold,
              column.testedProposalsControversyThreshold -> sequenceConfig.testedProposalsControversyThreshold,
              column.testedProposalsMaxVotesThreshold -> sequenceConfig.testedProposalsMaxVotesThreshold,
              column.intraIdeaEnabled -> sequenceConfig.intraIdeaEnabled,
              column.intraIdeaMinCount -> sequenceConfig.intraIdeaMinCount,
              column.intraIdeaProposalsRatio -> sequenceConfig.intraIdeaProposalsRatio,
              column.interIdeaCompetitionEnabled -> sequenceConfig.interIdeaCompetitionEnabled,
              column.interIdeaCompetitionTargetCount -> sequenceConfig.interIdeaCompetitionTargetCount,
              column.interIdeaCompetitionControversialRatio -> sequenceConfig.interIdeaCompetitionControversialRatio,
              column.interIdeaCompetitionControversialCount -> sequenceConfig.interIdeaCompetitionControversialCount,
              column.maxTestedProposalCount -> sequenceConfig.maxTestedProposalCount,
              column.sequenceSize -> sequenceConfig.sequenceSize,
              column.selectionAlgorithmName -> sequenceConfig.selectionAlgorithmName,
              column.updatedAt -> DateHelper.now(),
              column.nonSequenceVotesWeight -> sequenceConfig.nonSequenceVotesWeight
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
        case Some(_) => updateConfig(sequenceConfig).map(_ == 1)
        case None    => insertConfig(sequenceConfig)
      }
    }
    override def delete(questionId: QuestionId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentSequenceConfiguration)
            .where(sqls.eq(PersistentSequenceConfiguration.column.questionId, questionId.value))
        }.execute().apply()
      }).map(_ => ())
    }
  }
}

object DefaultPersistentSequenceConfigurationServiceComponent {

  final case class PersistentSequenceConfiguration(
    sequenceId: String,
    questionId: String,
    newProposalsRatio: Double,
    newProposalsVoteThreshold: Int,
    testedProposalsEngagementThreshold: Option[Double],
    testedProposalsScoreThreshold: Option[Double],
    testedProposalsControversyThreshold: Option[Double],
    testedProposalsMaxVotesThreshold: Option[Int],
    intraIdeaEnabled: Boolean,
    intraIdeaMinCount: Int,
    intraIdeaProposalsRatio: Double,
    interIdeaCompetitionEnabled: Boolean,
    interIdeaCompetitionTargetCount: Int,
    interIdeaCompetitionControversialRatio: Double,
    interIdeaCompetitionControversialCount: Int,
    maxTestedProposalCount: Int,
    sequenceSize: Int,
    selectionAlgorithmName: String,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
    nonSequenceVotesWeight: Double
  ) {
    def toSequenceConfiguration: SequenceConfiguration =
      SequenceConfiguration(
        sequenceId = SequenceId(sequenceId),
        questionId = QuestionId(questionId),
        newProposalsRatio = newProposalsRatio,
        newProposalsVoteThreshold = newProposalsVoteThreshold,
        testedProposalsEngagementThreshold = testedProposalsEngagementThreshold,
        testedProposalsScoreThreshold = testedProposalsScoreThreshold,
        testedProposalsControversyThreshold = testedProposalsControversyThreshold,
        testedProposalsMaxVotesThreshold = testedProposalsMaxVotesThreshold,
        intraIdeaEnabled = intraIdeaEnabled,
        intraIdeaMinCount = intraIdeaMinCount,
        intraIdeaProposalsRatio = intraIdeaProposalsRatio,
        interIdeaCompetitionEnabled = interIdeaCompetitionEnabled,
        interIdeaCompetitionTargetCount = interIdeaCompetitionTargetCount,
        interIdeaCompetitionControversialRatio = interIdeaCompetitionControversialRatio,
        interIdeaCompetitionControversialCount = interIdeaCompetitionControversialCount,
        maxTestedProposalCount = maxTestedProposalCount,
        sequenceSize = sequenceSize,
        selectionAlgorithmName = SelectionAlgorithmName.withValue(selectionAlgorithmName),
        nonSequenceVotesWeight = nonSequenceVotesWeight
      )
  }

  object PersistentSequenceConfiguration
      extends SQLSyntaxSupport[PersistentSequenceConfiguration]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] =
      Seq(
        "sequence_id",
        "question_id",
        "new_proposals_ratio",
        "new_proposals_vote_threshold",
        "tested_proposals_engagement_threshold",
        "tested_proposals_score_threshold",
        "tested_proposals_controversy_threshold",
        "tested_proposals_max_votes_threshold",
        "intra_idea_enabled",
        "intra_idea_min_count",
        "intra_idea_proposals_ratio",
        "inter_idea_competition_enabled",
        "inter_idea_competition_target_count",
        "inter_idea_competition_controversial_ratio",
        "inter_idea_competition_controversial_count",
        "max_tested_proposal_count",
        "sequence_size",
        "selection_algorithm_name",
        "created_at",
        "updated_at",
        "non_sequence_votes_weight"
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
        newProposalsRatio = resultSet.double(resultName.newProposalsRatio),
        newProposalsVoteThreshold = resultSet.int(resultName.newProposalsVoteThreshold),
        testedProposalsEngagementThreshold = resultSet.doubleOpt(resultName.testedProposalsEngagementThreshold),
        testedProposalsScoreThreshold = resultSet.doubleOpt(resultName.testedProposalsScoreThreshold),
        testedProposalsControversyThreshold = resultSet.doubleOpt(resultName.testedProposalsControversyThreshold),
        testedProposalsMaxVotesThreshold = resultSet.intOpt(resultName.testedProposalsMaxVotesThreshold),
        intraIdeaEnabled = resultSet.boolean(resultName.intraIdeaEnabled),
        intraIdeaMinCount = resultSet.int(resultName.intraIdeaMinCount),
        intraIdeaProposalsRatio = resultSet.double(resultName.intraIdeaProposalsRatio),
        interIdeaCompetitionEnabled = resultSet.boolean(resultName.interIdeaCompetitionEnabled),
        interIdeaCompetitionTargetCount = resultSet.int(resultName.interIdeaCompetitionTargetCount),
        interIdeaCompetitionControversialRatio = resultSet.double(resultName.interIdeaCompetitionControversialRatio),
        interIdeaCompetitionControversialCount = resultSet.int(resultName.interIdeaCompetitionControversialCount),
        maxTestedProposalCount = resultSet.int(resultName.maxTestedProposalCount),
        sequenceSize = resultSet.int(resultName.sequenceSize),
        selectionAlgorithmName = resultSet.string(resultName.selectionAlgorithmName),
        createdAt = resultSet.zonedDateTime(resultName.createdAt),
        updatedAt = resultSet.zonedDateTime(resultName.updatedAt),
        nonSequenceVotesWeight = resultSet.double(resultName.nonSequenceVotesWeight)
      )
    }
  }
}
