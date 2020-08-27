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

import org.flywaydb.core.api.migration._

class V24_1__Populate_question_id_for_sequence_configuration extends BaseJavaMigration {

  @SuppressWarnings(Array("org.wartremover.warts.While"))
  override def migrate(context: Context): Unit = {
    val connection = context.getConnection
    val resultSet = connection
      .prepareStatement(
        "SELECT landing_sequence_id, question_id FROM operation_country_configuration " +
          "INNER JOIN question ON operation_uuid = operation_id"
      )
      .executeQuery()

    while (resultSet.next()) {
      val sequenceId = resultSet.getString("landing_sequence_id")
      val questionId = resultSet.getString("question_id")
      val statement =
        connection.prepareStatement("UPDATE sequence_configuration SET question_id = ? WHERE sequence_id = ?")
      statement.setString(1, questionId)
      statement.setString(2, sequenceId)
      statement.execute()
    }

    connection.commit()

  }
}
