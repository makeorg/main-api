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

import org.make.api.MakeUnitTest
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class FuturesTest extends MakeUnitTest {

  val defaultValue: Int = 42
  val alwaysRetry: Throwable => Boolean = _ => true

  Feature("Retry on futures") {
    Scenario("future succeeds") {

      val result = Futures.retry(() => Future.successful(defaultValue), alwaysRetry)

      whenReady(result, Timeout(5.seconds)) {
        _ should be(defaultValue)
      }
    }

    Scenario("future always fails") {
      val result = Futures.retry(() => Future.failed(new IllegalStateException("fake")), alwaysRetry)

      whenReady(result.failed, Timeout(5.seconds)) {
        _ should be(a[IllegalStateException])
      }
    }

    Scenario("future fails once") {
      val counter = new AtomicInteger(0)
      val function = () => {
        if (counter.getAndIncrement() == 0) {
          Future.failed(new IllegalStateException("fake"))
        } else {
          Future.successful(defaultValue)
        }
      }
      whenReady(Futures.retry(function, alwaysRetry), Timeout(3.seconds)) {
        _ should be(defaultValue)
      }
    }

    Scenario("retry on registered exceptions") {
      val counter = new AtomicInteger(0)
      val function = () => {
        if (counter.getAndIncrement() == 0) {
          Future.failed(new IllegalStateException("fake"))
        } else {
          Future.failed(new IllegalArgumentException("fake"))
        }
      }

      val retryOnIllegalStateException: Throwable => Boolean = _.isInstanceOf[IllegalStateException]

      whenReady(Futures.retry(function, retryOnIllegalStateException).failed, Timeout(3.seconds)) {
        _ should be(a[IllegalArgumentException])
      }
    }

  }

}
