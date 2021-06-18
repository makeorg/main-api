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

package org.make.core.technical

import org.make.core.EventId

import java.util.UUID
import org.make.core.idea.IdeaMappingId
import org.make.core.auth.ClientId
import org.make.core.crmTemplate.CrmQuestionTemplateId
import org.make.core.feature.{ActiveFeatureId, FeatureId}
import org.make.core.idea.{IdeaId, TopIdeaCommentId, TopIdeaId}
import org.make.core.job.Job.JobId
import org.make.core.operation.OperationId
import org.make.core.partner.PartnerId
import org.make.core.personality.{PersonalityId, PersonalityRoleFieldId, PersonalityRoleId}
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.sequence.{ExplorationSequenceConfigurationId, SequenceId, SpecificSequenceConfigurationId}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.tag.{TagId, TagTypeId}
import org.make.core.user.UserId

trait IdGenerator {
  def nextClientId(): ClientId = ClientId(nextId())
  def nextCrmQuestionTemplateId(): CrmQuestionTemplateId = CrmQuestionTemplateId(nextId())
  def nextFeatureId(): FeatureId = FeatureId(nextId())
  def nextActiveFeatureId(): ActiveFeatureId = ActiveFeatureId(nextId())
  def nextIdeaId(): IdeaId = IdeaId(nextId())
  def nextIdeaMappingId(): IdeaMappingId = IdeaMappingId(nextId())
  def nextJobId(): JobId = JobId(nextId())
  def nextOperationId(): OperationId = OperationId(nextId())
  def nextPartnerId(): PartnerId = PartnerId(nextId())
  def nextProposalId(): ProposalId = ProposalId(nextId())
  def nextQuestionId(): QuestionId = QuestionId(nextId())
  def nextSequenceId(): SequenceId = SequenceId(nextId())
  def nextTagId(): TagId = TagId(nextId())
  def nextTagTypeId(): TagTypeId = TagTypeId(nextId())
  def nextUserId(): UserId = UserId(nextId())
  def nextVisitorId(): VisitorId = VisitorId(nextId())
  def nextPersonalityId(): PersonalityId = PersonalityId(nextId())
  def nextTopIdeaId(): TopIdeaId = TopIdeaId(nextId())
  def nextTopIdeaCommentId(): TopIdeaCommentId = TopIdeaCommentId(nextId())
  def nextPersonalityRoleId(): PersonalityRoleId = PersonalityRoleId(nextId())
  def nextPersonalityRoleFieldId(): PersonalityRoleFieldId = PersonalityRoleFieldId(nextId())
  def nextSessionId(): SessionId = SessionId(nextId())
  def nextEventId(): EventId = EventId(nextId())
  def nextSpecificSequenceConfigurationId(): SpecificSequenceConfigurationId = SpecificSequenceConfigurationId(nextId())
  def nextExplorationSequenceConfigurationId(): ExplorationSequenceConfigurationId =
    ExplorationSequenceConfigurationId(nextId())

  def nextId(): String
}

object IdGenerator {
  val uuidGenerator: IdGenerator = () => {
    UUID.randomUUID().toString
  }
}
