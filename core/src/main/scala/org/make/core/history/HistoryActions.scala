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
