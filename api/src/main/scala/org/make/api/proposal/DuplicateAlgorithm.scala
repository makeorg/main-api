package org.make.api.proposal

import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.semantic.text.document.Corpus
import org.make.semantic.text.feature.wordvec.WordVecOption
import org.make.semantic.text.feature.{FeatureExtractor, WordVecFT}
import org.make.semantic.text.model.duplicate.{DuplicateDetector, SimilarDocResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object DuplicateAlgorithm {
  private val duplicateDetector: DuplicateDetector[Proposal] =
    new DuplicateDetector(lang = "fr", decisionThreshold = 0.0) // Currently do not have notion of multiple languages
  private val featureExtractor: FeatureExtractor = new FeatureExtractor(Seq((WordVecFT, Some(WordVecOption))))

  def getModelName: String = duplicateDetector.getClass.getSimpleName

  /**
    * Get duplicates for a given indexedProposal and a list of potential indexed proposals
    * @param indexedProposal target indexed proposal
    * @param potentialIndexedCandidates list of potential indexed proposals
    * @param getEventSourceProposalById function to get the a Proposal from event sourcing given a proposal id
    * @param maxResults maximum number of duplicates to return
    * @return tuple of a list of similar duplicate indexed proposal and their corresponding similarity scores
    */
  def getDuplicates(indexedProposal: IndexedProposal,
                    potentialIndexedCandidates: Seq[IndexedProposal],
                    getEventSourceProposalById: ProposalId => Future[Option[Proposal]],
                    maxResults: Int): Future[(Seq[IndexedProposal], Seq[Double])] = {

    getEventSourceProposalById(indexedProposal.id).flatMap {
      case Some(targetProposal) =>
        Future.traverse(potentialIndexedCandidates.map(_.id))(getEventSourceProposalById).map {
          maybePotentialCandidates =>
            val potentialCandidates = maybePotentialCandidates.flatten
            val duplicateResults: Seq[SimilarDocResult[Proposal]] = getPredictedDuplicateResults(
              target = targetProposal,
              candidates = potentialCandidates,
              maxResults = 100000 // filtering is done after removing duplicate ideas
            )

            val uniqueDuplicateIdeas: Seq[ProposalId] =
              getUniqueIdeas(duplicateResults.flatMap(_.candidateDoc.document.meta)).take(maxResults)

            (
              uniqueDuplicateIdeas.flatMap(proposalId => potentialIndexedCandidates.find(_.id == proposalId)),
              uniqueDuplicateIdeas
                .flatMap(
                  proposalId =>
                    duplicateResults
                      .find(result => result.candidateDoc.document.meta.getOrElse(ProposalId("NotFound")) == proposalId)
                )
                .map(_.score)
            )
        }
      case _ => Future.failed(new IllegalArgumentException(s"Target proposal not found by id: ${indexedProposal.id}"))
    }
  }

  /**
    *
    * @param target target proposal
    * @param candidates list of potential similars
    * @param maxResults maximum length of result
    * @return list of similar document results
    */
  def getPredictedDuplicateResults(target: Proposal,
                                   candidates: Seq[Proposal],
                                   maxResults: Int): Seq[SimilarDocResult[Proposal]] = {
    val corpus = Corpus(
      rawCorpus = candidates.filter(_.proposalId != target.proposalId).map(_.content),
      extractor = featureExtractor,
      lang = "fr", // Currently do not have notion of multiple languages
      maybeCorpusMeta = Some(candidates.filter(_.proposalId != target.proposalId))
    )

    val predictedResults: Seq[SimilarDocResult[Proposal]] = duplicateDetector
      .getSimilar(target.content, corpus, maxResults)

    predictedResults
  }

  /**
    * Get only proposals that are not similar to each other
    * @param proposals list of proposals
    * @param chosen set of chosen proposal ids
    * @return set of final chosen proposal ids
    */
  def getUniqueIdeas(proposals: Seq[Proposal], chosen: Seq[ProposalId] = Seq.empty): Seq[ProposalId] = {
    if (proposals.isEmpty) {
      chosen.reverse
    } else {
      val nextProposal = proposals.head
      val excludeSet = Set(nextProposal.proposalId) ++ nextProposal.similarProposals.toSet
      getUniqueIdeas(chosen = chosen ++ Set(nextProposal.proposalId), proposals = proposals.filter { p =>
        !excludeSet.contains(p.proposalId)
      })
    }
  }

}
