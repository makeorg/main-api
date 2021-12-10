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
import org.make.core.proposal.indexed.SequencePool.New
import org.make.core.proposal.indexed.Zone.{Consensus, Controversy}
import org.make.core.proposal.indexed.{IndexedProposal, Zone}
import org.make.core.sequence.{
  ExplorationSequenceConfiguration,
  ExplorationSortAlgorithm,
  SpecificSequenceConfiguration
}
import eu.timepit.refined.auto._
import org.make.core.proposal.ProposalKeywordKey
import org.make.core.technical.RefinedTypes.Ratio

import scala.Ordering.Double.IeeeOrdering
import scala.annotation.tailrec
import org.make.core.technical.CollectionUtils.ImprovedSeq
import scala.collection.mutable.{HashSet => MSet}

object SelectionAlgorithm {

  object ExplorationSelectionAlgorithm {
    def selectProposalsForSequence(
      configuration: ExplorationSequenceConfiguration,
      nonSequenceVotesWeight: Double,
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal],
      testedProposals: Seq[IndexedProposal],
      userSegment: Option[String]
    ): Seq[IndexedProposal] = {

      // Choose new proposals first and complete with enough tested proposals.
      // this setting will give more sequence fallbacks in the beginning of consultations
      // but should remove the need of fallback in the end of consultations, in the "vote-only" phase
      val chosenNewProposals: Seq[IndexedProposal] = chooseNewProposals(configuration, includedProposals, newProposals)

      val testedProposalsConfiguration = createTestedProposalsConfiguration(
        configuration,
        nonSequenceVotesWeight,
        includedProposals,
        userSegment,
        chosenNewProposals.size
      )

      val deDuplicatedTestedProposals = removeAuthorDuplication(chosenNewProposals, testedProposals)
      val chosenTestedProposals = chooseTestedProposal(deDuplicatedTestedProposals, testedProposalsConfiguration)

      includedProposals ++ MakeRandom.shuffleSeq(chosenNewProposals ++ chosenTestedProposals)
    }

    private def chooseNewProposals(
      configuration: ExplorationSequenceConfiguration,
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal]
    ): Seq[IndexedProposal] = {

      val neededNewProposals: Int = Math.max(
        Math.ceil(configuration.newRatio * configuration.sequenceSize).toInt -
          includedProposals.count(_.sequencePool == New),
        0
      )

      NewProposalsChooser.choose(newProposals, neededNewProposals)
    }

    private def createTestedProposalsConfiguration(
      configuration: ExplorationSequenceConfiguration,
      nonSequenceVotesWeight: Double,
      includedProposals: Seq[IndexedProposal],
      userSegment: Option[String],
      chosenNewProposalsCount: Int
    ): TestedProposalsSelectionConfiguration = {
      val votesCounter = if (userSegment.isDefined) {
        VotesCounter.SegmentVotesCounter
      } else {
        VotesCounter.SequenceVotesCounter
      }

      val neededTested = Math.max(configuration.sequenceSize - chosenNewProposalsCount - includedProposals.size, 0)
      val neededControversies: Int = Math.round(neededTested * configuration.controversyRatio).toInt
      val neededTops = neededTested - neededControversies

      val controversySorter = Sorter.parse(configuration.controversySorter)
      val topSorter = Sorter.parse(configuration.topSorter)

      TestedProposalsSelectionConfiguration(
        votesCounter = votesCounter,
        ratio = nonSequenceVotesWeight,
        neededControversies = neededControversies,
        controversySorter = controversySorter,
        neededTops = neededTops,
        topSorter = topSorter,
        keywordsThreshold = configuration.keywordsThreshold,
        candidatesPoolSize = configuration.candidatesPoolSize
      )
    }

    @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
    def resolveIgnoredKeywords(proposals: Seq[IndexedProposal], keywordsThreshold: Ratio): MSet[ProposalKeywordKey] = {
      if (proposals.isEmpty) {
        MSet.empty
      } else {
        val maxOccurrences = Math.floor(keywordsThreshold * proposals.size).toInt
        proposals
          .flatMap(_.keywords)
          .countOccurencesBy[ProposalKeywordKey](_.key)
          .collect {
            case (key, occurences) if occurences >= maxOccurrences => key
          }
          .to(MSet)
      }
    }

    private def chooseTestedProposal(
      testedProposals: Seq[IndexedProposal],
      configuration: TestedProposalsSelectionConfiguration
    ): Seq[IndexedProposal] = {
      val (tops, controversies) =
        testedProposals
          .zip(
            testedProposals
              .map(proposal => ProposalScorer(proposal.votes, configuration.votesCounter, configuration.ratio))
          )
          .collect({
            case (proposal, scorer) if scorer.sampledZone == Consensus =>
              ZonedProposal(proposal, Consensus, scorer.topScore.sample)
            case (proposal, scorer) if scorer.sampledZone == Controversy =>
              ZonedProposal(proposal, Controversy, scorer.controversy.sample)
          })
          .partition(_.zone == Consensus)

      // Applying the different selection algorithms consists in sorting the proposals in some order,
      // and then take enough of them, using the same de-duplication + equalizer everytime.

      val sortedControversies = configuration.controversySorter.sort(controversies)
      val sortedTops = configuration.topSorter.sort(tops)

      val ignoredKeywords = resolveIgnoredKeywords(testedProposals, configuration.keywordsThreshold)

      val chosenControversies = TestedProposalsChooser.choose(
        sortedControversies,
        configuration.neededControversies,
        ignoredKeywords,
        configuration.candidatesPoolSize
      )
      val availableTops = removeAuthorDuplication(chosenControversies, sortedTops)
      chosenControversies ++
        TestedProposalsChooser.choose(
          availableTops,
          configuration.neededTops,
          ignoredKeywords,
          configuration.candidatesPoolSize
        )
    }

    @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
    private def removeAuthorDuplication(
      chosenProposals: Seq[IndexedProposal],
      candidates: Seq[IndexedProposal]
    ): Seq[IndexedProposal] = {
      val chosenAuthors = chosenProposals.map(_.userId).to(MSet)
      candidates.filterNot(p => chosenAuthors.contains(p.userId))
    }

    final case class TestedProposalsSelectionConfiguration(
      votesCounter: VotesCounter,
      ratio: Double,
      neededControversies: Int,
      controversySorter: Sorter,
      neededTops: Int,
      topSorter: Sorter,
      keywordsThreshold: Ratio,
      candidatesPoolSize: Int
    )

    final case class ZonedProposal(proposal: IndexedProposal, zone: Zone, score: Double)

    sealed trait Sorter {
      def sort(proposals: Seq[ZonedProposal]): Seq[IndexedProposal]
    }

    object Sorter {
      def parse(name: ExplorationSortAlgorithm): Sorter = {
        name match {
          case ExplorationSortAlgorithm.Bandit    => BanditSorter
          case ExplorationSortAlgorithm.Equalizer => EqualizerSorter
          case ExplorationSortAlgorithm.Random    => RandomSorter
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

    object EqualizerSorter extends Sorter {
      override def sort(proposals: Seq[ZonedProposal]): Seq[IndexedProposal] = {
        proposals.map(_.proposal).sortBy(_.votesSequenceCount)
      }
    }

    object NewProposalsChooser {
      def choose(candidates: Seq[IndexedProposal], neededCount: Int): Seq[IndexedProposal] = {
        candidates.sortBy(_.createdAt).distinctBy(_.userId).take(neededCount)
      }
    }

    object TestedProposalsChooser {

      /**
        * Chooses some proposals in a sorted list of proposals and preserve a few properties:
        * - no keyword duplication in the list (ie. a given keyword should appear only once)
        * - no author duplication in the list
        * - proposals with fewer votes will be boosted (see below)
        *
        * In order to boost the proposals with fewer votes, instead of taking the first element of the list,
        * the one with the fewest number of sequence votes in the 10 first proposals is chosen.
        * The proposal pool is then cleaned of proposals sharing any keyword / author with that proposal
        * before choosing the next proposal.
        *
        * @param candidates the whole pool of proposals from which to choose
        * @param neededCount the target number of proposals needed
        * @param ignoredKeywords keywords that shouldn't be used to deduplicate proposals
        * @return a list consisting of at most the required number of proposals, without any duplication.
        *         The result can be empty or contain fewer elements than required.
        */
      def choose(
        candidates: Seq[IndexedProposal],
        neededCount: Int,
        ignoredKeywords: MSet[ProposalKeywordKey],
        candidatesPoolSize: Int
      ): Seq[IndexedProposal] = {
        chooseRecursively(List.empty, candidates, neededCount, ignoredKeywords, candidatesPoolSize)
      }

      @tailrec
      private def chooseRecursively(
        accumulator: List[IndexedProposal],
        candidates: Seq[IndexedProposal],
        neededCount: Int,
        ignoredKeywords: MSet[ProposalKeywordKey],
        candidatesPoolSize: Int
      ): Seq[IndexedProposal] = {
        if (neededCount <= 0) {
          accumulator
        } else {
          candidates.take(candidatesPoolSize).minByOption(_.votesSequenceCount) match {
            case None => accumulator
            case Some(chosen) =>
              val newAccumulator = chosen :: accumulator
              if (neededCount <= 1) {
                newAccumulator
              } else {
                chooseRecursively(
                  accumulator = newAccumulator,
                  candidates = deduplicate(chosen, candidates, ignoredKeywords),
                  neededCount = neededCount - 1,
                  ignoredKeywords,
                  candidatesPoolSize
                )
              }
          }
        }
      }

      @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
      private def deduplicate(
        chosen: IndexedProposal,
        candidates: Seq[IndexedProposal],
        ignoredKeywords: MSet[ProposalKeywordKey]
      ): Seq[IndexedProposal] = {
        val forbiddenKeywords = chosen.keywords.filterNot(k => ignoredKeywords.contains(k.key)).to(MSet)
        candidates.filter { proposal =>
          !proposal.keywords.exists(forbiddenKeywords.contains) && chosen.userId != proposal.userId
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
