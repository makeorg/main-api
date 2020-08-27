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

class V39_1__Migrate_start_end_date extends BaseJavaMigration {

  @SuppressWarnings(Array("org.wartremover.warts.While"))
  override def migrate(context: Context): Unit = {
    val connection = context.getConnection
    val resultSet = connection
      .prepareStatement("SELECT question_id, start_date, end_date FROM operation_of_question")
      .executeQuery()

    while (resultSet.next()) {
      val questionId = resultSet.getString("question_id")
      val startDate = resultSet.getString("start_date")
      val endDate = resultSet.getString("end_date")
      val statement =
        connection.prepareStatement(
          "UPDATE operation_of_question SET new_start_date = ?, new_end_date = ? WHERE question_id = ?"
        )
      statement.setString(1, startDate)
      statement.setString(2, endDate)
      statement.setString(3, questionId)
      statement.execute()
    }

    connection.commit()
  }
}
