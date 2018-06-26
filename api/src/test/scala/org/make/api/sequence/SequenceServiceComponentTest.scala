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

package org.make.api.sequence

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeTest
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.proposal._
import org.make.api.sessionhistory.{SessionHistoryCoordinatorService, SessionHistoryCoordinatorServiceComponent}
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGenerator, IdGeneratorComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.core.idea.IdeaId
import org.make.core.proposal._
import org.make.core.user.UserId
import org.make.core.{proposal, DateHelper, RequestContext}
import org.scalatest.PrivateMethodTester

class SequenceServiceComponentTest
    extends MakeTest
    with PrivateMethodTester
    with DefaultSequenceServiceComponent
    with SequenceConfigurationComponent
    with IdGeneratorComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with SequenceServiceComponent
    with SequenceSearchEngineComponent
    with ProposalSearchEngineComponent
    with SequenceCoordinatorServiceComponent
    with EventBusServiceComponent
    with UserServiceComponent
    with MakeSettingsComponent
    with SelectionAlgorithmComponent
    with StrictLogging {

  override val eventBusService: EventBusService = mock[EventBusService]
  override val elasticsearchSequenceAPI: SequenceSearchEngine = mock[SequenceSearchEngine]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val sequenceCoordinatorService: SequenceCoordinatorService = mock[SequenceCoordinatorService]
  override val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService =
    mock[SessionHistoryCoordinatorService]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService =
    mock[UserHistoryCoordinatorService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val userService: UserService = mock[UserService]
  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val selectionAlgorithm: SelectionAlgorithm = mock[SelectionAlgorithm]
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]

  val defaultSize = 12
  val proposalIds: Seq[ProposalId] = (1 to defaultSize).map(i => ProposalId(s"proposal$i"))

  def fakeProposal(id: ProposalId,
                   votes: Map[VoteKey, Int],
                   ideaId: Option[IdeaId],
                   duplicates: Seq[ProposalId],
                   createdAt: ZonedDateTime = DateHelper.now()): Proposal = {
    proposal.Proposal(
      proposalId = id,
      author = UserId("fake"),
      content = "fake",
      slug = "fake",
      status = ProposalStatus.Accepted,
      createdAt = Some(createdAt),
      updatedAt = None,
      votes = votes.map {
        case (key, amount) => Vote(key = key, count = amount, qualifications = Seq.empty)
      }.toSeq,
      labels = Seq.empty,
      theme = None,
      refusalReason = None,
      tags = Seq.empty,
      similarProposals = duplicates,
      idea = ideaId,
      events = Nil,
      creationContext = RequestContext.empty,
      language = Some("fr"),
      country = Some("FR")
    )
  }

  feature("Starting a sequence") {}
}
