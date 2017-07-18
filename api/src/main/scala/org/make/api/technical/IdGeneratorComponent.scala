package org.make.api.technical

import java.util.UUID

import org.make.core.user.UserId
import org.make.core.proposition.PropositionId
import org.make.core.vote.VoteId

trait IdGeneratorComponent {
  def idGenerator: IdGenerator
}

trait IdGenerator {
  def nextUserId(): UserId = UserId(nextId())
  def nextPropositionId(): PropositionId = PropositionId(nextId())
  def nextVoteId(): VoteId = VoteId(nextId())
  def nextId(): String
}

trait DefaultIdGeneratorComponent extends IdGeneratorComponent {
  override lazy val idGenerator = new IdGenerator {
    override def nextId(): String = {
      UUID.randomUUID().toString
    }
  }
}
