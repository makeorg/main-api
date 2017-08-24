package org.make.api.technical.businessconfig

final case class BusinessConfig(proposalMinLength: Int,
                                proposalMaxLength: Int,
                                nVotesTriggerConnexion: Int,
                                nPendingProposalsTriggerEmailModerator: Int,
                                minProposalsPerSequence: Int,
                                maxProposalsPerSequence: Int,
                                newVisitorCookieDefinition: String)

object BusinessConfig {
  val defaultProposalMinLength: Int = 7
  val defaultProposalMaxLength: Int = 140
  val defaultNumberVotesTriggerConnexion: Int = 5
  val defaultNumberPendingProposalsTriggerEmailModerator: Int = 50
  val defaultMinProposalsPerSequence: Int = 3
  val defaultMaxProposalsPerSequence: Int = 10
  val defaultNewVisitorCookieDefinition: String = "New user"

  def default(proposalMinLength: Int = defaultProposalMinLength,
              proposalMaxLength: Int = defaultProposalMaxLength,
              nVotesTriggerConnexion: Int = defaultNumberVotesTriggerConnexion,
              nPendingProposalsTriggerEmailModerator: Int = defaultNumberPendingProposalsTriggerEmailModerator,
              minProposalsPerSequence: Int = defaultMinProposalsPerSequence,
              maxProposalsPerSequence: Int = defaultMaxProposalsPerSequence,
              newVisitorCookieDefinition: String = defaultNewVisitorCookieDefinition): BusinessConfig =
    BusinessConfig(
      proposalMinLength = proposalMinLength,
      proposalMaxLength = proposalMaxLength,
      nVotesTriggerConnexion = nVotesTriggerConnexion,
      nPendingProposalsTriggerEmailModerator = nPendingProposalsTriggerEmailModerator,
      minProposalsPerSequence = minProposalsPerSequence,
      maxProposalsPerSequence = maxProposalsPerSequence,
      newVisitorCookieDefinition = newVisitorCookieDefinition
    )
}
