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

package org.make.api.technical.monitoring
import com.typesafe.scalalogging.StrictLogging
import kamon.Kamon
import kamon.metric.{DynamicRange, Histogram, MeasurementUnit}
import kamon.tag.TagSet
import org.make.api.technical.tracking.FrontPerformanceTimings

import scala.concurrent.duration.DurationInt

trait MonitoringService {
  def monitorPerformance(applicationName: String, metrics: FrontPerformanceTimings): Unit
}

trait MonitoringServiceComponent {
  def monitoringService: MonitoringService
}

final case class HistogramName(applicationName: String, metricName: String) {
  def fullMetricName: String = s"loadtime.$applicationName.$metricName"
}

trait DefaultMonitoringService extends MonitoringServiceComponent with StrictLogging {
  override lazy val monitoringService: MonitoringService = new DefaultMonitoringService

  class DefaultMonitoringService extends MonitoringService {

    private val maxLoadingTime: Long = 1.minute.toMillis
    private val range: DynamicRange = DynamicRange(1L, maxLoadingTime, 2)

    private var histograms: Map[HistogramName, Histogram] = Map.empty

    private def getHistogram(histogramName: HistogramName): Histogram = {
      if (!histograms.contains(histogramName)) {
        val value =
          Kamon
            .histogram(name = "load_time", unit = MeasurementUnit.time.milliseconds, dynamicRange = range)
            .withTags(
              TagSet.from(
                Map(
                  "application" -> MonitoringMessageHelper.format(histogramName.applicationName),
                  "metric" -> histogramName.metricName
                )
              )
            )

        histograms += histogramName -> value
      }
      histograms(histogramName)
    }

    private def recordIfPositive(applicationName: String, metric: String, value: Long): Unit = {
      if (value >= 0) {
        getHistogram(HistogramName(applicationName, metric)).record(value)
      } else {
        logger.warn(s"discarding metric $metric for $applicationName since it results in a negative metric")
      }
    }
    override def monitorPerformance(applicationName: String, metrics: FrontPerformanceTimings): Unit = {
      recordIfPositive(applicationName, "connect", metrics.connectEnd - metrics.connectStart)
      recordIfPositive(applicationName, "domain_lookup", metrics.domainLookupEnd - metrics.domainLookupStart)
      recordIfPositive(applicationName, "dom_complete", metrics.domComplete - metrics.responseEnd)
      recordIfPositive(applicationName, "dom_interactive", metrics.domInteractive - metrics.responseEnd)
      recordIfPositive(applicationName, "dom_loading", metrics.domLoading - metrics.responseEnd)
      recordIfPositive(applicationName, "request_time", metrics.responseEnd - metrics.requestStart)
      recordIfPositive(applicationName, "first_byte", metrics.responseStart - metrics.requestStart)
      recordIfPositive(applicationName, "transfer_time", metrics.responseEnd - metrics.responseStart)
    }
  }
}

object MonitoringMessageHelper {
  def format(value: String): String = value.filterNot(_ < ' ').replace("\"", "\\\"")
}
