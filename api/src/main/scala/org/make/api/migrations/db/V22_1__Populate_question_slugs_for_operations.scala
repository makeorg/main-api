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

class V22_1__Populate_question_slugs_for_operations extends BaseJavaMigration {

  override def migrate(context: Context): Unit = {
    val connection = context.getConnection
    val resultSet = connection
      .prepareStatement(
        "SELECT slug, country, operation.uuid AS operation_id " +
          "FROM operation INNER JOIN operation_country_configuration AS config " +
          "ON operation.uuid = config.operation_uuid"
      )
      .executeQuery()

    while (resultSet.next()) {
      val slug = resultSet.getString("slug")
      val country = resultSet.getString("country")
      val operationId = resultSet.getString("operation_id")
      val resolvedSlug = if (country == "FR") {
        slug
      } else {
        s"$slug-${country.toLowerCase()}"
      }
      val statement = connection.prepareStatement("UPDATE question SET slug = ? WHERE operation_id = ? AND country = ?")
      statement.setString(1, resolvedSlug)
      statement.setString(2, operationId)
      statement.setString(3, country)
      statement.execute()
    }

    connection.commit()

  }
}
