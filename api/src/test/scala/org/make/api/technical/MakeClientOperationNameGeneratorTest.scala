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

import org.make.api.MakeUnitTest
import org.scalatest.BeforeAndAfterEach
import org.make.api.technical.tracing.Tracing
import kamon.instrumentation.http.HttpMessage
import kamon.Kamon

class MakeClientOperationNameGeneratorTest extends MakeUnitTest with BeforeAndAfterEach {

  val generator = new MakeClientOperationNameGenerator

  override def beforeEach(): Unit = {
    super.beforeEach()
    Tracing.clearEntrypoint()
  }

  val request: HttpMessage.Request = new HttpMessage.Request {
    override def host: String = "make.org"
    override def read(header: String): Option[String] = readAll().get(header)
    override def readAll(): Map[String, String] = Map.empty
    override def url: String = "https://make.org/FR-fr/consultation/environnement/selection?utm=value"
    override def path: String = "/FR-fr/consultation/environnement/selection"
    override def method: String = "GET"
    override def port: Int = 443
  }

  feature("operation name generation") {

    scenario("generation not inside a span") {
      generator.name(request) should contain("make.org/FR-fr/consultation/environnement/selection")
    }

    scenario("inside a span") {
      val span = Kamon.spanBuilder("test-operation").doNotTrackMetrics().start()
      Kamon.runWithSpan(span) {
        generator.name(request) should contain("test-operation")
      }
      span.finish()
    }

    scenario("inside a span with the same name as the main operation") {
      val span = Kamon.spanBuilder("test-operation").start()
      Tracing.entrypoint("test-operation")
      Kamon.runWithSpan(Kamon.spanBuilder("test-operation").start()) {
        generator.name(request) should contain("make.org/FR-fr/consultation/environnement/selection")
      }
      span.finish()
    }
  }

}
