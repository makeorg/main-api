package org.make.api.swagger
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import com.github.swagger.akka.{HasActorSystem, SwaggerHttpService}
import com.typesafe.scalalogging.StrictLogging
import org.make.api.citizen.CitizenApi

import scala.reflect.runtime.{universe => ru}

class MakeDocumentation(system: ActorSystem) extends SwaggerHttpService with HasActorSystem with Directives with StrictLogging {

  override implicit val actorSystem: ActorSystem = system
  override val apiTypes: Seq[ru.Type] = Seq(ru.typeOf[CitizenApi])
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
}
