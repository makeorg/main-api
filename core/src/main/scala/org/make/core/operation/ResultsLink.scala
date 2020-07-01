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

package org.make.core.operation

import java.net.URL

import cats.Show
import com.typesafe.scalalogging.StrictLogging
import enumeratum.values.{StringEnum, StringEnumEntry}
import org.make.core.CirceFormatters

import scala.util.Try

sealed trait ResultsLink extends Product with Serializable

object ResultsLink extends CirceFormatters with StrictLogging {

  def parse(value: String): Option[ResultsLink] = {
    val result = Internal.withValueOpt(value).orElse(Try(External(new URL(value))).toOption)
    if (result.isEmpty) {
      logger.error(s"'$value' is not a valid ResultsLink")
    }
    result
  }

  final case class External(value: URL) extends ResultsLink

  sealed abstract class Internal(val value: String) extends StringEnumEntry with ResultsLink
  object Internal extends StringEnum[Internal] {
    def unapply(internal: Internal): Some[String] = Some(internal.value)
    case object TopIdeas extends Internal("top-ideas")
    case object Results extends Internal("results")
    override def values: IndexedSeq[Internal] = findValues
  }

  implicit val resultsLinkShow: Show[ResultsLink] = Show.show {
    case External(url)   => url.toString
    case Internal(value) => value
  }

}
