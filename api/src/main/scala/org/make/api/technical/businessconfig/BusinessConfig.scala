package org.make.api.technical.businessconfig

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import org.make.core.reference.Theme

sealed trait BusinessConfig {
  val proposalMinLength: Int
  val proposalMaxLength: Int
  val themes: Seq[Theme]
}

case class BackofficeConfiguration(override val proposalMinLength: Int,
                                   override val proposalMaxLength: Int,
                                   override val themes: Seq[Theme],
                                   nVotesTriggerConnexion: Int,
                                   nPendingProposalsTriggerEmailModerator: Int,
                                   minProposalsPerSequence: Int,
                                   maxProposalsPerSequence: Int,
                                   reasonsForRefusal: Seq[String])
    extends BusinessConfig

case class FrontConfiguration(override val proposalMinLength: Int,
                              override val proposalMaxLength: Int,
                              override val themes: Seq[Theme])
    extends BusinessConfig

object BusinessConfig {
  val defaultProposalMinLength: Int = 12
  val defaultProposalMaxLength: Int = 140
  val themes: Seq[Theme] = Seq.empty
}

object FrontConfiguration {
  implicit val encoder: ObjectEncoder[FrontConfiguration] = deriveEncoder[FrontConfiguration]
  implicit val decoder: Decoder[FrontConfiguration] = deriveDecoder[FrontConfiguration]

  def default(proposalMinLength: Int = BusinessConfig.defaultProposalMinLength,
              proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
              themes: Seq[Theme] = BusinessConfig.themes): FrontConfiguration =
    FrontConfiguration(proposalMinLength = proposalMinLength, proposalMaxLength = proposalMaxLength, themes = themes)
}

object BackofficeConfiguration {
  implicit val encoder: ObjectEncoder[BackofficeConfiguration] = deriveEncoder[BackofficeConfiguration]
  implicit val decoder: Decoder[BackofficeConfiguration] = deriveDecoder[BackofficeConfiguration]

  val defaultNumberVotesTriggerConnexion: Int = 5
  val defaultNumberPendingProposalsTriggerEmailModerator: Int = 50
  val defaultMinProposalsPerSequence: Int = 3
  val defaultMaxProposalsPerSequence: Int = 12
  val defaultReasonsForRefusal: Seq[String] =
    Seq("Incomprehensible", "Off-topic", "Partisan", "Legal", "Advertising", "MultipleIdeas", "Other")

  def default(proposalMinLength: Int = BusinessConfig.defaultProposalMinLength,
              proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
              themes: Seq[Theme] = BusinessConfig.themes,
              nVotesTriggerConnexion: Int = defaultNumberVotesTriggerConnexion,
              nPendingProposalsTriggerEmailModerator: Int = defaultNumberPendingProposalsTriggerEmailModerator,
              minProposalsPerSequence: Int = defaultMinProposalsPerSequence,
              maxProposalsPerSequence: Int = defaultMaxProposalsPerSequence,
              reasonsForRefusal: Seq[String] = defaultReasonsForRefusal): BackofficeConfiguration =
    BackofficeConfiguration(
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
