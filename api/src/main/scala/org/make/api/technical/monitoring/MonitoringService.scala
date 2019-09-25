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

trait DefaultMonitoringService extends MonitoringServiceComponent {
  override lazy val monitoringService: MonitoringService = new DefaultMonitoringService

  class DefaultMonitoringService extends MonitoringService {

    val maxLoadingTime: Long = 1.minute.toMillis
    val range = DynamicRange(1L, maxLoadingTime, 2)

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

    override def monitorPerformance(applicationName: String, metrics: FrontPerformanceTimings): Unit = {
      getHistogram(HistogramName(applicationName, "connect")).record(metrics.connectEnd - metrics.connectStart)
      getHistogram(HistogramName(applicationName, "domain_lookup"))
        .record(metrics.domainLookupEnd - metrics.domainLookupStart)
      if (metrics.domComplete > 0) {
        getHistogram(HistogramName(applicationName, "dom_complete")).record(metrics.domComplete - metrics.responseEnd)
      }
      if (metrics.domInteractive > 0) {
        getHistogram(HistogramName(applicationName, "dom_interactive"))
          .record(metrics.domInteractive - metrics.responseEnd)
      }
      getHistogram(HistogramName(applicationName, "dom_loading")).record(metrics.domLoading - metrics.responseEnd)
      getHistogram(HistogramName(applicationName, "request_time")).record(metrics.responseEnd - metrics.requestStart)
      getHistogram(HistogramName(applicationName, "first_byte")).record(metrics.responseStart - metrics.requestStart)
      getHistogram(HistogramName(applicationName, "transfer_time")).record(metrics.responseEnd - metrics.responseStart)
    }
  }
}

object MonitoringMessageHelper {
  def format(value: String): String = value.filterNot(_ < ' ').replace("\"", "\\\"")
}
