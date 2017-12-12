package org.make.api.proposal

import com.typesafe.scalalogging.StrictLogging
import org.make.core.proposal.{Proposal, ProposalId, ProposalStatus, VoteKey}

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
    - tested proposals are filtered out if their engagement rate is too low
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
                              testedProposalEngagementThreshold: Double,
                              includeList: Seq[ProposalId]): Seq[ProposalId] = {

    // fetch included proposals and exclude similars
    val includedProposals: Seq[Proposal] = proposals.filter(p             => includeList.contains(p.proposalId))
    val proposalsToExclude: Seq[ProposalId] = includedProposals.flatMap(p => p.similarProposals ++ Seq(p.proposalId))

    // fetch available proposals for user
    val availableProposals: Seq[Proposal] =
      proposals.filter(
        p =>
          !proposalsToExclude.contains(p.proposalId) &&
            !votedProposals.contains(p.proposalId) &&
            p.status == ProposalStatus.Accepted &&
            !p.similarProposals.exists(proposal => includeList.contains(proposal))
      )

    // balance proposals between new and tested
    val proposalsToChoose: Int = targetLength - includeList.size
    val targetNewProposalsCount: Int = proposalsToChoose / 2

    // chooses new proposals
    val newIncludedProposals: Seq[Proposal] =
      chooseNewProposals(availableProposals, newProposalVoteCount, targetNewProposalsCount)
    val newProposalsSimilars: Seq[ProposalId] = newIncludedProposals.flatMap(_.similarProposals)

    // chooses tested proposals
    val testedProposalCount: Int = proposalsToChoose - newIncludedProposals.size
    val testedIncludedProposals: Seq[Proposal] = chooseTestedProposals(
      availableProposals,
      newProposalsSimilars,
      newProposalVoteCount,
      testedProposalEngagementThreshold,
      testedProposalCount
    )

    // build sequence
    val sequence: Seq[ProposalId] = includeList ++ Random.shuffle(
      newIncludedProposals.map(_.proposalId) ++ testedIncludedProposals.map(_.proposalId)
    )
    if (sequence.size < targetLength) {
      complementSequence(sequence, targetLength, proposals, availableProposals)
    } else {
      sequence
    }
  }

  /*
   * Returns the upper bound estimate for the engagement rate
   * it uses Agresti-Coull estimate: "add 2 successes and 2 failures"
   * https://en.wikipedia.org/wiki/Binomial_proportion_confidence_interval#Agresti–Coull_interval
   * nn is the number of trials estimate
   * pp is the probability estimate
   * sd is the standard deviation estimate
   * */
  def computeEngagementRateEstimate(proposal: Proposal): Double = {
    val votes: Int = proposal.votes.map(_.count).sum
    val forAgainst: Int =
      proposal.votes.filter(v => v.key == VoteKey.Agree || v.key == VoteKey.Disagree).map(_.count).sum
    val nn: Double = (votes + 4).toDouble
    val pp: Double = (forAgainst + 2) / nn
    val sd: Double = Math.sqrt(pp * (1 - pp) / nn)
    pp + 2 * sd
  }

  def chooseProposals(proposals: Seq[Proposal], count: Int, algorithm: (Seq[Proposal]) => Proposal): Seq[Proposal] = {
    if (proposals.isEmpty || count <= 0) {
      Seq.empty
    } else {
      val chosen: Proposal = algorithm(proposals)
      Seq(chosen) ++ chooseProposals(
        count = count - 1,
        proposals =
          proposals.filter(p => p.proposalId != chosen.proposalId && !chosen.similarProposals.contains(p.proposalId)),
        algorithm = algorithm
      )
    }
  }

  def chooseNewProposalAlgorithm(proposals: Seq[Proposal]): Proposal = {
    proposals
      .sortWith((first, second) => {
        (for {
          firstCreation  <- first.createdAt
          secondCreation <- second.createdAt
        } yield {
          firstCreation.isBefore(secondCreation)
        }).getOrElse(false)
      })
      .head
  }

  def chooseNewProposals(availableProposals: Seq[Proposal],
                         newProposalVoteCount: Int,
                         targetNewProposalsCount: Int): Seq[Proposal] = {
    val newProposals: Seq[Proposal] = availableProposals.filter { proposal =>
      val votes: Int = proposal.votes.map(_.count).sum
      votes < newProposalVoteCount
    }
    chooseProposals(proposals = newProposals, count = targetNewProposalsCount, algorithm = chooseNewProposalAlgorithm)
  }

  def chooseTestedProposals(availableProposals: Seq[Proposal],
                            newProposalsSimilars: Seq[ProposalId],
                            newProposalVoteCount: Int,
                            testedProposalEngagementThreshold: Double,
                            testedProposalCount: Int): Seq[Proposal] = {
    val testedProposals: Seq[Proposal] = availableProposals.filter { proposal =>
      val votes: Int = proposal.votes.map(_.count).sum
      val engagement_rate: Double = computeEngagementRateEstimate(proposal)
      (votes >= newProposalVoteCount
      && !newProposalsSimilars.contains(proposal.proposalId)
      && engagement_rate > testedProposalEngagementThreshold)
    }
    chooseProposals(
      proposals = testedProposals,
      count = testedProposalCount,
      algorithm = InverseWeightedRandom.randomWeighted
    )
  }

  def complementSequence(sequence: Seq[ProposalId],
                         targetLength: Int,
                         proposals: Seq[Proposal],
                         availableProposals: Seq[Proposal]): Seq[ProposalId] = {
    val excludeList: Set[ProposalId] =
      (proposals.filter(p => sequence.contains(p.proposalId)).flatMap(_.similarProposals) ++ sequence).toSet

    sequence ++ chooseProposals(
      availableProposals.filter(p => !excludeList.contains(p.proposalId)),
      targetLength - sequence.size,
      chooseNewProposalAlgorithm
    ).map(_.proposalId)
  }
}
