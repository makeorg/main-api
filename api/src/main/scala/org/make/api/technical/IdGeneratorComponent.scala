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

import java.util.UUID

import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId
import org.make.core.session.VisitorId
import org.make.core.tag.{TagId, TagTypeId}
import org.make.core.user.UserId

trait IdGeneratorComponent {
  def idGenerator: IdGenerator
}

trait IdGenerator {
  def nextUserId(): UserId = UserId(nextId())
  def nextProposalId(): ProposalId = ProposalId(nextId())
  def nextSequenceId(): SequenceId = SequenceId(nextId())
  def nextOperationId(): OperationId = OperationId(nextId())
  def nextVisitorId(): VisitorId = VisitorId(nextId())
  def nextQuestionId(): QuestionId = QuestionId(nextId())
  def nextTagId(): TagId = TagId(nextId())
  def nextTagTypeId(): TagTypeId = TagTypeId(nextId())
  def nextId(): String
}

trait DefaultIdGeneratorComponent extends IdGeneratorComponent {
  override lazy val idGenerator: IdGenerator = new IdGenerator {
    override def nextId(): String = {
      UUID.randomUUID().toString
    }
  }
}
