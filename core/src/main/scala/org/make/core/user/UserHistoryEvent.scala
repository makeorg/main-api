package org.make.core.user

import java.time.{LocalDate, ZonedDateTime}

import org.make.core.{MakeSerializable, RequestContext}
import org.make.core.proposal.ProposalEvent.ProposalAccepted
import org.make.core.proposal.SearchQuery

final case class UserAction[T](date: ZonedDateTime, actionType: String, arguments: T)

sealed trait Protagonist

case object Moderator extends Protagonist
case object Citizen extends Protagonist

sealed trait UserHistoryEvent[T] extends MakeSerializable {
  def userId: UserId
  def context: RequestContext
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

final case class LogSearchProposalsEvent(userId: UserId, context: RequestContext, action: UserAction[SearchParameters])
    extends UserHistoryEvent[SearchParameters] {
  override val protagonist: Protagonist = Citizen
}

// User actions
object LogSearchProposalsEvent {
  val action: String = "search"
}

final case class LogAcceptProposalEvent(userId: UserId, context: RequestContext, action: UserAction[ProposalAccepted])
    extends UserHistoryEvent[ProposalAccepted] {
  override val protagonist: Protagonist = Moderator
}

object LogRegisterCitizenEvent {
  val action = "register"
}

final case class LogUserProposalEvent(userId: UserId, context: RequestContext, action: UserAction[UserProposal])
    extends UserHistoryEvent[UserProposal] {
  override val protagonist: Protagonist = Citizen
}

object LogUserProposalEvent {
  val action: String = "propose"
}

// Moderator actions
object LogAcceptProposalEvent {
  val action: String = "accept-proposal"
}

final case class LogRegisterCitizenEvent(userId: UserId, context: RequestContext, action: UserAction[UserRegistered])
    extends UserHistoryEvent[UserRegistered] {
  override val protagonist: Protagonist = Citizen
}

sealed trait UserHistoryAction {
  def userId: UserId
}

final case class GetUserHistory(userId: UserId) extends UserHistoryAction
