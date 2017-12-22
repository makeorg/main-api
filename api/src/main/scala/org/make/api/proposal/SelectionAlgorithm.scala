package org.make.api.proposal

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import org.make.core.proposal._

import scala.annotation.tailrec
import scala.util.Random
import scala.math.ceil
import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.random.{MersenneTwister, RandomGenerator}
import org.make.core.proposal.QualificationKey._
import org.make.core.proposal.VoteKey._

object InverseWeightedRandom extends StrictLogging {
  var random: Random = Random

  def proposalWeight(proposal: Proposal): Double = {
    1 / (proposal.votes.map(_.count).sum + 1).toDouble
  }

  @tailrec
  final def search(proposals: Seq[Proposal], choice: Double, cumsum: Double = 0): Proposal = {
    val cumsumNew = cumsum + proposalWeight(proposals.head)
    if (choice <= cumsumNew) {
      proposals.head
    } else {
      search(proposals.tail, choice, cumsumNew)
    }
  }

  def choose(proposals: Seq[Proposal]): Proposal = {
    val weightSum: Double = proposals.map(proposalWeight).sum
    val choice: Double = random.nextDouble() * weightSum
    search(proposals, choice)
  }

}

object UniformRandom extends StrictLogging {
  var random: Random = Random

  def choose(proposals: Seq[Proposal]): Proposal = {
    proposals(random.nextInt(proposals.length))
  }

}

object ProposalScorer extends StrictLogging {
  var random: RandomGenerator = new MersenneTwister()

  case class ScoreCounts(votes: Int,
                         neutralCount: Int,
                         platitudeAgreeCount: Int,
                         platitudeDisagreeCount: Int,
                         loveCount: Int,
                         hateCount: Int)

  def scoreCounts(proposal: Proposal): ScoreCounts = {
    val votes: Int = proposal.votes.map(_.count).sum
    val neutralCount: Int = proposal.votes.filter(_.key == Neutral).map(_.count).sum
    val platitudeAgreeCount: Int = proposal.votes
      .filter(_.key == Agree)
      .map(_.qualifications.filter(_.key == PlatitudeAgree).map(_.count).sum)
      .sum
    val platitudeDisagreeCount: Int = proposal.votes
      .filter(_.key == Disagree)
      .map(_.qualifications.filter(_.key == PlatitudeDisagree).map(_.count).sum)
      .sum
    val loveCount: Int = proposal.votes
      .filter(_.key == Agree)
      .map(_.qualifications.filter(_.key == LikeIt).map(_.count).sum)
      .sum
    val hateCount: Int = proposal.votes
      .filter(_.key == Disagree)
      .map(_.qualifications.filter(_.key == NoWay).map(_.count).sum)
      .sum

    ScoreCounts(votes, neutralCount, platitudeAgreeCount, platitudeDisagreeCount, loveCount, hateCount)
  }

  /*
   * platitude qualifications counts as neutral votes
   */
  def engagement(counts: ScoreCounts): Double = {
    1 -
      (counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount + 0.33) /
        (counts.votes + 1).toDouble
  }

  def engagement(proposal: Proposal): Double = {
    engagement(scoreCounts(proposal))
  }

  def adhesion(counts: ScoreCounts): Double = {
    ((counts.loveCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble
      - (counts.hateCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble)
  }

  def adhesion(proposal: Proposal): Double = {
    adhesion(scoreCounts(proposal))
  }

  def score(proposal: Proposal): Double = {
    val counts = scoreCounts(proposal)
    engagement(counts) + adhesion(counts)
  }

  /*
   * Samples are taken from a beta distribution, which is the bayesian companion distribution for the binomial distribution
   * Each rate is sampled from its own beta distribution with a prior at 0.33 for votes and 0.01 for qualifications
   */
  def sampleRate(successes: Int, trials: Int, prior: Double): Double = {
    new BetaDistribution(random, successes + prior, trials - successes + 1).sample()
  }

  def sampleEngagement(counts: ScoreCounts): Double = {
    1 - sampleRate(counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount, counts.votes, 0.33)
  }

  def sampleEngagement(proposal: Proposal): Double = {
    sampleEngagement(scoreCounts(proposal))
  }

  def sampleAdhesion(counts: ScoreCounts): Double = {
    (sampleRate(counts.loveCount, counts.votes - counts.neutralCount, 0.01)
      - sampleRate(counts.hateCount, counts.votes - counts.neutralCount, 0.01))
  }

  def sampleAdhesion(proposal: Proposal): Double = {
    sampleAdhesion(scoreCounts(proposal))
  }

  def sampleScore(proposal: Proposal): Double = {
    val counts = scoreCounts(proposal)
    sampleEngagement(counts) + sampleAdhesion(counts)
  }

  /*
   * Returns the rate estimate and standard error
   * it uses Agresti-Coull estimate: "add 2 successes and 2 failures"
   * https://en.wikipedia.org/wiki/Binomial_proportion_confidence_interval#Agresti–Coull_interval
   * nn is the number of trials estimate
   * pp is the probability estimate
   * sd is the standard deviation estimate
   * */
  case class RateEstimate(rate: Double, sd: Double)

  def rateEstimate(successes: Int, trials: Int): RateEstimate = {
    val nn: Double = (trials + 4).toDouble
    val pp: Double = (successes + 2) / nn
    val sd: Double = Math.sqrt(pp * (1 - pp) / nn)

    RateEstimate(pp, sd)
  }

  def engagementUpperBound(proposal: Proposal): Double = {
    val counts = scoreCounts(proposal)

    val engagementEstimate: RateEstimate =
      rateEstimate(counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount, counts.votes)

    1 - engagementEstimate.rate + 2 * engagementEstimate.sd
  }
}

class SelectionAlgorithmConfiguration(config: Config) {
  val newProposalsRatio: Double = config.getDouble("new-proposals-ratio")
  val newProposalsVoteThreshold: Int = config.getInt("new-proposals-vote-threshold")
  val testedProposalsEngagementThreshold: Double = config.getDouble("tested-proposals-engagement-threshold")
  val banditEnabled: Boolean = config.getBoolean("bandit-enabled")
  val banditMinCount: Int = config.getInt("bandit-min-count")
  val banditProposalsRatio: Double = config.getDouble("bandit-proposals-ratio")
}

trait SelectionAlgorithmConfigurationComponent {
  val selectionAlgorithmConfiguration: SelectionAlgorithmConfiguration
}

trait SelectionAlgorithmComponent {
  val selectionAlgorithm: SelectionAlgorithm
}

trait SelectionAlgorithm {
  def newProposalsForSequence(targetLength: Int,
                              proposals: Seq[Proposal],
                              votedProposals: Seq[ProposalId],
                              includeList: Seq[ProposalId]): Seq[ProposalId]
}

trait DefaultSelectionAlgorithmComponent extends SelectionAlgorithmComponent with StrictLogging {

  this: SelectionAlgorithmConfigurationComponent =>

  override val selectionAlgorithm: DefaultSelectionAlgorithm = new DefaultSelectionAlgorithm

  class DefaultSelectionAlgorithm extends SelectionAlgorithm {
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
                                includeList: Seq[ProposalId]): Seq[ProposalId] = {

      // fetch included proposals and exclude similars
      val includedProposals: Seq[Proposal] = proposals.filter(p             => includeList.contains(p.proposalId))
      val proposalsToExclude: Seq[ProposalId] = includedProposals.flatMap(p => p.similarProposals ++ Seq(p.proposalId))

      // fetch available proposals for user
      val availableProposals: Seq[Proposal] = proposals.filter(
        p =>
          p.status == ProposalStatus.Accepted &&
            !proposalsToExclude.contains(p.proposalId) &&
            !votedProposals.contains(p.proposalId) &&
            !p.similarProposals.exists(proposal => includeList.contains(proposal))
      )

      // balance proposals between new and tested
      val proposalsToChoose: Int = targetLength - includeList.size
      val targetNewProposalsCount: Int =
        math.ceil(proposalsToChoose * selectionAlgorithmConfiguration.newProposalsRatio).toInt

      // chooses new proposals
      val newIncludedProposals: Seq[Proposal] =
        chooseNewProposals(availableProposals, targetNewProposalsCount)
      val newProposalsToExclude: Seq[ProposalId] =
        newIncludedProposals.flatMap(p => p.similarProposals ++ Seq(p.proposalId))

      // chooses tested proposals
      val remainingProposals: Seq[Proposal] =
        availableProposals.filter(p => !newProposalsToExclude.contains(p.proposalId))
      val testedProposalCount: Int = proposalsToChoose - newIncludedProposals.size
      val testedIncludedProposals: Seq[Proposal] = chooseTestedProposals(remainingProposals, testedProposalCount)

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

    def chooseNewProposals(availableProposals: Seq[Proposal], targetNewProposalsCount: Int): Seq[Proposal] = {
      val newProposals: Seq[Proposal] = availableProposals.filter { proposal =>
        val votes: Int = proposal.votes.map(_.count).sum
        votes < selectionAlgorithmConfiguration.newProposalsVoteThreshold
      }
      chooseProposals(proposals = newProposals, count = targetNewProposalsCount, algorithm = chooseNewProposalAlgorithm)
    }

    case class ScoredProposal(proposal: Proposal, score: Double)

    /*
     * Chooses the top proposals of each cluster according to the bandit algorithm
     * Keep at least 3 proposals per ideas
     * Then keep the top quartile
     */
    def chooseBanditProposalsSimilars(similarProposals: Seq[Proposal]): Proposal = {

      val similarProposalsScored: Seq[ScoredProposal] =
        similarProposals.map(p => ScoredProposal(p, ProposalScorer.sampleScore(p)))

      val shortList = similarProposals.length match {
        case l if l <= selectionAlgorithmConfiguration.banditMinCount => similarProposals
        case _ =>
          val count = math.max(
            selectionAlgorithmConfiguration.banditMinCount,
            ceil(similarProposals.length * selectionAlgorithmConfiguration.banditProposalsRatio).toInt
          )
          similarProposalsScored.sortWith(_.score > _.score).take(count).map(sp => sp.proposal)
      }

      UniformRandom.choose(shortList)
    }

    def chooseBanditProposals(proposals: Seq[Proposal]): Seq[Proposal] = {
      if (proposals.isEmpty) {
        Seq.empty
      } else {
        val currentProposal: Proposal = proposals.head
        val similarProposals: Seq[Proposal] =
          proposals.filter(p => currentProposal.similarProposals.contains(p.proposalId)) ++ Seq(currentProposal)
        val chosen = chooseBanditProposalsSimilars(similarProposals)
        val similarProposalIds = similarProposals.map(p => p.proposalId)

        Seq(chosen) ++ chooseBanditProposals(
          proposals = proposals.filter(p => !similarProposalIds.contains(p.proposalId))
        )
      }
    }

    def chooseTestedProposals(availableProposals: Seq[Proposal], testedProposalCount: Int): Seq[Proposal] = {
      val testedProposals: Seq[Proposal] = availableProposals.filter { proposal =>
        val votes: Int = proposal.votes.map(_.count).sum
        val engagement_rate: Double = ProposalScorer.engagementUpperBound(proposal)
        (votes >= selectionAlgorithmConfiguration.newProposalsVoteThreshold
        && engagement_rate > selectionAlgorithmConfiguration.testedProposalsEngagementThreshold)
      }

      val proposalPool =
        if (selectionAlgorithmConfiguration.banditEnabled) {
          chooseBanditProposals(testedProposals)
        } else {
          testedProposals
        }

      chooseProposals(proposals = proposalPool, count = testedProposalCount, algorithm = InverseWeightedRandom.choose)
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
}
