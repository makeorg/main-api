package org.make.core.user

import java.time.{LocalDate, ZonedDateTime}

import org.make.core.proposal.ProposalEvent.{ProposalAccepted, ProposalRefused}
import org.make.core.proposal.SearchQuery
import org.make.core.proposal.indexed.VoteKey
import org.make.core.{MakeSerializable, RequestContext}

final case class UserAction[T](date: ZonedDateTime, actionType: String, arguments: T)

sealed trait Protagonist

case object Moderator extends Protagonist
case object Citizen extends Protagonist

sealed trait UserHistoryEvent[T] extends MakeSerializable {
  def userId: UserId
  def requestContext: RequestContext
  def action: UserAction[T]
  def protagonist: Protagonist
}

final case class SearchParameters(query: SearchQuery)
final case class UserRegistered(email: String,
                                dateOfBirth: Option[LocalDate],
                                firstName: Option[String],
                                lastName: Option[String],
                                profession: Option[String],
                                postalCode: Option[String])

final case class UserProposal(content: String)

final case class UserVote(voteKey: VoteKey)

final case class LogSearchProposalsEvent(userId: UserId,
                                         requestContext: RequestContext,
                                         action: UserAction[SearchParameters])
    extends UserHistoryEvent[SearchParameters] {
  override val protagonist: Protagonist = Citizen
}

// User actions
object LogSearchProposalsEvent {
  val action: String = "search"
}

final case class LogAcceptProposalEvent(userId: UserId,
                                        requestContext: RequestContext,
                                        action: UserAction[ProposalAccepted])
    extends UserHistoryEvent[ProposalAccepted] {
  override val protagonist: Protagonist = Moderator
}

final case class LogRefuseProposalEvent(userId: UserId,
                                        requestContext: RequestContext,
                                        action: UserAction[ProposalRefused])
    extends UserHistoryEvent[ProposalRefused] {
  override val protagonist: Protagonist = Moderator
}

object LogRegisterCitizenEvent {
  val action = "register"
}

final case class LogUserProposalEvent(userId: UserId, requestContext: RequestContext, action: UserAction[UserProposal])
    extends UserHistoryEvent[UserProposal] {
  override val protagonist: Protagonist = Citizen
}

object LogUserProposalEvent {
  val action: String = "propose"
}

final case class LogUserVoteEvent(userId: UserId, requestContext: RequestContext, action: UserAction[UserVote])
    extends UserHistoryEvent[UserVote] {
  override val protagonist: Protagonist = Citizen
}

object LogUserVoteEvent {
  val action: String = "vote"
}

final case class LogUserUnvoteEvent(userId: UserId, requestContext: RequestContext, action: UserAction[UserVote])
    extends UserHistoryEvent[UserVote] {
  override val protagonist: Protagonist = Citizen
}

object LogUserUnvoteEvent {
  val action: String = "unvote"
}

// Moderator actions
object LogAcceptProposalEvent {
  val action: String = "accept-proposal"
}
object LogRefuseProposalEvent {
  val action: String = "refuse-proposal"
}

final case class LogRegisterCitizenEvent(userId: UserId,
                                         requestContext: RequestContext,
                                         action: UserAction[UserRegistered])
    extends UserHistoryEvent[UserRegistered] {
  override val protagonist: Protagonist = Citizen
}

sealed trait UserHistoryAction {
  def userId: UserId
}

final case class GetUserHistory(userId: UserId) extends UserHistoryAction
