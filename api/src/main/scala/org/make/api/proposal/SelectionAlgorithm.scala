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

import com.typesafe.scalalogging.StrictLogging
import org.make.api.sequence.SequenceConfiguration
import org.make.core.idea.IdeaId
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

trait SelectionAlgorithmComponent {
  val selectionAlgorithm: SelectionAlgorithm
}

trait SelectionAlgorithm {
  var random: Random = Random

  def selectProposalsForSequence(targetLength: Int,
                                 sequenceConfiguration: SequenceConfiguration,
                                 proposals: Seq[Proposal],
                                 votedProposals: Seq[ProposalId],
                                 includeList: Seq[ProposalId]): Seq[ProposalId]
}

trait DefaultSelectionAlgorithmComponent extends SelectionAlgorithmComponent with StrictLogging {

  override val selectionAlgorithm: DefaultSelectionAlgorithm = new DefaultSelectionAlgorithm

  def isSameIdea(ideaOption1: Option[IdeaId], ideaOption2: Option[IdeaId]): Boolean = {
    (ideaOption1, ideaOption2) match {
      case (Some(ideaId1), Some(ideaId2)) => ideaId1 == ideaId2
      case _                              => false
    }
  }

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
            !includedIdeasToExclude.exists(excludedIdea => p.idea.contains(excludedIdea)) &&
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
          !newProposalsToExclude.contains(p.proposalId) && !newIdeasToExclude
            .exists(excludedIdea => p.idea.contains(excludedIdea))
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
                !isSameIdea(p.idea, chosen.idea) &&
                p.author != chosen.author
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
        proposals.map(p => ScoredProposal(p, ProposalScorerHelper.sampleScore(p)))

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

    def chooseChampion(proposals: Seq[Proposal]): Proposal = {
      val scoredProposal: Seq[ScoredProposal] = proposals.map(p => ScoredProposal(p, ProposalScorerHelper.topScore(p)))
      scoredProposal.maxBy(_.score).proposal
    }

    case class ScoredIdeaId(ideaId: IdeaId, score: Double)

    def selectIdeasWithChampions(champions: Map[IdeaId, Proposal], count: Int): Seq[IdeaId] = {
      val scoredIdea: Seq[ScoredIdeaId] =
        champions.toSeq.map { case (i, p) => ScoredIdeaId(i, ProposalScorerHelper.sampleScore(p)) }
      scoredIdea.sortBy(-_.score).take(count).map(_.ideaId)
    }

    def selectControversialIdeasWithChampions(champions: Map[IdeaId, Proposal], count: Int): Seq[IdeaId] = {
      val scoredIdea: Seq[ScoredIdeaId] =
        champions.toSeq.map { case (i, p) => ScoredIdeaId(i, ProposalScorerHelper.sampleControversy(p)) }
      scoredIdea.sortBy(-_.score).take(count).map(_.ideaId)
    }

    def chooseTestedProposals(sequenceConfiguration: SequenceConfiguration,
                              availableProposals: Seq[Proposal],
                              testedProposalCount: Int): Seq[Proposal] = {

      // filter proposals
      val testedProposals: Seq[Proposal] = availableProposals.filter { proposal =>
        val votes: Int = proposal.votes.map(_.count).sum
        val engagementRate: Double = ProposalScorerHelper.engagementUpperBound(proposal)
        val scoreRate: Double = ProposalScorerHelper.scoreUpperBound(proposal)
        val controversyRate: Double = ProposalScorerHelper.controversyUpperBound(proposal)
        (votes >= sequenceConfiguration.newProposalsVoteThreshold
        && engagementRate > sequenceConfiguration.testedProposalsEngagementThreshold
        && (scoreRate > sequenceConfiguration.testedProposalsScoreThreshold
        || controversyRate > sequenceConfiguration.testedProposalsControversyThreshold))
      }

      // group by idea
      val ideas: Map[IdeaId, Seq[Proposal]] =
        testedProposals.groupBy(p => p.idea.getOrElse(IdeaId(p.proposalId.value)))

      // select ideas
      val selectedIdeas: Seq[IdeaId] = if (sequenceConfiguration.ideaCompetitionEnabled) {
        val champions: Map[IdeaId, Proposal] = ideas.mapValues(chooseChampion(_))
        if (random.nextFloat() < sequenceConfiguration.ideaCompetitionControversialRatio) {
          selectControversialIdeasWithChampions(champions, sequenceConfiguration.ideaCompetitionControversialCount)
        } else {
          selectIdeasWithChampions(champions, sequenceConfiguration.ideaCompetitionTargetCount)
        }
      } else {
        ideas.keys.toSeq
      }

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

      // and finally pick the requested number of proposals
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
              !excludeIdeas.exists(excludedIdea => p.idea.contains(excludedIdea))
        ),
        targetLength - sequence.size,
        OldestProposalChooser
      ).map(_.proposalId)
    }
  }
}
