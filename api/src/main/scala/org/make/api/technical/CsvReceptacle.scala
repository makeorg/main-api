/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical

import akka.http.scaladsl.common.RepeatedValueReceptacle
import akka.http.scaladsl.server.directives.ParameterDirectives.ParamSpec
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling.FromStringUnmarshaller

final case class CsvReceptacle[T](underlying: RepeatedValueReceptacle[Seq[T]])

object CsvReceptacle extends Directives {

  implicit class RepeatedCsvFlatteningParamOps(val name: String) extends AnyVal {
    def csv[T]: CsvReceptacle[T] = CsvReceptacle(name.as[Seq[T]].*)
  }

  implicit def paramSpec[T: FromStringUnmarshaller](a: CsvReceptacle[T]): ParamSpec.Aux[Option[Seq[T]]] =
    ParamSpec(
      ParamSpec.forRepVR(a.underlying).get.map(nested => if (nested.isEmpty) None else Some(nested.flatten.toSeq))
    )

}
