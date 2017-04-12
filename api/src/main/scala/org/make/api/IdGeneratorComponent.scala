package org.make.api

import java.util.UUID

import org.make.core.citizen.CitizenId

trait IdGeneratorComponent {

  def idGenerator: IdGenerator

  trait IdGenerator {
    def nextCitizenId(): CitizenId = {
      CitizenId(nextId())
    }
    def nextId(): String
  }

  class UUIDIdGenerator extends IdGenerator {
    override def nextId(): String = {
      UUID.randomUUID().toString
    }
  }


}
