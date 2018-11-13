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

class V22_2__Populate_question_slugs_for_themes extends BaseJavaMigration {

  override def migrate(context: Context): Unit = {
    val connection = context.getConnection
    val resultSet = connection
      .prepareStatement("SELECT slug, theme_uuid AS theme_id FROM theme_translation")
      .executeQuery()

    while (resultSet.next()) {
      val slug = resultSet.getString("slug")
      val themeId = resultSet.getString("theme_id")
      val statement = connection.prepareStatement("UPDATE question SET slug = ? WHERE theme_id = ?")
      statement.setString(1, slug)
      statement.setString(2, themeId)
      statement.execute()
    }

    connection.commit()

  }
}