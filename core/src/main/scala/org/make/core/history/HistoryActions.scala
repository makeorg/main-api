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

package org.make.core.history

import java.time.ZonedDateTime

import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import spray.json.DefaultJsonProtocol._

object HistoryActions {

  final case class VoteAndQualifications(voteKey: VoteKey, qualificationKeys: Seq[QualificationKey])

  object VoteAndQualifications {
    implicit val formatter: RootJsonFormat[VoteAndQualifications] =
      DefaultJsonProtocol.jsonFormat2(VoteAndQualifications.apply)
  }

  sealed trait VoteRelatedAction extends Product with Serializable {
    def proposalId: ProposalId
    def date: ZonedDateTime
  }

  sealed trait GenericVoteAction extends VoteRelatedAction {
    def key: VoteKey
  }

  sealed trait GenericQualificationAction extends VoteRelatedAction {
    def key: QualificationKey
  }

  final case class VoteAction(proposalId: ProposalId, date: ZonedDateTime, key: VoteKey) extends GenericVoteAction
  final case class UnvoteAction(proposalId: ProposalId, date: ZonedDateTime, key: VoteKey) extends GenericVoteAction
  final case class QualificationAction(proposalId: ProposalId, date: ZonedDateTime, key: QualificationKey)
      extends GenericQualificationAction
  final case class UnqualificationAction(proposalId: ProposalId, date: ZonedDateTime, key: QualificationKey)
      extends GenericQualificationAction

}
