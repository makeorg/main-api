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

import eu.timepit.refined.types.numeric._
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.sequence.DefaultPersistentSequenceConfigurationServiceComponent.{
  PersistentExplorationSequenceConfiguration,
  PersistentSequenceConfiguration,
  PersistentSpecificSequenceConfiguration
}
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.Futures._
import org.make.api.technical.ScalikeSupport._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.question.QuestionId
import org.make.core.sequence._
import scalikejdbc._

import java.time.ZonedDateTime
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

  implicit val specificSequenceConfigurationIdBinder: Binders[SpecificSequenceConfigurationId] =
    stringValueBinders[SpecificSequenceConfigurationId](SpecificSequenceConfigurationId.apply)

  override lazy val persistentSequenceConfigurationService: PersistentSequenceConfigurationService =
    new DefaultPersistentSequenceConfigurationService

  class DefaultPersistentSequenceConfigurationService
      extends PersistentSequenceConfigurationService
      with ShortenedNames
      with Logging {

    private val alias = PersistentSequenceConfiguration.alias
    private val mainSequenceAlias = PersistentExplorationSequenceConfiguration.syntax("main_sequence_configuration")
    private val controversialSequenceAlias =
      PersistentSpecificSequenceConfiguration.syntax("controversial_sequence_configuration")
    private val popularSequenceAlias = PersistentSpecificSequenceConfiguration.syntax("popular_sequence_configuration")
    private val keywordSequenceAlias = PersistentSpecificSequenceConfiguration.syntax("keyword_sequence_configuration")
    private val column = PersistentSequenceConfiguration.column
    private val specificColumn = PersistentSpecificSequenceConfiguration.column
    private val explorationColumn = PersistentExplorationSequenceConfiguration.column

    private def selectSequenceConfiguration[T]: scalikejdbc.SelectSQLBuilder[T] =
      select
        .from(PersistentSequenceConfiguration.as(alias))
        .leftJoin(PersistentExplorationSequenceConfiguration.as(mainSequenceAlias))
        .on(alias.main, mainSequenceAlias.explorationSequenceConfigurationId)
        .leftJoin(PersistentSpecificSequenceConfiguration.as(controversialSequenceAlias))
        .on(alias.controversial, controversialSequenceAlias.id)
        .leftJoin(PersistentSpecificSequenceConfiguration.as(popularSequenceAlias))
        .on(alias.popular, popularSequenceAlias.id)
        .leftJoin(PersistentSpecificSequenceConfiguration.as(keywordSequenceAlias))
        .on(alias.keyword, keywordSequenceAlias.id)

    private def findOne[A: ParameterBinderFactory](name: SQLSyntax, value: A) = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTag = Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          selectSequenceConfiguration.where(sqls.eq(name, value))
        }.map(
            PersistentSequenceConfiguration
              .apply(
                alias.resultName,
                mainSequenceAlias.resultName,
                controversialSequenceAlias.resultName,
                popularSequenceAlias.resultName,
                keywordSequenceAlias.resultName
              )
          )
          .single()
          .apply()
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
          selectSequenceConfiguration
        }.map(
            PersistentSequenceConfiguration.apply(
              alias.resultName,
              mainSequenceAlias.resultName,
              controversialSequenceAlias.resultName,
              popularSequenceAlias.resultName,
              keywordSequenceAlias.resultName
            )
          )
          .list()
          .apply()
      })

      futurePersistentSequenceConfig.map(_.map(_.toSequenceConfiguration))
    }

    private def insertConfig(sequenceConfig: SequenceConfiguration): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentSequenceConfiguration)
            .namedValues(
              column.sequenceId -> sequenceConfig.sequenceId,
              column.questionId -> sequenceConfig.questionId,
              column.main -> sequenceConfig.mainSequence.explorationSequenceConfigurationId,
              column.controversial -> sequenceConfig.controversial.specificSequenceConfigurationId,
              column.popular -> sequenceConfig.popular.specificSequenceConfigurationId,
              column.keyword -> sequenceConfig.keyword.specificSequenceConfigurationId,
              column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold,
              column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold,
              column.testedProposalsScoreThreshold -> sequenceConfig.testedProposalsScoreThreshold,
              column.testedProposalsControversyThreshold -> sequenceConfig.testedProposalsControversyThreshold,
              column.testedProposalsMaxVotesThreshold -> sequenceConfig.testedProposalsMaxVotesThreshold,
              column.createdAt -> DateHelper.now(),
              column.updatedAt -> DateHelper.now(),
              column.nonSequenceVotesWeight -> sequenceConfig.nonSequenceVotesWeight
            )
        }.execute().apply()
      })
    }

    private def insertSpecificConfig(specificSequenceConfig: SpecificSequenceConfiguration): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentSpecificSequenceConfiguration)
            .namedValues(
              specificColumn.id -> specificSequenceConfig.specificSequenceConfigurationId,
              specificColumn.sequenceSize -> specificSequenceConfig.sequenceSize,
              specificColumn.newProposalsRatio -> specificSequenceConfig.newProposalsRatio,
              specificColumn.maxTestedProposalCount -> specificSequenceConfig.maxTestedProposalCount,
              specificColumn.selectionAlgorithmName -> specificSequenceConfig.selectionAlgorithmName,
              specificColumn.intraIdeaEnabled -> specificSequenceConfig.intraIdeaEnabled,
              specificColumn.intraIdeaMinCount -> specificSequenceConfig.intraIdeaMinCount,
              specificColumn.intraIdeaProposalsRatio -> specificSequenceConfig.intraIdeaProposalsRatio,
              specificColumn.interIdeaCompetitionEnabled -> specificSequenceConfig.interIdeaCompetitionEnabled,
              specificColumn.interIdeaCompetitionTargetCount -> specificSequenceConfig.interIdeaCompetitionTargetCount,
              specificColumn.interIdeaCompetitionControversialRatio -> specificSequenceConfig.interIdeaCompetitionControversialRatio,
              specificColumn.interIdeaCompetitionControversialCount -> specificSequenceConfig.interIdeaCompetitionControversialCount
            )
        }.execute().apply()
      })
    }

    private def insertExplorationConfig(explorationConfig: ExplorationSequenceConfiguration): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentExplorationSequenceConfiguration)
            .namedValues(autoNamedValues(explorationConfig, explorationColumn))
        }.execute().apply()
      })
    }

    private def updateConfig(sequenceConfig: SequenceConfiguration): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentSequenceConfiguration)
            .set(
              column.main -> sequenceConfig.mainSequence.explorationSequenceConfigurationId,
              column.controversial -> sequenceConfig.controversial.specificSequenceConfigurationId,
              column.popular -> sequenceConfig.popular.specificSequenceConfigurationId,
              column.keyword -> sequenceConfig.keyword.specificSequenceConfigurationId,
              column.newProposalsVoteThreshold -> sequenceConfig.newProposalsVoteThreshold,
              column.testedProposalsEngagementThreshold -> sequenceConfig.testedProposalsEngagementThreshold,
              column.testedProposalsScoreThreshold -> sequenceConfig.testedProposalsScoreThreshold,
              column.testedProposalsControversyThreshold -> sequenceConfig.testedProposalsControversyThreshold,
              column.testedProposalsMaxVotesThreshold -> sequenceConfig.testedProposalsMaxVotesThreshold,
              column.updatedAt -> DateHelper.now(),
              column.nonSequenceVotesWeight -> sequenceConfig.nonSequenceVotesWeight
            )
            .where(
              sqls
                .eq(column.sequenceId, sequenceConfig.sequenceId)
                .or(sqls.eq(column.questionId, sequenceConfig.questionId))
            )
        }.update().apply()
      })
    }

    private def updateSpecificConfig(specificSequenceConfig: SpecificSequenceConfiguration): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentSpecificSequenceConfiguration)
            .set(
              specificColumn.sequenceSize -> specificSequenceConfig.sequenceSize,
              specificColumn.newProposalsRatio -> specificSequenceConfig.newProposalsRatio,
              specificColumn.maxTestedProposalCount -> specificSequenceConfig.maxTestedProposalCount,
              specificColumn.selectionAlgorithmName -> specificSequenceConfig.selectionAlgorithmName,
              specificColumn.intraIdeaEnabled -> specificSequenceConfig.intraIdeaEnabled,
              specificColumn.intraIdeaMinCount -> specificSequenceConfig.intraIdeaMinCount,
              specificColumn.intraIdeaProposalsRatio -> specificSequenceConfig.intraIdeaProposalsRatio,
              specificColumn.interIdeaCompetitionEnabled -> specificSequenceConfig.interIdeaCompetitionEnabled,
              specificColumn.interIdeaCompetitionTargetCount -> specificSequenceConfig.interIdeaCompetitionTargetCount,
              specificColumn.interIdeaCompetitionControversialRatio -> specificSequenceConfig.interIdeaCompetitionControversialRatio,
              specificColumn.interIdeaCompetitionControversialCount -> specificSequenceConfig.interIdeaCompetitionControversialCount
            )
            .where(
              sqls
                .eq(specificColumn.id, specificSequenceConfig.specificSequenceConfigurationId)
            )
        }.update().apply()
      })
    }

    private def updateExplorationConfig(config: ExplorationSequenceConfiguration): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentExplorationSequenceConfiguration)
            .set(autoNamedValues(config, explorationColumn, "explorationSequenceConfigurationId"))
            .where(
              sqls.eq(explorationColumn.explorationSequenceConfigurationId, config.explorationSequenceConfigurationId)
            )
        }.update().apply()
      })
    }

    private def deleteConfig(questionId: QuestionId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentSequenceConfiguration)
            .where(sqls.eq(PersistentSequenceConfiguration.column.questionId, questionId.value))
        }.execute().apply()
      }).toUnit
    }

    private def deleteSpecificConfig(specificSequenceConfigurationId: SpecificSequenceConfigurationId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentSpecificSequenceConfiguration)
            .where(sqls.eq(PersistentSpecificSequenceConfiguration.column.id, specificSequenceConfigurationId))
        }.execute().apply()
      }).toUnit
    }

    private def deleteExplorationConfig(
      explorationSequenceConfigurationId: ExplorationSequenceConfigurationId
    ): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentExplorationSequenceConfiguration)
            .where(sqls.eq(explorationColumn.explorationSequenceConfigurationId, explorationSequenceConfigurationId))
        }.execute().apply()
      }).toUnit
    }

    private def insertAllConfig(sequenceConfiguration: SequenceConfiguration): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      for {
        _      <- insertExplorationConfig(sequenceConfiguration.mainSequence)
        _      <- insertSpecificConfig(sequenceConfiguration.controversial)
        _      <- insertSpecificConfig(sequenceConfiguration.popular)
        _      <- insertSpecificConfig(sequenceConfiguration.keyword)
        result <- insertConfig(sequenceConfiguration)
      } yield result
    }

    private def updateAllConfig(sequenceConfiguration: SequenceConfiguration): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      for {
        _      <- updateExplorationConfig(sequenceConfiguration.mainSequence)
        _      <- updateSpecificConfig(sequenceConfiguration.controversial)
        _      <- updateSpecificConfig(sequenceConfiguration.popular)
        _      <- updateSpecificConfig(sequenceConfiguration.keyword)
        result <- updateConfig(sequenceConfiguration)
      } yield result
    }

    private def deleteAllConfig(sequenceConfiguration: SequenceConfiguration): Future[Unit] = {
      implicit val context: EC = writeExecutionContext
      for {
        _ <- deleteConfig(sequenceConfiguration.questionId)
        _ <- deleteExplorationConfig(sequenceConfiguration.mainSequence.explorationSequenceConfigurationId)
        _ <- deleteSpecificConfig(sequenceConfiguration.controversial.specificSequenceConfigurationId)
        _ <- deleteSpecificConfig(sequenceConfiguration.popular.specificSequenceConfigurationId)
        _ <- deleteSpecificConfig(sequenceConfiguration.keyword.specificSequenceConfigurationId)
      } yield {}
    }

    override def persist(sequenceConfig: SequenceConfiguration): Future[Boolean] = {
      implicit val context: EC = writeExecutionContext
      findOne(sequenceConfig.sequenceId).flatMap {
        case Some(_) => updateAllConfig(sequenceConfig).map(_ == 1)
        case None    => insertAllConfig(sequenceConfig)
      }
    }

    override def delete(questionId: QuestionId): Future[Unit] = {
      implicit val context: EC = writeExecutionContext
      findOne(questionId).flatMap {
        case Some(sequenceConfig) => deleteAllConfig(sequenceConfig)
        case None                 => Future.successful({})
      }
    }
  }
}

object DefaultPersistentSequenceConfigurationServiceComponent {

  final case class PersistentSequenceConfiguration(
    sequenceId: String,
    questionId: String,
    main: ExplorationSequenceConfiguration,
    controversial: PersistentSpecificSequenceConfiguration,
    popular: PersistentSpecificSequenceConfiguration,
    keyword: PersistentSpecificSequenceConfiguration,
    newProposalsVoteThreshold: Int,
    testedProposalsEngagementThreshold: Option[Double],
    testedProposalsScoreThreshold: Option[Double],
    testedProposalsControversyThreshold: Option[Double],
    testedProposalsMaxVotesThreshold: Option[Int],
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
    nonSequenceVotesWeight: Double
  ) {
    def toSequenceConfiguration: SequenceConfiguration = {
      SequenceConfiguration(
        sequenceId = SequenceId(sequenceId),
        questionId = QuestionId(questionId),
        mainSequence = main,
        controversial = controversial.toSpecificSequenceConfiguration,
        popular = popular.toSpecificSequenceConfiguration,
        keyword = keyword.toSpecificSequenceConfiguration,
        newProposalsVoteThreshold = newProposalsVoteThreshold,
        testedProposalsEngagementThreshold = testedProposalsEngagementThreshold,
        testedProposalsScoreThreshold = testedProposalsScoreThreshold,
        testedProposalsControversyThreshold = testedProposalsControversyThreshold,
        testedProposalsMaxVotesThreshold = testedProposalsMaxVotesThreshold,
        nonSequenceVotesWeight = nonSequenceVotesWeight
      )
    }
  }

  object PersistentSequenceConfiguration
      extends SQLSyntaxSupport[PersistentSequenceConfiguration]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] =
      Seq(
        "sequence_id",
        "question_id",
        "main",
        "controversial",
        "popular",
        "keyword",
        "new_proposals_vote_threshold",
        "tested_proposals_engagement_threshold",
        "tested_proposals_score_threshold",
        "tested_proposals_controversy_threshold",
        "tested_proposals_max_votes_threshold",
        "created_at",
        "updated_at",
        "non_sequence_votes_weight"
      )

    override val tableName: String = "sequence_configuration"

    lazy val alias
      : QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentSequenceConfiguration], PersistentSequenceConfiguration] =
      syntax("sequence_configuration")

    val specificSequenceConfigurationResultName: ResultName[PersistentSpecificSequenceConfiguration] =
      PersistentSpecificSequenceConfiguration.alias.resultName

    def apply(
      resultName: ResultName[PersistentSequenceConfiguration],
      mainSequenceResultName: ResultName[ExplorationSequenceConfiguration],
      controversialSequenceResultName: ResultName[PersistentSpecificSequenceConfiguration],
      popularSequenceResultName: ResultName[PersistentSpecificSequenceConfiguration],
      keywordSequenceResultName: ResultName[PersistentSpecificSequenceConfiguration]
    )(resultSet: WrappedResultSet): PersistentSequenceConfiguration = {
      PersistentSequenceConfiguration.apply(
        sequenceId = resultSet.string(resultName.sequenceId),
        questionId = resultSet.string(resultName.questionId),
        main = PersistentExplorationSequenceConfiguration(mainSequenceResultName)(resultSet),
        controversial = PersistentSpecificSequenceConfiguration(controversialSequenceResultName)(resultSet),
        popular = PersistentSpecificSequenceConfiguration(popularSequenceResultName)(resultSet),
        keyword = PersistentSpecificSequenceConfiguration(keywordSequenceResultName)(resultSet),
        newProposalsVoteThreshold = resultSet.int(resultName.newProposalsVoteThreshold),
        testedProposalsEngagementThreshold = resultSet.doubleOpt(resultName.testedProposalsEngagementThreshold),
        testedProposalsScoreThreshold = resultSet.doubleOpt(resultName.testedProposalsScoreThreshold),
        testedProposalsControversyThreshold = resultSet.doubleOpt(resultName.testedProposalsControversyThreshold),
        testedProposalsMaxVotesThreshold = resultSet.intOpt(resultName.testedProposalsMaxVotesThreshold),
        createdAt = resultSet.zonedDateTime(resultName.createdAt),
        updatedAt = resultSet.zonedDateTime(resultName.updatedAt),
        nonSequenceVotesWeight = resultSet.double(resultName.nonSequenceVotesWeight)
      )
    }
  }

  object PersistentExplorationSequenceConfiguration extends SQLSyntaxSupport[ExplorationSequenceConfiguration] {
    override lazy val columns: scala.collection.Seq[String] = autoColumns[ExplorationSequenceConfiguration]()
    override def tableName: String = "exploration_sequence_configuration"

    def apply(
      rn: ResultName[ExplorationSequenceConfiguration]
    )(rs: WrappedResultSet): ExplorationSequenceConfiguration =
      autoConstruct(rs, rn)
  }

  final case class PersistentSpecificSequenceConfiguration(
    id: String,
    sequenceSize: PosInt,
    newProposalsRatio: Double,
    maxTestedProposalCount: PosInt,
    selectionAlgorithmName: String,
    intraIdeaEnabled: Boolean,
    intraIdeaMinCount: Int,
    intraIdeaProposalsRatio: Double,
    interIdeaCompetitionEnabled: Boolean,
    interIdeaCompetitionTargetCount: Int,
    interIdeaCompetitionControversialRatio: Double,
    interIdeaCompetitionControversialCount: Int
  ) {
    def toSpecificSequenceConfiguration: SpecificSequenceConfiguration = {
      SpecificSequenceConfiguration(
        specificSequenceConfigurationId = SpecificSequenceConfigurationId(id),
        sequenceSize = sequenceSize,
        newProposalsRatio = newProposalsRatio,
        maxTestedProposalCount = maxTestedProposalCount,
        selectionAlgorithmName = SelectionAlgorithmName.withValue(selectionAlgorithmName),
        intraIdeaEnabled = intraIdeaEnabled,
        intraIdeaMinCount = intraIdeaMinCount,
        intraIdeaProposalsRatio = intraIdeaProposalsRatio,
        interIdeaCompetitionEnabled = interIdeaCompetitionEnabled,
        interIdeaCompetitionTargetCount = interIdeaCompetitionTargetCount,
        interIdeaCompetitionControversialRatio = interIdeaCompetitionControversialRatio,
        interIdeaCompetitionControversialCount = interIdeaCompetitionControversialCount
      )
    }
  }

  object PersistentSpecificSequenceConfiguration
      extends SQLSyntaxSupport[PersistentSpecificSequenceConfiguration]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] =
      Seq(
        "id",
        "sequence_size",
        "new_proposals_ratio",
        "max_tested_proposal_count",
        "selection_algorithm_name",
        "intra_idea_enabled",
        "intra_idea_min_count",
        "intra_idea_proposals_ratio",
        "inter_idea_competition_enabled",
        "inter_idea_competition_target_count",
        "inter_idea_competition_controversial_ratio",
        "inter_idea_competition_controversial_count"
      )

    override val tableName: String = "specific_sequence_configuration"

    lazy val alias
      : QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentSpecificSequenceConfiguration], PersistentSpecificSequenceConfiguration] =
      syntax("specific_sequence_configuration")

    def apply(
      resultName: ResultName[PersistentSpecificSequenceConfiguration] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentSpecificSequenceConfiguration = {
      PersistentSpecificSequenceConfiguration.apply(
        id = resultSet.string(resultName.id),
        sequenceSize = resultSet.get[PosInt](resultName.sequenceSize),
        newProposalsRatio = resultSet.double(resultName.newProposalsRatio),
        maxTestedProposalCount = resultSet.get[PosInt](resultName.maxTestedProposalCount),
        selectionAlgorithmName = resultSet.string(resultName.selectionAlgorithmName),
        intraIdeaEnabled = resultSet.boolean(resultName.intraIdeaEnabled),
        intraIdeaMinCount = resultSet.int(resultName.intraIdeaMinCount),
        intraIdeaProposalsRatio = resultSet.double(resultName.intraIdeaProposalsRatio),
        interIdeaCompetitionEnabled = resultSet.boolean(resultName.interIdeaCompetitionEnabled),
        interIdeaCompetitionTargetCount = resultSet.int(resultName.interIdeaCompetitionTargetCount),
        interIdeaCompetitionControversialRatio = resultSet.double(resultName.interIdeaCompetitionControversialRatio),
        interIdeaCompetitionControversialCount = resultSet.int(resultName.interIdeaCompetitionControversialCount)
      )
    }
  }
}
