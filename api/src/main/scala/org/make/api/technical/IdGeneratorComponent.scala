package org.make.api.technical

import java.util.UUID

import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId

trait IdGeneratorComponent {
  def idGenerator: IdGenerator
}

trait IdGenerator {
  def nextUserId(): UserId = UserId(nextId())
  def nextProposalId(): ProposalId = ProposalId(nextId())
  def nextSequenceId(): SequenceId = SequenceId(nextId())
  def nextOperationId(): OperationId = OperationId(nextId())
  def nextId(): String
}

trait DefaultIdGeneratorComponent extends IdGeneratorComponent {
  override lazy val idGenerator: IdGenerator = new IdGenerator {
    override def nextId(): String = {
      UUID.randomUUID().toString
    }
  }
}
