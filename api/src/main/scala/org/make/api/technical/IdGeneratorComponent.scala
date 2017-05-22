package org.make.api.technical

import java.util.UUID

import org.make.core.citizen.CitizenId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteId

trait IdGeneratorComponent {

  def idGenerator: IdGenerator

  trait IdGenerator {
    def nextCitizenId(): CitizenId = CitizenId(nextId())
    def nextPropositionId(): PropositionId = PropositionId(nextId())
    def nextVoteId(): VoteId = VoteId(nextId())
    def nextId(): String
  }

  class UUIDIdGenerator extends IdGenerator {
    override def nextId(): String = {
      UUID.randomUUID().toString
    }
  }


}
