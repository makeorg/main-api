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

package org.make.api.serialization

import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.Base64

object CreateSerializationFromJson extends App {

  val json =
    """
      |{"author":{"userId":"my-user-id","firstName":"first name","postalCode":"75011","age":42},"slug":"my-proposal","id":"proposal-id","operation":"my-operation","eventDate":"2018-03-01T16:09:30.441Z","content":"my proposal","requestContext":{"requestId":"","sessionId":"","externalId":""},"userId":"my-user-id","theme":"theme-id"}
    """.stripMargin

  val data = Base64.getEncoder.encode(json.getBytes("UTF-8"))
  val destination =
    Paths.get("/home/francois/dev/make/make-api/api/src/test/resources/serialization/proposal-proposed-v1-default")

  Files.write(destination, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

}
