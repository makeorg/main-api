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

package org.make.api.migrations.db

import java.sql.Connection
import java.util.UUID

import org.make.core.sequence.SelectionAlgorithmName

class V105_2__Specific_sequence_data extends Migration {

  @SuppressWarnings(Array("org.wartremover.warts.While"))
  override def migrate(connection: Connection): Unit = {
    val resultSet = connection
      .prepareStatement(
        "SELECT sequence_id, sequence_size, new_proposals_ratio, max_tested_proposal_count, selection_algorithm_name, " +
          "intra_idea_enabled, intra_idea_min_count, intra_idea_proposals_ratio, inter_idea_competition_enabled, " +
          "inter_idea_competition_target_count, inter_idea_competition_controversial_ratio, inter_idea_competition_controversial_count " +
          "FROM sequence_configuration"
      )
      .executeQuery()

    def nextId(): String = UUID.randomUUID().toString

    def specificConfigurationValuesToInsert(
      id: String,
      sequenceSize: Int,
      newProposalsRatio: Double,
      maxTestedProposalCount: Int,
      selectionAlgorithmName: String,
      intraIdeaEnabled: Boolean,
      intraIdeaMinCount: Int,
      intraIdeaProposalsRatio: Double,
      interIdeaCompetitionEnabled: Boolean,
      interIdeaCompetitionTargetCount: Int,
      interIdeaCompetitionControversialRatio: Double,
      interIdeaCompetitionControversialCount: Int
    ): String = {
      (s"('$id', '$sequenceSize', '$newProposalsRatio', '$maxTestedProposalCount', '$selectionAlgorithmName'," +
        s" '$intraIdeaEnabled', '$intraIdeaMinCount', '$intraIdeaProposalsRatio', '$interIdeaCompetitionEnabled'," +
        s" '$interIdeaCompetitionTargetCount', '$interIdeaCompetitionControversialRatio'," +
        s" '$interIdeaCompetitionControversialCount')").replace("''", "null")
    }

    while (resultSet.next()) {
      val sequenceId = resultSet.getString("sequence_id")
      val sequenceSize = resultSet.getInt("sequence_size")
      val newProposalsRatio = resultSet.getDouble("new_proposals_ratio")
      val maxTestedProposalCount = resultSet.getInt("max_tested_proposal_count")
      val selectionAlgorithmName = resultSet.getString("selection_algorithm_name")
      val intraIdeaEnabled = resultSet.getBoolean("intra_idea_enabled")
      val intraIdeaMinCount = resultSet.getInt("intra_idea_min_count")
      val intraIdeaProposalsRatio = resultSet.getDouble("intra_idea_proposals_ratio")
      val interIdeaCompetitionEnabled = resultSet.getBoolean("inter_idea_competition_enabled")
      val interIdeaCompetitionTargetCount = resultSet.getInt("inter_idea_competition_target_count")
      val interIdeaCompetitionControversialRatio = resultSet.getDouble("inter_idea_competition_controversial_ratio")
      val interIdeaCompetitionControversialCount = resultSet.getInt("inter_idea_competition_controversial_count")

      val mainSequenceConfigurationId: String = nextId()
      val controversialSequenceConfigurationId: String = nextId()
      val popularSequenceConfigurationId: String = nextId()
      val keywordSequenceConfigurationId: String = nextId()

      def mainSequenceValuesToInsert(id: String): String = {
        specificConfigurationValuesToInsert(
          id,
          sequenceSize = sequenceSize,
          newProposalsRatio = newProposalsRatio,
          maxTestedProposalCount = maxTestedProposalCount,
          selectionAlgorithmName = selectionAlgorithmName,
          intraIdeaEnabled = intraIdeaEnabled,
          intraIdeaMinCount = intraIdeaMinCount,
          intraIdeaProposalsRatio = intraIdeaProposalsRatio,
          interIdeaCompetitionEnabled = interIdeaCompetitionEnabled,
          interIdeaCompetitionTargetCount = interIdeaCompetitionTargetCount,
          interIdeaCompetitionControversialRatio = interIdeaCompetitionControversialRatio,
          interIdeaCompetitionControversialCount = interIdeaCompetitionControversialCount
        )
      }

      def otherSequenceValuesToInsert(id: String): String = {
        specificConfigurationValuesToInsert(
          id,
          sequenceSize = 12,
          newProposalsRatio = 0.5,
          maxTestedProposalCount = 1000,
          selectionAlgorithmName = SelectionAlgorithmName.Random.value,
          intraIdeaEnabled = false,
          intraIdeaMinCount = 0,
          intraIdeaProposalsRatio = 0,
          interIdeaCompetitionEnabled = false,
          interIdeaCompetitionTargetCount = 0,
          interIdeaCompetitionControversialRatio = 0,
          interIdeaCompetitionControversialCount = 0
        )
      }

      val mainSequenceInsertStatement = connection.prepareStatement(
        "INSERT INTO specific_sequence_configuration (id," +
          "sequence_size, new_proposals_ratio, max_tested_proposal_count, selection_algorithm_name, intra_idea_enabled, " +
          "intra_idea_min_count, intra_idea_proposals_ratio, inter_idea_competition_enabled, inter_idea_competition_target_count, " +
          "inter_idea_competition_controversial_ratio, inter_idea_competition_controversial_count) " +
          s"VALUES ${mainSequenceValuesToInsert(mainSequenceConfigurationId)}"
      )
      mainSequenceInsertStatement.execute()
      val controversialSequenceInsertStatement = connection.prepareStatement(
        "INSERT INTO specific_sequence_configuration (id," +
          "sequence_size, new_proposals_ratio, max_tested_proposal_count, selection_algorithm_name, intra_idea_enabled, " +
          "intra_idea_min_count, intra_idea_proposals_ratio, inter_idea_competition_enabled, inter_idea_competition_target_count, " +
          "inter_idea_competition_controversial_ratio, inter_idea_competition_controversial_count) " +
          s"VALUES ${otherSequenceValuesToInsert(controversialSequenceConfigurationId)}"
      )
      controversialSequenceInsertStatement.execute()
      val popularSequenceInsertStatement = connection.prepareStatement(
        "INSERT INTO specific_sequence_configuration (id," +
          "sequence_size, new_proposals_ratio, max_tested_proposal_count, selection_algorithm_name, intra_idea_enabled, " +
          "intra_idea_min_count, intra_idea_proposals_ratio, inter_idea_competition_enabled, inter_idea_competition_target_count, " +
          "inter_idea_competition_controversial_ratio, inter_idea_competition_controversial_count) " +
          s"VALUES ${otherSequenceValuesToInsert(popularSequenceConfigurationId)}"
      )
      popularSequenceInsertStatement.execute()
      val keywordSequenceInsertStatement = connection.prepareStatement(
        "INSERT INTO specific_sequence_configuration (id," +
          "sequence_size, new_proposals_ratio, max_tested_proposal_count, selection_algorithm_name, intra_idea_enabled, " +
          "intra_idea_min_count, intra_idea_proposals_ratio, inter_idea_competition_enabled, inter_idea_competition_target_count, " +
          "inter_idea_competition_controversial_ratio, inter_idea_competition_controversial_count) " +
          s"VALUES ${otherSequenceValuesToInsert(keywordSequenceConfigurationId)}"
      )
      keywordSequenceInsertStatement.execute()

      val updateStatement = connection.prepareStatement(
        "UPDATE sequence_configuration SET main = ?, controversial = ?, " +
          "popular = ?, keyword = ? WHERE sequence_id = ?"
      )
      updateStatement.setString(1, mainSequenceConfigurationId)
      updateStatement.setString(2, controversialSequenceConfigurationId)
      updateStatement.setString(3, popularSequenceConfigurationId)
      updateStatement.setString(4, keywordSequenceConfigurationId)
      updateStatement.setString(5, sequenceId)
      updateStatement.execute()
    }
  }
}
