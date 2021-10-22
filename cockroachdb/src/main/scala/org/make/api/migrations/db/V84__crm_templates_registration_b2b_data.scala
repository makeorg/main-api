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

class V84__crm_templates_registration_b2b_data extends Migration {

  override def migrate(connection: Connection): Unit = {
    val isProd = System.getenv("ENV_NAME") == "prod"

    val registrationB2BTemplateId = if (isProd) "1395993" else "1393409"

    val statement =
      connection.prepareStatement(
        s"ALTER TABLE crm_templates ADD COLUMN IF NOT EXISTS registration_b2b STRING NOT NULL DEFAULT '$registrationB2BTemplateId'"
      )
    statement.execute()
  }

}
