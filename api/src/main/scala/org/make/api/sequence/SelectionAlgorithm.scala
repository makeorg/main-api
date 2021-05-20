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

package org.make.api.sequence

import org.make.api.proposal.ProposalScorer
import org.make.api.proposal.ProposalScorer.VotesCounter
import org.make.api.technical.MakeRandom
import org.make.core.DateHelper._
import org.make.core.proposal.indexed.SequencePool.Tested
import org.make.core.proposal.indexed.Zone.{Consensus, Controversy}
import org.make.core.proposal.indexed.{IndexedProposal, Zone}
import org.make.core.sequence.{ExplorationConfiguration, SpecificSequenceConfiguration}

import scala.Ordering.Double.IeeeOrdering
import scala.annotation.tailrec

object SelectionAlgorithm {

  object ExplorationSelectionAlgorithm {
    def selectProposalsForSequence(
      configuration: ExplorationConfiguration,
      nonSequenceVotesWeight: Double,
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal],
      testedProposals: Seq[IndexedProposal],
      userSegment: Option[String]
    ): Seq[IndexedProposal] = {

      val neededTested: Int = Math.max(
        Math.ceil((1 - configuration.newRatio) * configuration.sequenceSize).toInt -
          includedProposals.count(_.sequencePool == Tested),
        0
      )

      val neededControversies: Int = Math.round(neededTested * configuration.controversyRatio).toInt
      val neededTops = neededTested - neededControversies

      val votesCounter = if (userSegment.isDefined) {
        VotesCounter.SegmentVotesCounter
      } else {
        VotesCounter.SequenceVotesCounter
      }

      val controversySorter = Sorter.parse(configuration.controversySorter)
      val topSorter = Sorter.parse(configuration.topSorter)

      val testedProposalsConfiguration = TestedProposalsSelectionConfiguration(
        votesCounter = votesCounter,
        ratio = nonSequenceVotesWeight,
        neededControversies = neededControversies,
        controversySorter = controversySorter,
        neededTops = neededTops,
        topSorter = topSorter
      )
      val chosenTestedProposals = chooseTestedProposal(testedProposals, testedProposalsConfiguration)
      val chosenNewProposals: Seq[IndexedProposal] =
        NewProposalsChooser.choose(
          newProposals,
          Math.max(configuration.sequenceSize - includedProposals.size - chosenTestedProposals.size, 0)
        )

      includedProposals ++ MakeRandom.shuffleSeq(chosenNewProposals ++ chosenTestedProposals)
    }

    private def chooseTestedProposal(
      testedProposals: Seq[IndexedProposal],
      configuration: TestedProposalsSelectionConfiguration
    ): Seq[IndexedProposal] = {
      val (tops, controversies) = testedProposals.flatMap { proposal =>
        val scorer = new ProposalScorer(proposal.votes, configuration.votesCounter, configuration.ratio)
        scorer.sampledZone match {
          case Consensus =>
            Seq(ZonedProposal(proposal, Consensus, scorer.topScore.cachedSample))
          case Controversy =>
            Seq(ZonedProposal(proposal, Controversy, scorer.controversy.cachedSample))
          case _ =>
            Seq.empty
        }
      }.partition(_.zone == Consensus)

      val sortedControversies = configuration.controversySorter.sort(controversies)
      val sortedTops = configuration.topSorter.sort(tops)

      Equalizer.choose(sortedControversies, configuration.neededControversies) ++
        Equalizer.choose(sortedTops, configuration.neededTops)
    }

    final case class TestedProposalsSelectionConfiguration(
      votesCounter: VotesCounter,
      ratio: Double,
      neededControversies: Int,
      controversySorter: Sorter,
      neededTops: Int,
      topSorter: Sorter
    )

    final case class ZonedProposal(proposal: IndexedProposal, zone: Zone, score: Double)

    sealed trait Sorter {
      def sort(proposals: Seq[ZonedProposal]): Seq[IndexedProposal]
    }

    object Sorter {
      def parse(name: String): Sorter = {
        name match {
          case "bandit"      => BanditSorter
          case "round-robin" => RoundRobinSorter
          case _             => RandomSorter
        }
      }
    }

    object BanditSorter extends Sorter {
      override def sort(proposals: Seq[ZonedProposal]): Seq[IndexedProposal] = {
        proposals.sortBy(-_.score).map(_.proposal)
      }
    }

    object RandomSorter extends Sorter {
      override def sort(proposals: Seq[ZonedProposal]): Seq[IndexedProposal] = {
        MakeRandom.shuffleSeq(proposals).map(_.proposal)
      }
    }

    object RoundRobinSorter extends Sorter {
      override def sort(proposals: Seq[ZonedProposal]): Seq[IndexedProposal] = {
        proposals.map(_.proposal).sortBy(_.votesSequenceCount)
      }
    }

    object NewProposalsChooser {
      def choose(candidates: Seq[IndexedProposal], neededCount: Int): Seq[IndexedProposal] = {
        candidates.sortBy(_.createdAt).distinctBy(_.userId).take(neededCount)
      }
    }

    object Equalizer {
      private val candidatesPool: Int = 10

      def choose(candidates: Seq[IndexedProposal], neededCount: Int): Seq[IndexedProposal] = {
        chooseRecursively(Seq.empty, candidates, neededCount)
      }

      @tailrec
      private def chooseRecursively(
        accumulator: Seq[IndexedProposal],
        candidates: Seq[IndexedProposal],
        neededCount: Int
      ): Seq[IndexedProposal] = {
        if (neededCount <= 0) {
          accumulator
        } else {
          candidates.take(candidatesPool).sortBy(_.createdAt).headOption match {
            case None => accumulator
            case Some(chosen) =>
              chooseRecursively(
                accumulator = accumulator :+ chosen,
                candidates = deduplicate(chosen, candidates),
                neededCount = neededCount - 1
              )
          }
        }
      }

      private def deduplicate(chosen: IndexedProposal, candidates: Seq[IndexedProposal]): Seq[IndexedProposal] = {
        candidates.filter { proposal =>
          proposal.keywords.intersect(chosen.keywords).isEmpty && chosen.userId != proposal.userId
        }
      }
    }
  }

  object RoundRobinSelectionAlgorithm {

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
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal],
      testedProposals: Seq[IndexedProposal]
    ): Seq[IndexedProposal] = {

      val proposalsPool: Seq[IndexedProposal] = newProposals ++ testedProposals

      // balance proposals between new and tested
      val sequenceSize: Int = sequenceConfiguration.sequenceSize
      val includedSize: Int = includedProposals.size

      val proposalsToChoose: Int = sequenceSize - includedSize

      val proposals = proposalsPool.sortBy(_.votesSequenceCount).take(proposalsToChoose)

      includedProposals ++ MakeRandom.shuffleSeq(proposals)
    }

  }

  object RandomSelectionAlgorithm {

    def selectProposalsForSequence(
      sequenceConfiguration: SpecificSequenceConfiguration,
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal],
      testedProposals: Seq[IndexedProposal]
    ): Seq[IndexedProposal] = {

      val excludedIds = includedProposals.map(_.id)
      val candidates = (newProposals ++ testedProposals)
        .filter(p => !excludedIds.contains(p.id))
      val shuffled = MakeRandom.shuffleSeq(candidates)
      includedProposals ++ shuffled.take(sequenceConfiguration.sequenceSize - includedProposals.size)
    }
  }
}
