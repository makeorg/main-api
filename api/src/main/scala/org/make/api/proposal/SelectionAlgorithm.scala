package org.make.api.proposal

import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed._

import scala.util.Random

object InverseWeightedRandom {

  /** Returns the index result of a binary search to find @n in the discrete
    * cdf array.
    */
  def search(n: Int, cdf: Seq[Int]): Int = {
    if (n > cdf.head) {
      1 + search(n - cdf.head, cdf.tail)
    } else {
      0
    }
  }

  /** Returns the cumulative density function (CDF) of @list (in simple terms,
    * the cumulative sums of the weights).
    */
  def cdf(list: Seq[Int]): Seq[Int] = list.map {
    var s = 0
    d =>
      {
        s += d
        s
      }
  }

  def randomWeighted(list: Seq[IndexedProposal]): IndexedProposal = {
    val maxCount = list.map(_.votes.map(_.count).sum).max
    val transformedList = list.map(p => maxCount - p.votes.map(_.count).sum)
    list(search(Random.nextInt(transformedList.sum + 1), cdf(transformedList)))
  }

}

object SelectionAlgorithm {

  /**
    * @param lengthSequence        number of elements in the sequence
    * @param getSearchSpace        function that returns a list of randomly chosen proposals
    * @param getSimilarForProposal function that returns a list of similar proposal ids for a given proposal id
    * @return selected list of proposals for the sequence
    */
  def getProposalsForSequence(lengthSequence: Int,
                              getSearchSpace: Seq[ProposalId]   => Seq[IndexedProposal],
                              getSimilarForProposal: ProposalId => Seq[ProposalId],
                              includeList: Seq[IndexedProposal] = Seq.empty): Seq[IndexedProposal] = {
    // initialize exclude list with the forced included and their similar
    var excludeList: Set[ProposalId] = (includeList.map(_.id) ++ includeList.flatMap { proposal =>
      getSimilarForProposal(proposal.id)
    }).toSet
    // initialize search space
    var searchSpace: Seq[IndexedProposal] = getSearchSpace(excludeList.toSeq)

    if (searchSpace.nonEmpty) {
      var selectedProposals: Seq[IndexedProposal] = Seq.empty
      var continue: Boolean = true
      var prunedSearchSpace = searchSpace

      while (selectedProposals.length < (lengthSequence - includeList.length) && continue) {
        // picking a proposal at (inverse weighted) random from {searchSpace - excludeList}
        val nextEntry = InverseWeightedRandom.randomWeighted(prunedSearchSpace)
        // adding next entry to list of selected proposals
        selectedProposals = nextEntry +: selectedProposals
        // adding next entry and it's similar to the exclude list
        excludeList = excludeList + nextEntry.id ++ getSimilarForProposal(nextEntry.id).toSet

        // prune search space
        prunedSearchSpace = searchSpace.filter { e =>
          !excludeList.contains(e.id)
        }
        // pruned search space exhausted
        if (prunedSearchSpace.isEmpty) {
          // get new search space
          searchSpace = getSearchSpace(excludeList.toSeq)
          if (searchSpace.isEmpty) {
            // search space exhausted, exiting
            continue = false
          }
        }
      }
      includeList ++ selectedProposals.reverse
    } else {
      includeList
    }
  }
}
