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
