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

import enumeratum.values.{StringEnum, StringEnumEntry}
import org.make.core.SprayJsonFormatters._
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

object HistoryActions {

  sealed abstract class VoteTrust(val value: String) extends StringEnumEntry with Product with Serializable {
    def isTrusted: Boolean
    def isInSequence: Boolean
    def isInSegment: Boolean
  }

  object VoteTrust extends StringEnum[VoteTrust] {

    case object Trusted extends VoteTrust("trusted") {
      override val isTrusted: Boolean = true
      override val isInSequence: Boolean = false
      override val isInSegment: Boolean = false
    }

    case object Troll extends VoteTrust("troll") {
      override val isTrusted: Boolean = false
      override val isInSequence: Boolean = false
      override val isInSegment: Boolean = false
    }

    case object Sequence extends VoteTrust("sequence") {
      override val isTrusted: Boolean = true
      override val isInSequence: Boolean = true
      override val isInSegment: Boolean = false
    }

    case object Segment extends VoteTrust("segment") {
      override val isTrusted: Boolean = true
      override val isInSequence: Boolean = true
      override val isInSegment: Boolean = true
    }

    override def values: IndexedSeq[VoteTrust] = findValues

  }

  final case class VoteAndQualifications(
    voteKey: VoteKey,
    qualificationKeys: Map[QualificationKey, VoteTrust],
    date: ZonedDateTime,
    trust: VoteTrust
  )

  object VoteAndQualifications {
    implicit val formatter: RootJsonFormat[VoteAndQualifications] =
      DefaultJsonProtocol.jsonFormat4(VoteAndQualifications.apply)
  }

  sealed trait VoteRelatedAction extends Product with Serializable {
    def proposalId: ProposalId
    def date: ZonedDateTime
    def trust: VoteTrust
  }

  sealed trait GenericVoteAction extends VoteRelatedAction {
    def key: VoteKey
  }

  sealed trait GenericQualificationAction extends VoteRelatedAction {
    def key: QualificationKey
  }

  final case class VoteAction(proposalId: ProposalId, date: ZonedDateTime, key: VoteKey, trust: VoteTrust)
      extends GenericVoteAction
  final case class UnvoteAction(proposalId: ProposalId, date: ZonedDateTime, key: VoteKey, trust: VoteTrust)
      extends GenericVoteAction
  final case class QualificationAction(
    proposalId: ProposalId,
    date: ZonedDateTime,
    key: QualificationKey,
    trust: VoteTrust
  ) extends GenericQualificationAction
  final case class UnqualificationAction(
    proposalId: ProposalId,
    date: ZonedDateTime,
    key: QualificationKey,
    trust: VoteTrust
  ) extends GenericQualificationAction

}
