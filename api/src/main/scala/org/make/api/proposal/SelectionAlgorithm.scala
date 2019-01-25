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
import io.circe.{Decoder, Encoder, Json}
import org.make.api.sequence.SequenceConfiguration
import org.make.api.technical.MakeRandom
import org.make.core.DateHelper._
import org.make.core.idea.IdeaId
import org.make.core.proposal._
import org.make.core.proposal.indexed.IndexedProposal

import scala.annotation.tailrec
import scala.math.ceil
import scala.util.Random

trait ProposalChooser {
  def choose(proposals: Seq[IndexedProposal]): IndexedProposal
}

object OldestProposalChooser extends ProposalChooser {
  override def choose(proposals: Seq[IndexedProposal]): IndexedProposal = {
    proposals.minBy(_.createdAt)
  }
}

object RoundRobin extends ProposalChooser {
  override def choose(proposals: Seq[IndexedProposal]): IndexedProposal = {
    proposals.minBy(_.votesCount)
  }
}

trait RandomProposalChooser extends ProposalChooser {
  var random: Random = MakeRandom.random

  protected def proposalWeight(proposal: IndexedProposal): Double

  @tailrec
  private final def search(proposals: Seq[IndexedProposal],
                           choice: Double,
                           accumulatedSum: Double = 0): IndexedProposal = {
    val accumulatedSumNew = accumulatedSum + proposalWeight(proposals.head)
    if (choice <= accumulatedSumNew) {
      proposals.head
    } else {
      search(proposals.tail, choice, accumulatedSumNew)
    }
  }

  final def choose(proposals: Seq[IndexedProposal]): IndexedProposal = {
    val weightSum: Double = proposals.map(proposalWeight).sum
    val choice: Double = random.nextDouble() * weightSum
    search(proposals, choice)
  }
}

object InverseWeightedRandom extends RandomProposalChooser {
  override def proposalWeight(proposal: IndexedProposal): Double = {
    1 / (proposal.votes.map(_.countVerified).sum + 1).toDouble
  }
}

object SoftMinRandom extends RandomProposalChooser {
  override def proposalWeight(proposal: IndexedProposal): Double = {
    Math.exp(-1 * proposal.votes.map(_.countVerified).sum)
  }
}

object UniformRandom extends ProposalChooser with StrictLogging {
  var random: Random = MakeRandom.random

  def choose(proposals: Seq[IndexedProposal]): IndexedProposal = {
    proposals(random.nextInt(proposals.length))
  }

}

sealed trait SelectionAlgorithmName { val shortName: String }
object SelectionAlgorithmName {
  final case object Bandit extends SelectionAlgorithmName { override val shortName: String = "Bandit" }
  final case object RoundRobin extends SelectionAlgorithmName { override val shortName: String = "RoundRobin" }

  val selectionAlgorithms: Map[String, SelectionAlgorithmName] =
    Map(Bandit.shortName -> Bandit, RoundRobin.shortName -> RoundRobin)

  implicit val encoder: Encoder[SelectionAlgorithmName] = (name: SelectionAlgorithmName) =>
    Json.fromString(name.shortName)
  implicit val decoder: Decoder[SelectionAlgorithmName] =
    Decoder.decodeString.emap(
      name => selectionAlgorithms.get(name).map(Right.apply).getOrElse(Left(s"$name is not a SelectionAlgorithmName"))
    )
}

trait SelectionAlgorithmComponent {
  val banditSelectionAlgorithm: SelectionAlgorithm
  val roundRobinSelectionAlgorithm: SelectionAlgorithm
}

trait SelectionAlgorithm {
  var random: Random = MakeRandom.random

  def name: SelectionAlgorithmName

  def selectProposalsForSequence(sequenceConfiguration: SequenceConfiguration,
                                 includedProposals: Seq[IndexedProposal],
                                 newProposals: Seq[IndexedProposal],
                                 testedProposals: Seq[IndexedProposal],
                                 votedProposals: Seq[ProposalId]): Seq[IndexedProposal]
}

trait DefaultSelectionAlgorithmComponent extends SelectionAlgorithmComponent with StrictLogging {

  override val banditSelectionAlgorithm: BanditSelectionAlgorithm = new BanditSelectionAlgorithm
  override val roundRobinSelectionAlgorithm: RoundRobinSelectionAlgorithm = new RoundRobinSelectionAlgorithm

  def isSameIdea(ideaOption1: Option[IdeaId], ideaOption2: Option[IdeaId]): Boolean = {
    (ideaOption1, ideaOption2) match {
      case (Some(ideaId1), Some(ideaId2)) => ideaId1 == ideaId2
      case _                              => false
    }
  }

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
    def selectProposalsForSequence(sequenceConfiguration: SequenceConfiguration,
                                   includedProposals: Seq[IndexedProposal],
                                   newProposals: Seq[IndexedProposal],
                                   testedProposals: Seq[IndexedProposal],
                                   votedProposals: Seq[ProposalId]): Seq[IndexedProposal] = {

      def uniqueIdeaIdForProposal(proposal: IndexedProposal): IdeaId =
        IdeaId(proposal.id.value)

      // fetch included proposals and exclude same idea
      var sequence: Seq[IndexedProposal] = includedProposals
      var distinctProposals: Set[ProposalId] = (votedProposals ++ includedProposals.map(_.id)).toSet
      var distinctIdeas: Set[IdeaId] = includedProposals.map(p => p.ideaId.getOrElse(uniqueIdeaIdForProposal(p))).toSet

      // distinct proposals by distinct ideas
      def filterDistinct(proposals: Seq[IndexedProposal],
                         proposalIds: Set[ProposalId] = distinctProposals,
                         ideaIds: Set[IdeaId] = distinctIdeas): Map[IdeaId, Seq[IndexedProposal]] =
        proposals
          .groupBy(p => p.ideaId.getOrElse(uniqueIdeaIdForProposal(p)))
          .filterKeys(ideaId => !ideaIds.contains(ideaId))
          .mapValues(_.filterNot(p => proposalIds.contains(p.id)))
          .filterNot {
            case (_, proposalsList) => proposalsList.isEmpty
          }

      def includeProposalToSequence(proposals: Seq[IndexedProposal]): Seq[IndexedProposal] = {
        sequence ++= proposals
        distinctProposals ++= proposals.map(_.id).toSet
        distinctIdeas ++= proposals.map(p => p.ideaId.getOrElse(uniqueIdeaIdForProposal(p))).toSet
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
          chooseTestedProposals(sequenceConfiguration, filterDistinct(testedProposals), testedSize)
        )

      buildSequence(includedProposals, newIncludedProposals, testedIncludedProposals)
    }

    /**
      * Build the sequence
      *
      * first: included proposals
      * then: most engaging tested proposals
      * finally: randomized new + tested proposals
      *
      */
    def buildSequence(includedProposals: Seq[IndexedProposal],
                      newIncludedProposals: Seq[IndexedProposal],
                      testedIncludedProposals: Seq[IndexedProposal]): Seq[IndexedProposal] = {

      // build sequence
      if (includedProposals.isEmpty && testedIncludedProposals.nonEmpty) {
        // pick most engaging
        val sortedTestedProposal: Seq[IndexedProposal] =
          testedIncludedProposals.sortBy(p => -1 * ProposalScorerHelper.engagement(p.votes))
        Seq(sortedTestedProposal.head) ++ MakeRandom.random.shuffle(newIncludedProposals ++ sortedTestedProposal.tail)
      } else if (includedProposals.nonEmpty && testedIncludedProposals.nonEmpty) {
        includedProposals ++ MakeRandom.random.shuffle(newIncludedProposals ++ testedIncludedProposals)
      } else {
        includedProposals ++ MakeRandom.random.shuffle(newIncludedProposals)
      }
    }

    @tailrec
    final def chooseProposals(proposals: Seq[IndexedProposal],
                              count: Int,
                              algorithm: ProposalChooser,
                              aggregator: Seq[IndexedProposal] = Seq.empty): Seq[IndexedProposal] = {
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

    case class ScoredProposal(proposal: IndexedProposal, score: Double)

    /*
     * Chooses the top proposals of each cluster according to the bandit algorithm
     * Keep at least 3 proposals per ideas
     * Then keep the top quartile
     */
    def chooseProposalBandit(sequenceConfiguration: SequenceConfiguration,
                             proposals: Seq[IndexedProposal]): IndexedProposal = {

      val proposalsScored: Seq[ScoredProposal] =
        proposals.map(p => ScoredProposal(p, ProposalScorerHelper.sampleTopScore(p.votes)))

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

    def chooseChampion(proposals: Seq[IndexedProposal]): IndexedProposal = {
      val scoredProposal: Seq[ScoredProposal] =
        proposals.map(p => ScoredProposal(p, ProposalScorerHelper.topScore(p.votes)))
      scoredProposal.maxBy(_.score).proposal
    }

    case class ScoredIdeaId(ideaId: IdeaId, score: Double)

    def selectIdeasWithChampions(champions: Map[IdeaId, IndexedProposal], count: Int): Seq[IdeaId] = {
      val scoredIdea: Seq[ScoredIdeaId] =
        champions.toSeq.map { case (i, p) => ScoredIdeaId(i, ProposalScorerHelper.sampleTopScore(p.votes)) }
      scoredIdea.sortBy(-_.score).take(count).map(_.ideaId)
    }

    def selectControversialIdeasWithChampions(champions: Map[IdeaId, IndexedProposal], count: Int): Seq[IdeaId] = {
      val scoredIdea: Seq[ScoredIdeaId] =
        champions.toSeq.map { case (i, p) => ScoredIdeaId(i, ProposalScorerHelper.sampleControversy(p.votes)) }
      scoredIdea.sortBy(-_.score).take(count).map(_.ideaId)
    }

    def chooseTestedProposals(sequenceConfiguration: SequenceConfiguration,
                              testedProposalsByIdea: Map[IdeaId, Seq[IndexedProposal]],
                              testedProposalCount: Int): Seq[IndexedProposal] = {
      // select ideas
      val selectedIdeas: Seq[IdeaId] = if (sequenceConfiguration.ideaCompetitionEnabled) {
        val champions: Map[IdeaId, IndexedProposal] = testedProposalsByIdea.mapValues(chooseChampion)
        val topIdeas: Seq[IdeaId] =
          selectIdeasWithChampions(champions, sequenceConfiguration.ideaCompetitionTargetCount)
        val topControversial: Seq[IdeaId] =
          selectControversialIdeasWithChampions(champions, sequenceConfiguration.ideaCompetitionControversialCount)
        topIdeas ++ topControversial
      } else {
        testedProposalsByIdea.keys.toSeq
      }

      // pick one proposal for each idea
      val selectedProposals: Seq[IndexedProposal] = testedProposalsByIdea
        .filterKeys(selectedIdeas.contains)
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
    def selectProposalsForSequence(sequenceConfiguration: SequenceConfiguration,
                                   includedProposals: Seq[IndexedProposal],
                                   newProposals: Seq[IndexedProposal],
                                   testedProposals: Seq[IndexedProposal],
                                   votedProposals: Seq[ProposalId]): Seq[IndexedProposal] = {

      val proposalsPool: Seq[IndexedProposal] =
        (newProposals ++ testedProposals).filterNot(p => votedProposals.contains(p.id))

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
    def buildSequence(includedProposals: Seq[IndexedProposal],
                      proposals: Seq[IndexedProposal]): Seq[IndexedProposal] = {

      // build sequence
      includedProposals ++ MakeRandom.random.shuffle(proposals)
    }

    @tailrec
    final def chooseProposals(proposals: Seq[IndexedProposal],
                              count: Int,
                              algorithm: ProposalChooser,
                              aggregator: Seq[IndexedProposal] = Seq.empty): Seq[IndexedProposal] = {
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
}
