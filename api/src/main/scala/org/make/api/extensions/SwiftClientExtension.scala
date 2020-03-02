package org.make.api.extensions

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import org.make.swift.SwiftClient

class SwiftClientExtension(val swiftClient: SwiftClient) extends Extension

object SwiftClientExtension extends ExtensionId[SwiftClientExtension] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): SwiftClientExtension =
    new SwiftClientExtension(SwiftClient.create(system))

  override def lookup(): ExtensionId[SwiftClientExtension] =
    SwiftClientExtension
}
