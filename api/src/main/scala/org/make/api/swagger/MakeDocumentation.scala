package org.make.api.swagger
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.github.swagger.akka.{HasActorSystem, SwaggerHttpService}
import com.typesafe.scalalogging.StrictLogging
import io.swagger.models.auth.{OAuth2Definition, SecuritySchemeDefinition}

import scala.collection.JavaConverters._
import scala.reflect.runtime.{universe => ru}

class MakeDocumentation(system: ActorSystem, override val apiTypes: Seq[ru.Type]) extends SwaggerHttpService with HasActorSystem with Directives with StrictLogging {

  override implicit val actorSystem: ActorSystem = system
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override val securitySchemeDefinitions: Map[String, SecuritySchemeDefinition] = Map(
    "MakeApi" -> {
      val definition = new OAuth2Definition()
      definition.setFlow("implicit")
      definition.setTokenUrl("/oauth/access_token")
      definition.setAuthorizationUrl("/login.html")
      definition.setType("oauth2")
      definition.setScopes(
        Map("user" -> "application user", "admin" -> "application admin").asJava
      )
      definition
    }
  )

  /*
   "petstore_auth": {
    "type": "oauth2",
    "authorizationUrl": "http://swagger.io/api/oauth/dialog",
    "flow": "implicit",
    "scopes": {
      "write:pets": "modify pets in your account",
      "read:pets": "read your pets"
    }
  }
  */
}
