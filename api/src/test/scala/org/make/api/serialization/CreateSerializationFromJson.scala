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
      |{"votesAndQualifications":{"df9c3bac-ca5d-43dc-87fb-c4980c711297":{"voteKey":"neutral","qualificationKeys":[]},"9c2dfbcd-1a52-4337-9378-47c5ef1e94a6":{"voteKey":"neutral","qualificationKeys":[]},"17f40d57-f510-4049-bb1a-9637193107d6":{"voteKey":"neutral","qualificationKeys":[]},"857a9689-9e97-4811-b18e-3f4e8f993b0d":{"voteKey":"neutral","qualificationKeys":[]}}}
    """.stripMargin

  val data = Base64.getEncoder.encode(json.getBytes("UTF-8"))
  val destination =
    Paths.get(
      "/home/francois/dev/make/make-api/api/src/test/resources/serialization/user-votes-and-qualifications-v1-multiple"
    )

  Files.write(destination, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

}
