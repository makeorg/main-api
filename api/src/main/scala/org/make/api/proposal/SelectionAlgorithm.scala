package org.make.api.proposal

import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.math3.distribution.BetaDistribution
import org.apache.commons.math3.random.{MersenneTwister, RandomGenerator}
import org.make.api.sequence.SequenceConfiguration
import org.make.core.idea.IdeaId
import org.make.core.proposal.QualificationKey._
import org.make.core.proposal.VoteKey._
import org.make.core.proposal._

import scala.annotation.tailrec
import scala.math.ceil
import scala.util.Random

trait ProposalChooser {
  def choose(proposals: Seq[Proposal]): Proposal
}

object OldestProposalChooser extends ProposalChooser {
  override def choose(proposals: Seq[Proposal]): Proposal = {
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
}

trait RandomProposalChooser extends ProposalChooser {
  var random: Random = Random

  protected def proposalWeight(proposal: Proposal): Double

  @tailrec
  private final def search(proposals: Seq[Proposal], choice: Double, accumulatedSum: Double = 0): Proposal = {
    val accumulatedSumNew = accumulatedSum + proposalWeight(proposals.head)
    if (choice <= accumulatedSumNew) {
      proposals.head
    } else {
      search(proposals.tail, choice, accumulatedSumNew)
    }
  }

  final def choose(proposals: Seq[Proposal]): Proposal = {
    val weightSum: Double = proposals.map(proposalWeight).sum
    val choice: Double = random.nextDouble() * weightSum
    search(proposals, choice)
  }
}

object InverseWeightedRandom extends RandomProposalChooser {
  override def proposalWeight(proposal: Proposal): Double = {
    1 / (proposal.votes.map(_.count).sum + 1).toDouble
  }
}

object SoftMinRandom extends RandomProposalChooser {
  override def proposalWeight(proposal: Proposal): Double = {
    Math.exp(-1 * proposal.votes.map(_.count).sum)
  }
}

object UniformRandom extends ProposalChooser with StrictLogging {
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
                         hateCount: Int,
                         doableCount: Int,
                         impossibleCount: Int)

  def voteCounts(proposal: Proposal, voteKey: VoteKey): Int = {
    proposal.votes.filter(_.key == voteKey).map(_.count).sum
  }

  def qualificationCounts(proposal: Proposal, voteKey: VoteKey, qualificationKey: QualificationKey): Int = {
    proposal.votes
      .filter(_.key == voteKey)
      .map(_.qualifications.filter(_.key == qualificationKey).map(_.count).sum)
      .sum
  }

  def scoreCounts(proposal: Proposal): ScoreCounts = {
    val votes: Int = proposal.votes.map(_.count).sum
    val neutralCount: Int = voteCounts(proposal, Neutral)
    val platitudeAgreeCount: Int = qualificationCounts(proposal, Agree, PlatitudeAgree)
    val platitudeDisagreeCount: Int = qualificationCounts(proposal, Disagree, PlatitudeDisagree)
    val loveCount: Int = qualificationCounts(proposal, Agree, LikeIt)
    val hateCount: Int = qualificationCounts(proposal, Disagree, NoWay)
    val doableCount: Int = qualificationCounts(proposal, Agree, Doable)
    val impossibleCount: Int = qualificationCounts(proposal, Agree, Impossible)

    ScoreCounts(
      votes,
      neutralCount,
      platitudeAgreeCount,
      platitudeDisagreeCount,
      loveCount,
      hateCount,
      doableCount,
      impossibleCount
    )
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

  def realistic(counts: ScoreCounts): Double = {
    ((counts.doableCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble
      - (counts.impossibleCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble)
  }

  def realistic(proposal: Proposal): Double = {
    realistic(scoreCounts(proposal))
  }

  def score(counts: ScoreCounts): Double = {
    engagement(counts) + adhesion(counts) + 2 * realistic(counts)
  }

  def score(proposal: Proposal): Double = {
    score(scoreCounts(proposal))
  }

  def controversy(counts: ScoreCounts): Double = {
    math.min(counts.loveCount + 0.01, counts.hateCount + 0.01) / (counts.votes - counts.neutralCount + 1).toDouble
  }

  def controversy(proposal: Proposal): Double = {
    controversy(scoreCounts(proposal))
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

  def sampleRealistic(counts: ScoreCounts): Double = {
    (sampleRate(counts.doableCount, counts.votes - counts.neutralCount, 0.01)
      - sampleRate(counts.impossibleCount, counts.votes - counts.neutralCount, 0.01))
  }

  def sampleRealistic(proposal: Proposal): Double = {
    sampleRealistic(scoreCounts(proposal))
  }

  def sampleScore(proposal: Proposal): Double = {
    val counts = scoreCounts(proposal)
    sampleEngagement(counts) + sampleAdhesion(counts) + 2 * sampleRealistic(counts)
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

  def scoreUpperBound(proposal: Proposal): Double = {
    val counts = scoreCounts(proposal)

    val engagementEstimate: RateEstimate =
      rateEstimate(counts.neutralCount + counts.platitudeAgreeCount + counts.platitudeDisagreeCount, counts.votes)

    val adhesionEstimate: RateEstimate =
      rateEstimate(math.max(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount)

    val realisticEstimate: RateEstimate =
      rateEstimate(math.max(counts.doableCount, counts.impossibleCount), counts.votes - counts.neutralCount)

    val scoreEstimate: Double = score(counts)
    val confidenceInterval: Double = 2 * math.sqrt(
      math.pow(engagementEstimate.sd, 2) +
        math.pow(adhesionEstimate.sd, 2) +
        2 * math.pow(realisticEstimate.sd, 2)
    )

    scoreEstimate + confidenceInterval
  }

  def controversyUpperBound(proposal: Proposal): Double = {
    val counts = scoreCounts(proposal)

    val controversyEstimate: RateEstimate =
      rateEstimate(math.min(counts.loveCount, counts.hateCount), counts.votes - counts.neutralCount)

    controversyEstimate.rate + 2 * controversyEstimate.sd
  }
}

trait SelectionAlgorithmComponent {
  val selectionAlgorithm: SelectionAlgorithm
}

trait SelectionAlgorithm {
  def selectProposalsForSequence(targetLength: Int,
                                 sequenceConfiguration: SequenceConfiguration,
                                 proposals: Seq[Proposal],
                                 votedProposals: Seq[ProposalId],
                                 includeList: Seq[ProposalId]): Seq[ProposalId]
}

trait DefaultSelectionAlgorithmComponent extends SelectionAlgorithmComponent with StrictLogging {

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
       can appear in each sequence
    - the non imposed proposals are ordered randomly
     */
    def selectProposalsForSequence(targetLength: Int,
                                   sequenceConfiguration: SequenceConfiguration,
                                   proposals: Seq[Proposal],
                                   votedProposals: Seq[ProposalId],
                                   includeList: Seq[ProposalId]): Seq[ProposalId] = {

      // fetch included proposals and exclude same idea
      val includedProposals: Seq[Proposal] = proposals.filter(p                 => includeList.contains(p.proposalId))
      val includedProposalsToExclude: Seq[ProposalId] = includedProposals.map(p => p.proposalId)
      val includedIdeasToExclude: Seq[IdeaId] = includedProposals.flatMap(p     => p.idea)

      // fetch available proposals for user
      val availableProposals: Seq[Proposal] = proposals.filter(
        p =>
          p.status == ProposalStatus.Accepted &&
            !includedProposalsToExclude.contains(p.proposalId) &&
            !includedIdeasToExclude.contains(p.idea.getOrElse(IdeaId("None"))) &&
            !votedProposals.contains(p.proposalId)
      )

      // balance proposals between new and tested
      val proposalsToChoose: Int = targetLength - includeList.size
      val targetNewProposalsCount: Int =
        math.ceil(proposalsToChoose * sequenceConfiguration.newProposalsRatio).toInt

      // chooses new proposals
      val newIncludedProposals: Seq[Proposal] =
        chooseNewProposals(sequenceConfiguration, availableProposals, targetNewProposalsCount)
      val newProposalsToExclude: Seq[ProposalId] = newIncludedProposals.map(p => p.proposalId)
      val newIdeasToExclude: Seq[IdeaId] = newIncludedProposals.flatMap(p     => p.idea)

      // chooses tested proposals
      val remainingProposals: Seq[Proposal] = availableProposals.filter(
        p =>
          !newProposalsToExclude.contains(p.proposalId) && !newIdeasToExclude.contains(p.idea.getOrElse(IdeaId("None")))
      )
      val testedProposalCount: Int = proposalsToChoose - newIncludedProposals.size
      val testedIncludedProposals: Seq[Proposal] =
        chooseTestedProposals(sequenceConfiguration, remainingProposals, testedProposalCount)

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

    def chooseProposals(proposals: Seq[Proposal], count: Int, algorithm: ProposalChooser): Seq[Proposal] = {
      if (proposals.isEmpty || count <= 0) {
        Seq.empty
      } else {
        val chosen: Proposal = algorithm.choose(proposals)
        Seq(chosen) ++ chooseProposals(
          count = count - 1,
          proposals = proposals.filter(
            p =>
              p.proposalId != chosen.proposalId &&
                p.idea.getOrElse(IdeaId("Empty")) != chosen.idea.getOrElse(IdeaId("None"))
          ),
          algorithm = algorithm
        )
      }
    }

    def chooseNewProposals(sequenceConfiguration: SequenceConfiguration,
                           availableProposals: Seq[Proposal],
                           targetNewProposalsCount: Int): Seq[Proposal] = {
      val newProposals: Seq[Proposal] = availableProposals.filter { proposal =>
        val votes: Int = proposal.votes.map(_.count).sum
        votes < sequenceConfiguration.newProposalsVoteThreshold
      }
      chooseProposals(proposals = newProposals, count = targetNewProposalsCount, algorithm = OldestProposalChooser)
    }

    case class ScoredProposal(proposal: Proposal, score: Double)

    /*
     * Chooses the top proposals of each cluster according to the bandit algorithm
     * Keep at least 3 proposals per ideas
     * Then keep the top quartile
     */
    def chooseProposalBandit(sequenceConfiguration: SequenceConfiguration, proposals: Seq[Proposal]): Proposal = {

      val proposalsScored: Seq[ScoredProposal] =
        proposals.map(p => ScoredProposal(p, ProposalScorer.sampleScore(p)))

      val shortList = if (proposals.length < sequenceConfiguration.banditMinCount) {
        proposals
      } else {
        val count = math.max(
          sequenceConfiguration.banditMinCount,
          ceil(proposals.length * sequenceConfiguration.banditProposalsRatio).toInt
        )
        proposalsScored.sortWith(_.score > _.score).take(count).map(sp => sp.proposal)
      }

      UniformRandom.choose(shortList)
    }

    def chooseTestedProposals(sequenceConfiguration: SequenceConfiguration,
                              availableProposals: Seq[Proposal],
                              testedProposalCount: Int): Seq[Proposal] = {

      // filter proposals
      val testedProposals: Seq[Proposal] = availableProposals.filter { proposal =>
        val votes: Int = proposal.votes.map(_.count).sum
        val engagementRate: Double = ProposalScorer.engagementUpperBound(proposal)
        val scoreRate: Double = ProposalScorer.scoreUpperBound(proposal)
        val controversyRate: Double = ProposalScorer.controversyUpperBound(proposal)
        (votes >= sequenceConfiguration.newProposalsVoteThreshold
        && engagementRate > sequenceConfiguration.testedProposalsEngagementThreshold
        && (scoreRate > sequenceConfiguration.testedProposalsScoreThreshold
        || controversyRate > sequenceConfiguration.testedProposalsControversyThreshold))
      }

      // group by idea
      val ideas: Map[IdeaId, Seq[Proposal]] =
        testedProposals.groupBy(p => p.idea.getOrElse(IdeaId(p.proposalId.value)))

      // select ideas
      val selectedIdeas: Seq[IdeaId] = ideas.keys.toSeq

      // pick one proposal for each idea
      val selectedProposals: Seq[Proposal] = ideas
        .filterKeys(selectedIdeas.contains(_))
        .mapValues(
          proposals =>
            if (sequenceConfiguration.banditEnabled) {
              chooseProposalBandit(sequenceConfiguration, proposals)
            } else {
              chooseProposals(proposals, 1, SoftMinRandom).head
          }
        )
        .values
        .toSeq

      // and finally pick the proposals
      chooseProposals(selectedProposals, testedProposalCount, SoftMinRandom)
    }

    def complementSequence(sequence: Seq[ProposalId],
                           targetLength: Int,
                           proposals: Seq[Proposal],
                           availableProposals: Seq[Proposal]): Seq[ProposalId] = {
      val excludeIdeas: Set[IdeaId] =
        proposals.filter(p => sequence.contains(p.proposalId)).flatMap(_.idea).toSet

      sequence ++ chooseProposals(
        availableProposals.filter(
          p =>
            !sequence.contains(p.proposalId) &&
              !excludeIdeas.contains(p.idea.getOrElse(IdeaId("None")))
        ),
        targetLength - sequence.size,
        OldestProposalChooser
      ).map(_.proposalId)
    }
  }
}
