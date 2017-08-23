package org.make.api.technical.businessconfig

final case class BusinessConfig(proposalMinLength: Int,
                                proposalMaxLength: Int,
                                nVotesTriggerConnexion: Int,
                                nPendingProposalsTriggerEmailModerator: Int,
                                minProposalsPerSequence: Int,
                                maxProposalsPerSequence: Int,
                                newVisitorCookieDefinition: String)

object BusinessConfig {
  def default(proposalMinLength: Int = 7,
              proposalMaxLength: Int = 140,
              nVotesTriggerConnexion: Int = 5,
              nPendingProposalsTriggerEmailModerator: Int = 50,
              minProposalsPerSequence: Int = 3,
              maxProposalsPerSequence: Int = 10,
              newVisitorCookieDefinition: String = "New user"): BusinessConfig =
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
