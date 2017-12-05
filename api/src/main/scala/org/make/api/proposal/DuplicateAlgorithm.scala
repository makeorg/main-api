package org.make.api.proposal

import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.proposal.indexed.IndexedProposal
import org.make.semantic.text.document.Corpus
import org.make.semantic.text.feature.wordvec.WordVecOption
import org.make.semantic.text.feature.{FeatureExtractor, WordVecFT}
import org.make.semantic.text.model.duplicate.{DuplicateDetector, SimilarDocResult}

object DuplicateAlgorithm {
  private val duplicateDetector: DuplicateDetector[IndexedProposal] =
    new DuplicateDetector(lang = "fr", 0.0)
  private val featureExtractor: FeatureExtractor = new FeatureExtractor(Seq((WordVecFT, Some(WordVecOption))))

  def getModelName: String = duplicateDetector.getClass.getSimpleName

  def getUniqueIdeas(proposals: Seq[Proposal], chosen: Set[ProposalId] = Set.empty): Set[ProposalId] = {
    if (proposals.isEmpty) {
      chosen
    } else {
      val nextProposal = proposals.head
      val excludeSet = Set(nextProposal.proposalId) ++ nextProposal.similarProposals.toSet
      getUniqueIdeas(chosen = chosen ++ Set(nextProposal.proposalId), proposals = proposals.filter { p =>
        !excludeSet.contains(p.proposalId)
      })
    }
  }

  def getPredictedDuplicateResults(indexedProposal: IndexedProposal,
                                   candidates: Seq[IndexedProposal],
                                   maxResults: Int): (Seq[SimilarDocResult[IndexedProposal]], Seq[IndexedProposal]) = {
    val corpus = Corpus(
      rawCorpus = candidates.filter(_.id != indexedProposal.id).map(_.content),
      extractor = featureExtractor,
      lang = indexedProposal.language,
      maybeCorpusMeta = Some(candidates.filter(_.id != indexedProposal.id))
    )

    val predictedResults: Seq[SimilarDocResult[IndexedProposal]] = duplicateDetector
      .getSimilar(indexedProposal.content, corpus, maxResults)

    val predictedDuplicates: Seq[IndexedProposal] =
      predictedResults.flatMap(_.candidateDoc.document.meta)

    (predictedResults, predictedDuplicates)
  }

}
