/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

import com.typesafe.config.Config
import org.make.api.{ConfigComponent, MakeUnitTest}
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.technical.security.{SecurityConfiguration, SecurityConfigurationComponent, SecurityHelper}
import org.make.core.DefaultDateHelperComponent
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.user.UserId
import org.make.core.widget.{SourceId, Widget}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.net.URL
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class WidgetServiceTest
    extends MakeUnitTest
    with ConfigComponent
    with DefaultDateHelperComponent
    with DefaultIdGeneratorComponent
    with DefaultWidgetServiceComponent
    with PersistentWidgetServiceComponent
    with SecurityConfigurationComponent {

  override val config: Config = mock[Config]
  override val persistentWidgetService: PersistentWidgetService = mock[PersistentWidgetService]
  override val securityConfiguration: SecurityConfiguration = mock[SecurityConfiguration]

  when(config.getString("make-api.urls.widget")).thenReturn("http://localhost")
  when(persistentWidgetService.persist(any)).thenAnswer { w: Widget =>
    Future.successful(w)
  }
  when(securityConfiguration.secureHashSalt).thenReturn("pepper")

  Feature("create a widget") {
    Scenario("hash is valid") {
      val s = source(id = SourceId("source"), userId = UserId("123"))
      val q = question(id = QuestionId("foo"), slug = "widget_test")

      whenReady(widgetService.create(s, q, Country("FR"), UserId("456")), Timeout(10.seconds)) { widget =>
        val tmp = widget.script.splitAt(widget.script.indexOf("src=\"") + 5)._2

        val query = new URL(tmp.splitAt(tmp.indexOf("\">"))._1).getQuery
        val (url, hash) = query.splitAt(query.indexOf("&hash="))
        SecurityHelper.validateSecureHash(hash.substring(6), url.prepended('?'), "pepper") should be(true)
      }
    }
  }

}
