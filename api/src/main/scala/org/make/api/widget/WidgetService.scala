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

package org.make.api.widget

import org.make.api.ConfigComponent
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.security.{SecurityConfigurationComponent, SecurityHelper}
import org.make.core.question.Question
import org.make.core.reference.Country
import org.make.core.{DateHelperComponent, Order}
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.user.UserId
import org.make.core.widget.{Source, SourceId, Widget, WidgetId}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.concurrent.Future

trait WidgetService {
  def get(id: WidgetId): Future[Option[Widget]]
  def count(sourceId: SourceId): Future[Int]
  def list(
    sourceId: SourceId,
    start: Option[Start],
    end: Option[End],
    sort: Option[String],
    order: Option[Order]
  ): Future[Seq[Widget]]
  def create(source: Source, question: Question, country: Country, author: UserId): Future[Widget]
}

trait WidgetServiceComponent {
  def widgetService: WidgetService
}

trait DefaultWidgetServiceComponent extends WidgetServiceComponent {
  self: ConfigComponent
    with DateHelperComponent
    with IdGeneratorComponent
    with PersistentWidgetServiceComponent
    with SecurityConfigurationComponent =>

  override val widgetService: WidgetService = new WidgetService {

    override def get(id: WidgetId): Future[Option[Widget]] = persistentWidgetService.get(id)

    override def count(sourceId: SourceId): Future[Int] = persistentWidgetService.count(sourceId)

    override def list(
      sourceId: SourceId,
      start: Option[Start],
      end: Option[End],
      sort: Option[String],
      order: Option[Order]
    ): Future[Seq[Widget]] = persistentWidgetService.list(sourceId, start, end, sort, order)

    override def create(source: Source, question: Question, country: Country, author: UserId): Future[Widget] = {
      val title = URLEncoder.encode(question.question, StandardCharsets.UTF_8)
      val color = "0:0:0:1"
      val url =
        s"?questionSlug=${question.slug}&title=${title}&source=${source.source}&color=${color}&country=${country.value}&language=${question.language.value}&owner=${author.value}"
      val hash = SecurityHelper.createSecureHash(url, securityConfiguration.secureHashSalt)
      val script =
        s"""<iframe frameborder="0" scrolling="no" width="100%" height="575" style="min-height: 575px" src="${config
          .getString("make-api.urls.widget")}/$url&hash=$hash"></iframe>"""
      persistentWidgetService.persist(
        Widget(
          id = idGenerator.nextWidgetId(),
          sourceId = source.id,
          questionId = question.questionId,
          country = country,
          author = author,
          version = Widget.Version.V1,
          script = script,
          createdAt = dateHelper.now()
        )
      )
    }

  }
}
