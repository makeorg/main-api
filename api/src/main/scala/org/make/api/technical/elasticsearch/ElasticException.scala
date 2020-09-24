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

package org.make.api.technical.elasticsearch

import com.sksamuel.elastic4s.http.ElasticError

final case class ElasticException(message: String) extends RuntimeException(message) {
  def this(elasticError: ElasticError) =
    this(s"${elasticError.reason} ${elasticError.causedBy.map(_.toString)}")

  def this(elasticError: ElasticError, relatedRequest: String) =
    this(s"${elasticError.reason} ${elasticError.causedBy.map(_.toString)}\nCaused by request: $relatedRequest ")
}
