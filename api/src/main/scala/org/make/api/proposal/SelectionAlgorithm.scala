/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.proposal

import grizzled.slf4j.Logging
import org.make.api.proposal.DefaultSelectionAlgorithmComponent.Scored
import org.make.api.proposal.ProposalScorer.{Score, VotesCounter}
import org.make.api.technical.MakeRandom
import org.make.core.DateHelper._
import org.make.core.idea.IdeaId
import org.make.core.proposal._
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.sequence.{SelectionAlgorithmName, SpecificSequenceConfiguration}

import Ordering.Double.IeeeOrdering
import scala.annotation.tailrec
import scala.math.ceil

trait ProposalChooser {
  def choose(proposals: Seq[IndexedProposal]): IndexedProposal
}

object OldestProposalChooser extends ProposalChooser {
  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  override def choose(proposals: Seq[IndexedProposal]): IndexedProposal = {
    proposals.minBy(_.createdAt)
  }
}

object RoundRobin extends ProposalChooser {
  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  override def choose(proposals: Seq[IndexedProposal]): IndexedProposal = {
    proposals.minBy(_.votesSequenceCount)
  }
}

trait RandomProposalChooser extends ProposalChooser {
  protected def proposalWeight(proposal: IndexedProposal): Double

  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  @tailrec
  private final def search(
    proposals: Seq[IndexedProposal],
    choice: Double,
    accumulatedSum: Double = 0
  ): IndexedProposal = {
    val accumulatedSumNew = accumulatedSum + proposalWeight(proposals.head)
    if (choice <= accumulatedSumNew) {
      proposals.head
    } else {
      search(proposals.tail, choice, accumulatedSumNew)
    }
  }

  final def choose(proposals: Seq[IndexedProposal]): IndexedProposal = {
    val weightSum: Double = proposals.map(proposalWeight).sum
    val choice: Double = MakeRandom.nextDouble() * weightSum
    search(proposals, choice)
  }
}

object SoftMinRandom extends RandomProposalChooser {
  override def proposalWeight(proposal: IndexedProposal): Double = {
    Math.exp(-1 * proposal.votes.map(_.countSequence).iterator.sum)
  }
}

object UniformRandom extends ProposalChooser with Logging {
  def choose(proposals: Seq[IndexedProposal]): IndexedProposal = {
    proposals(MakeRandom.nextInt(proposals.length))
  }

}

trait SelectionAlgorithmComponent {
  val banditSelectionAlgorithm: SelectionAlgorithm
  val roundRobinSelectionAlgorithm: SelectionAlgorithm
  val randomSelectionAlgorithm: SelectionAlgorithm
}

trait SelectionAlgorithm {
  def name: SelectionAlgorithmName

  def selectProposalsForSequence(
    sequenceConfiguration: SpecificSequenceConfiguration,
    nonSequenceVotesWeight: Double,
    includedProposals: Seq[IndexedProposal],
    newProposals: Seq[IndexedProposal],
    testedProposals: Seq[IndexedProposal],
    userSegment: Option[String]
  ): Seq[IndexedProposal]
}

trait DefaultSelectionAlgorithmComponent extends SelectionAlgorithmComponent with Logging {

  override val banditSelectionAlgorithm: BanditSelectionAlgorithm = new BanditSelectionAlgorithm
  override val roundRobinSelectionAlgorithm: RoundRobinSelectionAlgorithm = new RoundRobinSelectionAlgorithm
  override val randomSelectionAlgorithm: RandomSelectionAlgorithm = new RandomSelectionAlgorithm

  def isSameIdea(ideaOption1: Option[IdeaId], ideaOption2: Option[IdeaId]): Boolean = {
    (ideaOption1, ideaOption2) match {
      case (Some(ideaId1), Some(ideaId2)) => ideaId1 == ideaId2
      case _                              => false
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
  class BanditSelectionAlgorithm extends SelectionAlgorithm {

    override val name: SelectionAlgorithmName = SelectionAlgorithmName.Bandit
    /*
    Returns the list of proposal to display in the sequence
    The proposals are chosen such that:
    - if they are imposed proposals (includeList) they will appear first
    - the rest is 50/50 new proposals to test (less than newProposalVoteCount votes)
      and tested proposals (more than newProposalVoteCount votes)
    - newProposals and testedProposals arguments must be distinct by author
    - new proposals are tested in a first-in first-out mode until they reach newProposalVoteCount votes
    - tested proposals are filtered out if their engagement rate is too low
    - if there are not enough tested proposals to provide the requested number of proposals,
      the sequence is completed with new proposals
    - the candidates proposals are filtered such that only one proposal by ideas
       can appear in each sequence
    - the non imposed proposals are ordered randomly
     */
    def selectProposalsForSequence(
      sequenceConfiguration: SpecificSequenceConfiguration,
      nonSequenceVotesWeight: Double,
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal],
      testedProposals: Seq[IndexedProposal],
      userSegment: Option[String]
    ): Seq[IndexedProposal] = {

      def uniqueIdeaIdForProposal(proposal: IndexedProposal): IdeaId =
        IdeaId(proposal.id.value)

      // fetch included proposals and exclude same idea
      var sequence: Seq[IndexedProposal] = includedProposals
      var distinctProposals: Set[ProposalId] = includedProposals.map(_.id).toSet
      var distinctIdeas: Set[IdeaId] = includedProposals.map(p => p.ideaId.getOrElse(uniqueIdeaIdForProposal(p))).toSet

      // distinct proposals by distinct ideas
      def filterDistinct(
        proposals: Seq[IndexedProposal],
        proposalIds: Set[ProposalId] = distinctProposals,
        ideaIds: Set[IdeaId] = distinctIdeas
      ): Map[IdeaId, Seq[IndexedProposal]] =
        proposals
          .groupBy(p => p.ideaId.getOrElse(uniqueIdeaIdForProposal(p)))
          .filter { case (ideaId, _) => !ideaIds.contains(ideaId) }
          .map { case (ideaId, value) => ideaId -> value.filterNot(p => proposalIds.contains(p.id)) }
          .filterNot {
            case (_, proposalsList) => proposalsList.isEmpty
          }

      def includeProposalToSequence(proposals: Seq[IndexedProposal]): Seq[IndexedProposal] = {
        sequence          ++= proposals
        distinctProposals ++= proposals.map(_.id).toSet
        distinctIdeas     ++= proposals.map(p => p.ideaId.getOrElse(uniqueIdeaIdForProposal(p))).toSet
        proposals
      }

      // balance proposals between new and tested
      val sequenceSize: Int = sequenceConfiguration.sequenceSize
      val includedSize: Int = includedProposals.size

      val newProposalsByIdea: Map[IdeaId, Seq[IndexedProposal]] = filterDistinct(newProposals)

      val proposalsToChoose: Int = sequenceSize - includedSize
      val askedNewProposalsCount: Int =
        math.ceil(proposalsToChoose * sequenceConfiguration.newProposalsRatio).toInt

      val maxNewIdeas: Int = newProposalsByIdea.keys.size
      val maxTestedIdeas: Int = testedProposals.map(p => p.ideaId.getOrElse(uniqueIdeaIdForProposal(p))).toSet.size

      val newProposalsCount: Int =
        math.min(math.max(askedNewProposalsCount, sequenceSize - maxTestedIdeas - includedSize), maxNewIdeas)
      val targetTestedProposalCount: Int = proposalsToChoose - newProposalsCount
      val testedSize: Int =
        math.min(math.max(targetTestedProposalCount, sequenceSize - maxNewIdeas - includedSize), maxTestedIdeas)

      // chooses new proposals
      val newIncludedProposals: Seq[IndexedProposal] =
        includeProposalToSequence(
          chooseProposals(
            proposals = newProposalsByIdea.values.map(_.head).toSeq,
            count = newProposalsCount,
            algorithm = OldestProposalChooser
          )
        )

      // chooses tested proposals
      val testedIncludedProposals: Seq[IndexedProposal] =
        includeProposalToSequence(
          chooseTestedProposals(
            sequenceConfiguration,
            nonSequenceVotesWeight,
            filterDistinct(testedProposals),
            testedSize,
            userSegment
          )
        )

      buildSequence(
        includedProposals,
        newIncludedProposals,
        testedIncludedProposals,
        userSegment,
        nonSequenceVotesWeight
      )
    }

    /**
      * Build the sequence
      *
      * first: included proposals
      * then: most engaging tested proposals
      * finally: randomized new + tested proposals
      *
      */
    def buildSequence(
      includedProposals: Seq[IndexedProposal],
      newIncludedProposals: Seq[IndexedProposal],
      testedIncludedProposals: Seq[IndexedProposal],
      maybeUserSegment: Option[String],
      nonSequenceVotesWeight: Double
    ): Seq[IndexedProposal] = {

      // build sequence
      if (includedProposals.isEmpty && testedIncludedProposals.nonEmpty) {
        // pick most engaging
        val sortedTestedProposal: Seq[IndexedProposal] =
          testedIncludedProposals.sortBy { proposal =>
            val scorer = computeProposalScores(maybeUserSegment, proposal, nonSequenceVotesWeight)
            -1 * scorer.engagement.lowerBound
          }
        Seq(sortedTestedProposal.head) ++ MakeRandom.shuffleSeq(newIncludedProposals ++ sortedTestedProposal.tail)
      } else if (includedProposals.nonEmpty && testedIncludedProposals.nonEmpty) {
        includedProposals ++ MakeRandom.shuffleSeq(newIncludedProposals ++ testedIncludedProposals)
      } else {
        includedProposals ++ MakeRandom.shuffleSeq(newIncludedProposals)
      }
    }

    @tailrec
    final def chooseProposals(
      proposals: Seq[IndexedProposal],
      count: Int,
      algorithm: ProposalChooser,
      aggregator: Seq[IndexedProposal] = Seq.empty
    ): Seq[IndexedProposal] = {
      if (proposals.isEmpty || count <= 0) {
        aggregator
      } else {
        val chosen: IndexedProposal = algorithm.choose(MakeRandom.shuffleSeq(proposals))
        chooseProposals(
          proposals = proposals.filter(p => p.id != chosen.id),
          count = count - 1,
          algorithm = algorithm,
          aggregator = aggregator ++ Seq(chosen)
        )
      }
    }

    /*
     * Chooses the top proposals of each cluster according to the bandit algorithm
     * Keep at least 3 proposals per ideas
     * Then keep the top quartile
     */
    def chooseProposalBandit(
      sequenceConfiguration: SpecificSequenceConfiguration,
      nonSequenceVotesWeight: Double,
      proposals: Seq[IndexedProposal],
      maybeUserSegment: Option[String]
    ): IndexedProposal = {

      val proposalsScored: Seq[Scored[IndexedProposal]] =
        proposals.map { proposal =>
          val scorer = computeProposalScores(maybeUserSegment, proposal, nonSequenceVotesWeight)

          Scored(proposal, scorer.topScore.cachedSample)
        }

      val shortList = if (proposals.length < sequenceConfiguration.intraIdeaMinCount) {
        proposals
      } else {
        val count = math.max(
          sequenceConfiguration.intraIdeaMinCount,
          ceil(proposals.length * sequenceConfiguration.intraIdeaProposalsRatio).toInt
        )
        proposalsScored.sortWith(_.score > _.score).take(count).map(sp => sp.item)
      }

      UniformRandom.choose(shortList)
    }

    def chooseChampion(
      nonSequenceVotesWeight: Double,
      proposals: Seq[IndexedProposal],
      maybeUserSegment: Option[String]
    ): IndexedProposal = {
      chooseChampion(_.topScore)(nonSequenceVotesWeight, proposals, maybeUserSegment)
    }

    def chooseControversyChampion(
      nonSequenceVotesWeight: Double,
      proposals: Seq[IndexedProposal],
      maybeUserSegment: Option[String]
    ): IndexedProposal = {
      chooseChampion(_.controversy)(nonSequenceVotesWeight, proposals, maybeUserSegment)
    }

    private def chooseChampion(
      score: ProposalScorer => Score
    ): (Double, Seq[IndexedProposal], Option[String]) => IndexedProposal = {
      (nonSequenceVotesWeight, proposals, maybeUserSegment) =>
        proposals.maxBy { proposal =>
          val scorer = computeProposalScores(maybeUserSegment, proposal, nonSequenceVotesWeight)
          score(scorer).lowerBound
        }
    }

    def selectIdeasWithChampions(
      sequenceConfiguration: SpecificSequenceConfiguration,
      nonSequenceVotesWeight: Double,
      champions: Map[IdeaId, IndexedProposal],
      maybeUserSegment: Option[String]
    ): Seq[IdeaId] = {
      chooseIdea(_.topScore.cachedSample)(
        nonSequenceVotesWeight,
        maybeUserSegment,
        sequenceConfiguration.interIdeaCompetitionTargetCount,
        champions
      )
    }

    def selectControversialIdeasWithChampions(
      sequenceConfiguration: SpecificSequenceConfiguration,
      nonSequenceVotesWeight: Double,
      champions: Map[IdeaId, IndexedProposal],
      maybeUserSegment: Option[String]
    ): Seq[IdeaId] = {
      chooseIdea(_.controversy.cachedSample)(
        nonSequenceVotesWeight,
        maybeUserSegment,
        sequenceConfiguration.interIdeaCompetitionControversialCount,
        champions
      )
    }

    private def chooseIdea(scoringFunction: ProposalScorer => Double)(
      nonSequenceVotesWeight: Double,
      maybeUserSegment: Option[String],
      ideaCount: Int,
      champions: Map[IdeaId, IndexedProposal]
    ): Seq[IdeaId] = {
      champions.toSeq.map {
        case (idea, proposal) =>
          val scorer = computeProposalScores(maybeUserSegment, proposal, nonSequenceVotesWeight)
          val score = scoringFunction(scorer)
          Scored(idea, score)
      }.sortBy(-1 * _.score).take(ideaCount).map(_.item)
    }

    def chooseTestedProposals(
      sequenceConfiguration: SpecificSequenceConfiguration,
      nonSequenceVotesWeight: Double,
      testedProposalsByIdea: Map[IdeaId, Seq[IndexedProposal]],
      testedProposalCount: Int,
      maybeUserSegment: Option[String]
    ): Seq[IndexedProposal] = {
      // select ideas
      val selectedIdeas: Seq[IdeaId] = if (sequenceConfiguration.interIdeaCompetitionEnabled) {
        val champions: Map[IdeaId, IndexedProposal] =
          testedProposalsByIdea.map {
            case (idea, proposal) => idea -> chooseChampion(nonSequenceVotesWeight, proposal, maybeUserSegment)
          }
        val topIdeas: Seq[IdeaId] =
          selectIdeasWithChampions(sequenceConfiguration, nonSequenceVotesWeight, champions, maybeUserSegment)
        val controversyChampions: Map[IdeaId, IndexedProposal] =
          testedProposalsByIdea.map {
            case (idea, proposal) =>
              idea -> chooseControversyChampion(nonSequenceVotesWeight, proposal, maybeUserSegment)
          }
        val topControversial: Seq[IdeaId] =
          selectControversialIdeasWithChampions(
            sequenceConfiguration,
            nonSequenceVotesWeight,
            controversyChampions,
            maybeUserSegment
          )
        topIdeas ++ topControversial
      } else {
        testedProposalsByIdea.keys.toSeq
      }

      // pick one proposal for each idea
      val selectedProposals: Seq[IndexedProposal] = testedProposalsByIdea.filter {
        case (key, _) => selectedIdeas.contains(key)
      }.map {
        case (key, proposals) =>
          if (sequenceConfiguration.intraIdeaEnabled) {
            key -> chooseProposalBandit(sequenceConfiguration, nonSequenceVotesWeight, proposals, maybeUserSegment)
          } else {
            key -> chooseProposals(proposals, 1, SoftMinRandom).head
          }
      }.values.toSeq

      // and finally pick the requested number of proposals
      chooseProposals(selectedProposals, testedProposalCount, SoftMinRandom)
    }
  }

  private def computeProposalScores(
    maybeUserSegment: Option[String],
    proposal: IndexedProposal,
    nonSequenceVotesWeight: Double
  ): ProposalScorer = {
    if (maybeUserSegment.isDefined && maybeUserSegment == proposal.segment) {
      ProposalScorer(proposal.votes, VotesCounter.SegmentVotesCounter, nonSequenceVotesWeight)
    } else {
      ProposalScorer(proposal.votes, VotesCounter.SequenceVotesCounter, nonSequenceVotesWeight)
    }
  }

  class RoundRobinSelectionAlgorithm extends SelectionAlgorithm {

    override val name: SelectionAlgorithmName = SelectionAlgorithmName.RoundRobin

    /*
    Returns the list of proposal to display in the sequence
    The proposals are chosen such that:
    - if they are imposed proposals (includeList) they will appear first
    - the rest is only new proposals to test (less than newProposalVoteCount votes)
    - new proposals are tested in a round robin mode until they reach newProposalVoteCount votes
    - tested proposals are filtered out if their engagement rate is too low
    - the non imposed proposals are ordered randomly
     */
    def selectProposalsForSequence(
      sequenceConfiguration: SpecificSequenceConfiguration,
      nonSequenceVotesWeight: Double,
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal],
      testedProposals: Seq[IndexedProposal],
      userSegment: Option[String]
    ): Seq[IndexedProposal] = {

      val proposalsPool: Seq[IndexedProposal] = newProposals ++ testedProposals

      // balance proposals between new and tested
      val sequenceSize: Int = sequenceConfiguration.sequenceSize
      val includedSize: Int = includedProposals.size

      val proposalsToChoose: Int = sequenceSize - includedSize

      val proposals = chooseProposals(proposalsPool, proposalsToChoose, RoundRobin)

      buildSequence(includedProposals, proposals)
    }

    /**
      * Build the sequence
      *
      * first: included proposals OR most engaging
      * finally: randomized new + tested proposals
      *
      */
    def buildSequence(
      includedProposals: Seq[IndexedProposal],
      proposals: Seq[IndexedProposal]
    ): Seq[IndexedProposal] = {

      // build sequence
      includedProposals ++ MakeRandom.shuffleSeq(proposals)
    }

    @tailrec
    final def chooseProposals(
      proposals: Seq[IndexedProposal],
      count: Int,
      algorithm: ProposalChooser,
      aggregator: Seq[IndexedProposal] = Seq.empty
    ): Seq[IndexedProposal] = {
      if (proposals.isEmpty || count <= 0) {
        aggregator
      } else {
        val chosen: IndexedProposal = algorithm.choose(proposals)
        chooseProposals(
          proposals = proposals.filter(p => p.id != chosen.id),
          count = count - 1,
          algorithm = algorithm,
          aggregator = aggregator ++ Seq(chosen)
        )
      }
    }
  }

  class RandomSelectionAlgorithm extends SelectionAlgorithm {
    override def name: SelectionAlgorithmName = SelectionAlgorithmName.Random

    override def selectProposalsForSequence(
      sequenceConfiguration: SpecificSequenceConfiguration,
      nonSequenceVotesWeight: Double,
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal],
      testedProposals: Seq[IndexedProposal],
      userSegment: Option[String]
    ): Seq[IndexedProposal] = {

      val excludedIds = includedProposals.map(_.id)
      val candidates = (newProposals ++ testedProposals)
        .filter(p => !excludedIds.contains(p.id))
      val shuffled = MakeRandom.shuffleSeq(candidates)
      includedProposals ++ shuffled.take(sequenceConfiguration.sequenceSize - includedProposals.size)
    }
  }

}

object DefaultSelectionAlgorithmComponent {
  final case class Scored[T](item: T, score: Double)
}
