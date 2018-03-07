package org.make.api.semantic

import com.typesafe.config.Config
import org.make.api.ActorSystemComponent

class SemanticConfiguration(config: Config) {
  val url: String = config.getString("url")
}

trait SemanticConfigurationComponent {
  def semanticConfiguration: SemanticConfiguration
}

trait DefaultSemanticConfigurationComponent extends SemanticConfigurationComponent {
  this: ActorSystemComponent =>
  override lazy val semanticConfiguration: SemanticConfiguration = new SemanticConfiguration(
    actorSystem.settings.config.getConfig("make-api.semantic")
  )
}
