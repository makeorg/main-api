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

package org.make.api.technical

import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandlerComponent}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class StreamUtilsTest
    extends MakeApiTestBase
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with MakeAuthentication
    with ScalaFutures {

  val elements: Range.Inclusive = 0.to(10)

  feature("async source") {

    scenario("empty source") {
      val source = StreamUtils.asyncPageToPageSource[Int](_ => Future.successful(Seq.empty))
      val result = source.runFold(0) { case (acc, value) => value.sum + acc }
      whenReady(result, Timeout(2.seconds)) { _ should be(0) }
    }

    scenario("source always returning the same number of elements") {
      val source =
        StreamUtils.asyncPageToPageSource[Int](offset => Future.successful(elements.slice(offset, offset + 2)))
      val result = source.runFold(0) { case (acc, value) => value.sum + acc }
      whenReady(result, Timeout(2.seconds)) { _ should be(elements.sum) }
    }

    scenario("source not returning the same number of elements ") {
      val source =
        StreamUtils.asyncPageToPageSource[Int](offset => Future.successful(elements.slice(offset, offset + 3)))
      val result = source.runFold(0) { case (acc, value) => value.sum + acc }
      whenReady(result, Timeout(2.seconds)) { _ should be(elements.sum) }
    }

  }

}
