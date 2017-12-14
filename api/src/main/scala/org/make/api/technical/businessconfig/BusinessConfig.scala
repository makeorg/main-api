package org.make.api.technical.businessconfig

import io.circe.{Decoder, ObjectEncoder}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import org.make.core.reference.{Tag, Theme}

sealed trait BusinessConfig {
  val proposalMinLength: Int
  val proposalMaxLength: Int
  val themes: Seq[Theme]
  //TODO: have a consistent operation model & configuration
  val tagsVFF: Seq[Tag]
}

case class BackofficeConfiguration(override val proposalMinLength: Int,
                                   override val proposalMaxLength: Int,
                                   override val themes: Seq[Theme],
                                   override val tagsVFF: Seq[Tag],
                                   nVotesTriggerConnexion: Int,
                                   nPendingProposalsTriggerEmailModerator: Int,
                                   minProposalsPerSequence: Int,
                                   maxProposalsPerSequence: Int,
                                   reasonsForRefusal: Seq[String])
    extends BusinessConfig

case class FrontConfiguration(override val proposalMinLength: Int,
                              override val proposalMaxLength: Int,
                              override val themes: Seq[Theme],
                              override val tagsVFF: Seq[Tag],
                              newVisitorCookieDefinition: String)
    extends BusinessConfig

object BusinessConfig {
  val defaultProposalMinLength: Int = 12
  val defaultProposalMaxLength: Int = 140
  val themes: Seq[Theme] = Seq.empty
  val tagsVFF: Seq[Tag] = Seq(
    Tag("signalement"),
    Tag("police & justice"),
    Tag("éducation & sensibilisation"),
    Tag("image des femmes"),
    Tag("indépendance financière"),
    Tag("soutien psychologique"),
    Tag("hébergement"),
    Tag("transports"),
    Tag("monde du travail"),
    Tag("monde medical"),
    Tag("agissements sexistes"),
    Tag("violences sexuelles"),
    Tag("harcèlement"),
    Tag("agressions physiques"),
    Tag("violences sexuelles"),
    Tag("violences conjugales"),
    Tag("traditions néfastes & mutilations"),
    Tag("action publique"),
    Tag("prévention"),
    Tag("protection"),
    Tag("réponses"),
    Tag("agression"),
    Tag("monde médical")
  )
}

object FrontConfiguration {
  implicit val encoder: ObjectEncoder[FrontConfiguration] = deriveEncoder[FrontConfiguration]
  implicit val decoder: Decoder[FrontConfiguration] = deriveDecoder[FrontConfiguration]

  val defaultNewVisitorCookieDefinition: String = "New user"

  def default(proposalMinLength: Int = BusinessConfig.defaultProposalMinLength,
              proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
              themes: Seq[Theme] = BusinessConfig.themes,
              tagsVFF: Seq[Tag] = BusinessConfig.tagsVFF,
              newVisitorCookieDefinition: String = defaultNewVisitorCookieDefinition): FrontConfiguration =
    FrontConfiguration(
      proposalMinLength = proposalMinLength,
      proposalMaxLength = proposalMaxLength,
      themes = themes,
      tagsVFF = tagsVFF,
      newVisitorCookieDefinition = newVisitorCookieDefinition
    )
}

object BackofficeConfiguration {
  implicit val encoder: ObjectEncoder[BackofficeConfiguration] = deriveEncoder[BackofficeConfiguration]
  implicit val decoder: Decoder[BackofficeConfiguration] = deriveDecoder[BackofficeConfiguration]

  val defaultNumberVotesTriggerConnexion: Int = 5
  val defaultNumberPendingProposalsTriggerEmailModerator: Int = 50
  val defaultMinProposalsPerSequence: Int = 3
  val defaultMaxProposalsPerSequence: Int = 12
  val defaultProposalVotesThreshold: Int = 100
  val defaultEngagementThreshold: Double = 0.85
  //TODO: redefine it with team product
  val defaultReasonsForRefusal: Seq[String] =
    Seq("Incomprehensible", "Off-topic", "Partisan", "Legal", "Advertising", "MultipleIdeas", "Other")

  def default(proposalMinLength: Int = BusinessConfig.defaultProposalMinLength,
              proposalMaxLength: Int = BusinessConfig.defaultProposalMaxLength,
              themes: Seq[Theme] = BusinessConfig.themes,
              tagsVFF: Seq[Tag] = BusinessConfig.tagsVFF,
              nVotesTriggerConnexion: Int = defaultNumberVotesTriggerConnexion,
              nPendingProposalsTriggerEmailModerator: Int = defaultNumberPendingProposalsTriggerEmailModerator,
              minProposalsPerSequence: Int = defaultMinProposalsPerSequence,
              maxProposalsPerSequence: Int = defaultMaxProposalsPerSequence,
              reasonsForRefusal: Seq[String] = defaultReasonsForRefusal): BackofficeConfiguration =
    BackofficeConfiguration(
      proposalMinLength = proposalMinLength,
      proposalMaxLength = proposalMaxLength,
      themes = themes,
      tagsVFF = tagsVFF,
      reasonsForRefusal = reasonsForRefusal,
      nVotesTriggerConnexion = nVotesTriggerConnexion,
      nPendingProposalsTriggerEmailModerator = nPendingProposalsTriggerEmailModerator,
      minProposalsPerSequence = minProposalsPerSequence,
      maxProposalsPerSequence = maxProposalsPerSequence
    )
}
