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

package org.make.api.widget

import org.make.api.operation.PersistentOperationServiceComponent
import org.make.api.proposal._
import org.make.api.sequence.{SequenceConfigurationComponent, SequenceServiceComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent
import org.make.core.RequestContext
import org.make.core.question.QuestionId
import org.make.core.tag.TagId
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait WidgetServiceComponent {
  def widgetService: WidgetService
}

trait WidgetService {
  def startNewWidgetSequence(
    maybeUserId: Option[UserId],
    questionId: QuestionId,
    tagsIds: Option[Seq[TagId]],
    limit: Option[Int],
    requestContext: RequestContext
  ): Future[ProposalsResultSeededResponse]
}

trait DefaultWidgetServiceComponent extends WidgetServiceComponent {
  this: ProposalServiceComponent
    with ProposalCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with ProposalSearchEngineComponent
    with PersistentOperationServiceComponent
    with SequenceConfigurationComponent
    with SequenceServiceComponent
    with SelectionAlgorithmComponent =>

  override lazy val widgetService: WidgetService = new DefaultWidgetService

  class DefaultWidgetService extends WidgetService {

    override def startNewWidgetSequence(
      maybeUserId: Option[UserId],
      questionId: QuestionId,
      tagsIds: Option[Seq[TagId]],
      limit: Option[Int],
      requestContext: RequestContext
    ): Future[ProposalsResultSeededResponse] = {

      for {
        selectedProposals <- sequenceService.startNewSequence(
          zone = None,
          keyword = None,
          maybeUserId = maybeUserId,
          questionId = questionId,
          includedProposals = Seq.empty,
          tagsIds = tagsIds,
          requestContext = requestContext
        )
      } yield ProposalsResultSeededResponse(selectedProposals.proposals.size.toLong, selectedProposals.proposals, None)

    }
  }
}
