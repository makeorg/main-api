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

import org.make.core.SprayJsonFormatters._
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, RootJsonFormat}

object HistoryActions {

  sealed trait VoteTrust {
    def shortName: String
    def isTrusted: Boolean
  }
  object VoteTrust {
    val trustValue: Map[String, VoteTrust] = Map(Trusted.shortName -> Trusted, Troll.shortName -> Troll)

    implicit val formatter: RootJsonFormat[VoteTrust] = new RootJsonFormat[VoteTrust] {
      override def write(obj: VoteTrust): JsValue = JsString(obj.shortName)
      override def read(json: JsValue): VoteTrust = {
        json match {
          case JsString(value) => trustValue(value)
          case other           => throw new IllegalArgumentException(s"Unable to convert $other")
        }
      }
    }
  }

  case object Trusted extends VoteTrust {
    override val shortName: String = "trusted"
    override val isTrusted: Boolean = true
  }

  case object Troll extends VoteTrust {
    override val shortName: String = "troll"
    override val isTrusted: Boolean = false
  }

  final case class VoteAndQualifications(voteKey: VoteKey,
                                         qualificationKeys: Map[QualificationKey, VoteTrust],
                                         date: ZonedDateTime,
                                         trust: VoteTrust)

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
  final case class QualificationAction(proposalId: ProposalId,
                                       date: ZonedDateTime,
                                       key: QualificationKey,
                                       trust: VoteTrust)
      extends GenericQualificationAction
  final case class UnqualificationAction(proposalId: ProposalId,
                                         date: ZonedDateTime,
                                         key: QualificationKey,
                                         trust: VoteTrust)
      extends GenericQualificationAction

}
