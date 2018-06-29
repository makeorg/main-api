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

import akka.http.scaladsl.server.Directives
import com.github.swagger.akka.SwaggerHttpService
import io.swagger.models.Scheme
import io.swagger.models.auth.{OAuth2Definition, SecuritySchemeDefinition}

import scala.collection.JavaConverters._
import scalaoauth2.provider.OAuthGrantType

class MakeDocumentation(override val apiClasses: Set[Class[_]], ssl: Boolean)
    extends SwaggerHttpService
    with Directives {

  override def schemes: List[Scheme] =
    if (ssl) {
      List(Scheme.HTTPS)
    } else {
      List(Scheme.HTTP)
    }

  override val securitySchemeDefinitions: Map[String, SecuritySchemeDefinition] = Map("MakeApi" -> {
    val definition = new OAuth2Definition()
    definition.setFlow(OAuthGrantType.PASSWORD)
    definition.setTokenUrl("/oauth/make_access_token")
    definition.setType("oauth2")
    definition.setScopes(Map("user" -> "application user", "admin" -> "application admin").asJava)
    definition
  })

}
