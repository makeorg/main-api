package org.make.api.proposal

import com.typesafe.scalalogging.StrictLogging
import org.make.core.proposal.{Proposal, ProposalId, ProposalStatus}

import scala.annotation.tailrec
import scala.util.Random

object InverseWeightedRandom extends StrictLogging {

  /** Returns the index result of a binary search to find @n in the discrete
    * cdf array.
    */
  @tailrec
  def search(n: Int, cdf: Seq[Int], index: Int = 0): Int = {
    if (n <= cdf.head) {
      index
    } else {
      search(n - cdf.head, cdf.tail, index + 1)
    }
  }

  /** Returns the cumulative density function (CDF) of @list (in simple terms,
    * the cumulative sums of the weights).
    */
  def cdf(list: Seq[Int]): Seq[Int] = {
    var s = 0
    list.map { d =>
      s += d
      s
    }
  }

  def randomWeighted(list: Seq[Proposal]): Proposal = {
    val maxCount = list.map(_.votes.map(_.count).sum).max
    val transformedList = list.map(p => maxCount - p.votes.map(_.count).sum)
    list(search(Random.nextInt(transformedList.sum + 1), cdf(transformedList)))
  }

}

object SelectionAlgorithm extends StrictLogging {
  /*
    Returns the list of proposal to display in the sequence
    The proposals are chosen such that:
    - if they are imposed proposals (includeList) they will appear first
    - the rest is 50/50 new proposals to test (less than newProposalVoteCount votes)
      and tested proposals (more than newProposalVoteCount votes)
    - new proposals are tested in a first-in first-out mode until they reach newProposalVoteCount votes
    - if there are not enough tested proposals to provide the requested number of proposals,
      the sequence is completed with new proposals
    - the candidates proposals are filtered such that only one proposal by ideas
      (cluster of similar proposals) can appear in each sequence
    - the non imposed proposals are ordered randomly
   */
  def newProposalsForSequence(targetLength: Int,
                              proposals: Seq[Proposal],
                              votedProposals: Seq[ProposalId],
                              newProposalVoteCount: Int,
                              includeList: Seq[ProposalId]): Seq[ProposalId] = {

    val includedProposals = proposals.filter(p           => includeList.contains(p.proposalId))
    val proposalsToExclude = includedProposals.flatMap(p => p.similarProposals ++ Seq(p.proposalId))

    val availableProposals =
      proposals.filter(
        p =>
          !proposalsToExclude.contains(p.proposalId) &&
            !votedProposals.contains(p.proposalId) &&
            p.status == ProposalStatus.Accepted &&
            !p.similarProposals.exists(proposal => includeList.contains(proposal))
      )

    val proposalsToChoose = targetLength - includeList.size
    val targetNewProposalsCount = proposalsToChoose / 2

    val newProposals = availableProposals.filter { proposal =>
      val votes = proposal.votes.map(_.count).sum
      votes < newProposalVoteCount
    }

    val newIncludedProposals = chooseNewProposals(newProposals, targetNewProposalsCount)
    val newProposalsSimilars = newIncludedProposals.flatMap(_.similarProposals) ++ newProposals.map(_.proposalId)

    val testedProposals = availableProposals.filter { proposal =>
      val votes = proposal.votes.map(_.count).sum
      votes >= newProposalVoteCount && !newProposalsSimilars.contains(proposal.proposalId)
    }
    val testedProposalCount = proposalsToChoose - newIncludedProposals.size
    val testedIncludedProposals: Seq[Proposal] = chooseTestedProposals(testedProposals, testedProposalCount)

    val sequence = includeList ++ Random.shuffle(
      newIncludedProposals.map(_.proposalId) ++ testedIncludedProposals.map(_.proposalId)
    )
    if (sequence.size < targetLength) {
      val excludeList =
        (proposals.filter(p => sequence.contains(p.proposalId)).flatMap(_.similarProposals) ++ sequence).toSet

      sequence ++ chooseNewProposals(
        availableProposals.filter(p => !excludeList.contains(p.proposalId)),
        targetLength - sequence.size
      ).map(_.proposalId)
    } else {
      sequence
    }
  }

  def chooseProposals(proposals: Seq[Proposal], count: Int, algorithm: (Seq[Proposal]) => Proposal): Seq[Proposal] = {
    if (proposals.isEmpty || count <= 0) {
      Seq.empty
    } else {
      val chosen = algorithm(proposals)
      Seq(chosen) ++ chooseProposals(
        count = count - 1,
        proposals =
          proposals.filter(p => p.proposalId != chosen.proposalId && !chosen.similarProposals.contains(p.proposalId)),
        algorithm = algorithm
      )
    }
  }

  def chooseTestedProposals(proposals: Seq[Proposal], count: Int): Seq[Proposal] = {
    chooseProposals(proposals = proposals, count = count, algorithm = InverseWeightedRandom.randomWeighted)
  }

  def chooseNewProposals(proposals: Seq[Proposal], count: Int): Seq[Proposal] = {
    chooseProposals(
      proposals = proposals,
      count = count,
      algorithm = { proposals =>
        proposals
          .sortWith((first, second) => {
            (for {
              firstCreation  <- first.updatedAt
              secondCreation <- second.updatedAt
            } yield {
              firstCreation.isBefore(secondCreation)
            }).getOrElse(false)
          })
          .head
      }
    )
  }
}
