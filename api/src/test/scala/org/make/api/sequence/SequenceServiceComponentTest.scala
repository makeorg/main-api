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

import grizzled.slf4j.Logging
import org.make.api.MakeUnitTest
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.operation.{OperationOfQuestionSearchEngine, OperationOfQuestionSearchEngineComponent}
import org.make.api.proposal._
import org.make.api.segment.{SegmentService, SegmentServiceComponent}
import org.make.api.sessionhistory.{SessionHistoryCoordinatorService, SessionHistoryCoordinatorServiceComponent}
import org.make.api.technical.security.{SecurityConfiguration, SecurityConfigurationComponent}
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGeneratorComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.core.RequestContext
import org.make.core.proposal.ProposalKeywordKey
import org.make.core.proposal.indexed.Zone
import org.make.core.question.QuestionId
import org.make.core.tag.TagId
import org.make.core.technical.IdGenerator
import org.scalatest.PrivateMethodTester
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class SequenceServiceComponentTest
    extends MakeUnitTest
    with PrivateMethodTester
    with DefaultSequenceServiceComponent
    with SequenceConfigurationComponent
    with IdGeneratorComponent
    with ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with SequenceServiceComponent
    with ProposalSearchEngineComponent
    with EventBusServiceComponent
    with UserServiceComponent
    with MakeSettingsComponent
    with SelectionAlgorithmComponent
    with SecurityConfigurationComponent
    with SegmentServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with Logging {

  override val eventBusService: EventBusService = mock[EventBusService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService =
    mock[SessionHistoryCoordinatorService]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService =
    mock[UserHistoryCoordinatorService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val userService: UserService = mock[UserService]
  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val banditSelectionAlgorithm: SelectionAlgorithm = mock[SelectionAlgorithm]
  override val roundRobinSelectionAlgorithm: SelectionAlgorithm = mock[SelectionAlgorithm]
  override val randomSelectionAlgorithm: SelectionAlgorithm = mock[SelectionAlgorithm]
  override val sequenceConfigurationService: SequenceConfigurationService = mock[SequenceConfigurationService]
  override val securityConfiguration: SecurityConfiguration = mock[SecurityConfiguration]
  override val segmentService: SegmentService = mock[SegmentService]
  override val elasticsearchOperationOfQuestionAPI: OperationOfQuestionSearchEngine =
    mock[OperationOfQuestionSearchEngine]

  Feature("resolve behaviour") {
    val questionId = QuestionId("question-id")
    val requestContext = RequestContext.empty
    when(segmentService.resolveSegment(eqTo(requestContext))).thenReturn(Future.successful(None))
    when(sequenceConfigurationService.getSequenceConfigurationByQuestionId(eqTo(questionId)))
      .thenReturn(Future.successful(sequenceConfiguration(questionId)))

    Scenario("Standard") {
      whenReady(sequenceService.resolveBehaviour(questionId, requestContext, None, None, None), Timeout(3.seconds)) {
        behaviour =>
          behaviour shouldBe a[SequenceBehaviour.Standard]
      }
    }
    Scenario("Keyword") {
      whenReady(
        sequenceService
          .resolveBehaviour(questionId, requestContext, None, Some(ProposalKeywordKey("keyword")), None),
        Timeout(3.seconds)
      ) { behaviour =>
        behaviour shouldBe a[SequenceBehaviour.Keyword]
      }
    }
    Scenario("Tags") {
      whenReady(
        sequenceService.resolveBehaviour(questionId, requestContext, None, None, Some(Seq(TagId("tag-id")))),
        Timeout(3.seconds)
      ) { behaviour =>
        behaviour shouldBe a[SequenceBehaviour.Tags]
      }
    }
    Scenario("Consensus") {
      when(elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(questionId))
        .thenReturn(Future.successful(None))
      whenReady(
        sequenceService.resolveBehaviour(questionId, requestContext, Some(Zone.Consensus), None, None),
        Timeout(3.seconds)
      ) { behaviour =>
        behaviour shouldBe a[SequenceBehaviour.Consensus]
      }
    }
    Scenario("ZoneDefault") {
      whenReady(
        sequenceService.resolveBehaviour(questionId, requestContext, Some(Zone.Controversy), None, None),
        Timeout(3.seconds)
      ) { behaviour =>
        behaviour shouldBe a[SequenceBehaviour.ZoneDefault]
      }
    }
    Scenario("precedence") {
      whenReady(
        sequenceService.resolveBehaviour(
          questionId,
          requestContext,
          Some(Zone.Controversy),
          Some(ProposalKeywordKey("keyword")),
          Some(Seq(TagId("tag-id")))
        ),
        Timeout(3.seconds)
      ) { behaviour =>
        behaviour shouldBe a[SequenceBehaviour.Keyword]
      }
    }
  }
}
