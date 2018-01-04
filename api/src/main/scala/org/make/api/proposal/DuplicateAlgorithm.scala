package org.make.api.proposal

import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.semantic.text.document.Corpus
import org.make.semantic.text.feature.wordvec.WordVecOption
import org.make.semantic.text.feature.{FeatureExtractor, WordVecFT}
import org.make.semantic.text.model.duplicate.{DuplicateDetector, SimilarDocResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class DuplicateResult(proposal: Proposal, score: Double)

object DuplicateAlgorithm {
  val duplicateDetector: DuplicateDetector[Proposal] =
    new DuplicateDetector(lang = "fr", decisionThreshold = 0.0) // Currently do not have notion of multiple languages
  val featureExtractor: FeatureExtractor = new FeatureExtractor(Seq((WordVecFT, Some(WordVecOption))))

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
                    maxResults: Int): Future[Seq[DuplicateResult]] = {

    getEventSourceProposalById(indexedProposal.id).flatMap {
      case Some(targetProposal) =>
        Future.traverse(potentialIndexedCandidates.map(_.id))(getEventSourceProposalById).map {
          maybePotentialCandidates =>
            val potentialCandidates = maybePotentialCandidates.flatten
            val duplicateResults: Seq[DuplicateResult] = getPredictedDuplicateResults(
              target = targetProposal,
              candidates = potentialCandidates,
              maxResults = 100000 // filtering is done after removing duplicate ideas
            ).map(similarDoc => DuplicateResult(similarDoc.candidateDoc.document.meta.get, similarDoc.score))

            getUniqueDuplicateIdeas(duplicateResults).take(maxResults)
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
    * @param duplicates list of proposals
    * @param chosen set of chosen proposal ids
    * @return set of final chosen proposal ids
    */
  def getUniqueDuplicateIdeas(duplicates: Seq[DuplicateResult],
                              chosen: Seq[DuplicateResult] = Seq.empty): Seq[DuplicateResult] = {
    if (duplicates.isEmpty) {
      chosen.reverse
    } else {
      val nextDuplicate: DuplicateResult = duplicates.head
      nextDuplicate.proposal.idea match {
        case Some(_) =>
          getUniqueDuplicateIdeas(chosen = chosen ++ Set(nextDuplicate), duplicates = duplicates.filter { p =>
            p.proposal.idea != nextDuplicate.proposal.idea
          })
        case None =>
          getUniqueDuplicateIdeas(chosen = chosen, duplicates = duplicates.filter { p =>
            p != nextDuplicate
          })
      }
    }
  }

}
