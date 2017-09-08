package org.make.api.technical.businessconfig

import org.make.core.reference.Theme

sealed trait BusinessConfig {
  val proposalMinLength: Int
  val proposalMaxLength: Int
  val themes: Seq[Theme]
}

case class BusinessConfigBack(override val proposalMinLength: Int,
                              override val proposalMaxLength: Int,
                              override val themes: Seq[Theme],
                              nVotesTriggerConnexion: Int,
                              nPendingProposalsTriggerEmailModerator: Int,
                              minProposalsPerSequence: Int,
                              maxProposalsPerSequence: Int,
                              reasonsForRefusal: Seq[String])
    extends BusinessConfig

case class BusinessConfigFront(override val proposalMinLength: Int,
                               override val proposalMaxLength: Int,
                               override val themes: Seq[Theme],
                               newVisitorCookieDefinition: String)
    extends BusinessConfig

object BusinessConfig {
  val defaultProposalMinLength: Int = 7
  val defaultProposalMaxLength: Int = 140
  val themes: Seq[Theme] = Seq.empty
}

object BusinessConfigFront {
  val defaultNewVisitorCookieDefinition: String = "New user"

  def default(proposalMinLength: Int = BusinessConfig.defaultProposalMinLength,
              proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
              themes: Seq[Theme] = BusinessConfig.themes,
              newVisitorCookieDefinition: String = defaultNewVisitorCookieDefinition): BusinessConfigFront =
    BusinessConfigFront(
      proposalMinLength = proposalMinLength,
      proposalMaxLength = proposalMaxLength,
      themes = themes,
      newVisitorCookieDefinition = newVisitorCookieDefinition
    )
}

object BusinessConfigBack {
  val defaultNumberVotesTriggerConnexion: Int = 5
  val defaultNumberPendingProposalsTriggerEmailModerator: Int = 50
  val defaultMinProposalsPerSequence: Int = 3
  val defaultMaxProposalsPerSequence: Int = 10
  //TODO: redefine it with team product
  val defaultReasonsForRefusal: Seq[String] =
    Seq("Inapropriate", "Shocking", "Duplicate", "Unactionnable", "Flat")

  def default(proposalMinLength: Int = BusinessConfig.defaultProposalMinLength,
              proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
              themes: Seq[Theme] = BusinessConfig.themes,
              nVotesTriggerConnexion: Int = defaultNumberVotesTriggerConnexion,
              nPendingProposalsTriggerEmailModerator: Int = defaultNumberPendingProposalsTriggerEmailModerator,
              minProposalsPerSequence: Int = defaultMinProposalsPerSequence,
              maxProposalsPerSequence: Int = defaultMaxProposalsPerSequence,
              reasonsForRefusal: Seq[String] = defaultReasonsForRefusal): BusinessConfigBack =
    BusinessConfigBack(
      proposalMinLength = proposalMinLength,
      proposalMaxLength = proposalMaxLength,
      themes = themes,
      reasonsForRefusal = reasonsForRefusal,
      nVotesTriggerConnexion = nVotesTriggerConnexion,
      nPendingProposalsTriggerEmailModerator = nPendingProposalsTriggerEmailModerator,
      minProposalsPerSequence = minProposalsPerSequence,
      maxProposalsPerSequence = maxProposalsPerSequence
    )
}
