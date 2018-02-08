package org.make.api.proposal

import io.circe.Encoder
import org.make.core.proposal.ProposalId

case class PredictDuplicate(proposalId: ProposalId,
                            predictedDuplicates: Seq[ProposalId],
                            predictedScores: Seq[Double],
                            algoLabel: String)

object PredictDuplicate {
  implicit val encoder: Encoder[PredictDuplicate] =
    Encoder.forProduct4("ProposalId", "PredictedDuplicates", "PredictedScores", "AlgoLabel") { predictDuplicate =>
      (
        predictDuplicate.proposalId,
        predictDuplicate.predictedDuplicates,
        predictDuplicate.predictedScores,
        predictDuplicate.algoLabel
      )
    }
}
