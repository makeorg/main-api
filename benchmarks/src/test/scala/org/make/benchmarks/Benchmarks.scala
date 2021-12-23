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
package org.make.benchmarks

import org.make.api.ProposalsUtils
import org.make.api.proposal.ProposalScorer
import org.make.api.proposal.ProposalScorer.VotesCounter.SequenceVotesCounter
import org.make.api.sequence.SelectionAlgorithm.ExplorationSelectionAlgorithm
import org.make.core.proposal.indexed.{IndexedProposal, IndexedScores}
import org.make.core.sequence.{
  ExplorationSequenceConfiguration,
  ExplorationSequenceConfigurationId,
  SequenceConfiguration
}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import java.util.concurrent.TimeUnit

object States {

  private val (newProposals, testedProposals): (Seq[IndexedProposal], Seq[IndexedProposal]) =
    ProposalsUtils.getNewAndTestedProposals("sequence_simulation.csv")

  private val scenariosProposal: Seq[IndexedProposal] =
    ProposalsUtils.getProposalsFromCsv("scorer_simulation.csv").take(3).sortBy(_.votes.map(_.countVerified).sum)

  @State(Scope.Thread)
  class MyState {
    val configuration: ExplorationSequenceConfiguration =
      ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId("bandit"))
    val nonSequenceRatio: Double = 0.5
    val newProposals: Seq[IndexedProposal] = States.newProposals
    val testedProposals: Seq[IndexedProposal] = States.testedProposals
  }

  @State(Scope.Thread)
  class ScorerState {
    val configuration: SequenceConfiguration = SequenceConfiguration.default
    val nonSequenceRatio: Double = 0.5
    val (zeroVotesProposal, standardVotesProposal, highVotesProposal) = States.scenariosProposal match {
      case Seq(zero, standard, high) => (zero, standard, high)
      case _                         => throw new IllegalStateException("Strictly 3 scenarios proposal needed for scorer benchmarks.")
    }
  }
}

@BenchmarkMode(Array(Mode.Throughput))
@Measurement(iterations = 8, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 8, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(value = 2, jvmArgsAppend = Array())
@Threads(value = 1)
@OutputTimeUnit(TimeUnit.SECONDS)
class Benchmarks {

  @Benchmark
  def sequenceAlgorithm(state: States.MyState, blackhole: Blackhole): Unit = {
    val chosenSequence = ExplorationSelectionAlgorithm.selectProposalsForSequence(
      state.configuration,
      state.nonSequenceRatio,
      Seq.empty,
      state.newProposals,
      state.testedProposals,
      None
    )
    blackhole.consume(chosenSequence)
  }

  @Benchmark
  def scorerZeroVotesScore(state: States.ScorerState, blackhole: Blackhole): Unit = {
    val scorer = ProposalScorer(state.zeroVotesProposal.votes, SequenceVotesCounter, state.nonSequenceRatio)
    blackhole.consume(IndexedScores(scorer))
  }

  @Benchmark
  def scorerStandardVotesScore(state: States.ScorerState, blackhole: Blackhole): Unit = {
    val scorer = ProposalScorer(state.standardVotesProposal.votes, SequenceVotesCounter, state.nonSequenceRatio)
    blackhole.consume(IndexedScores(scorer))
  }

  @Benchmark
  def scorerHighVotesScore(state: States.ScorerState, blackhole: Blackhole): Unit = {
    val scorer = ProposalScorer(state.highVotesProposal.votes, SequenceVotesCounter, state.nonSequenceRatio)
    blackhole.consume(IndexedScores(scorer))
  }

  @Benchmark
  def scorerZeroVotesPool(state: States.ScorerState, blackhole: Blackhole): Unit = {
    val scorer = ProposalScorer(state.zeroVotesProposal.votes, SequenceVotesCounter, state.nonSequenceRatio)
    blackhole.consume(scorer.pool(state.configuration, state.zeroVotesProposal.status))
  }

  @Benchmark
  def scorerStandardVotesPool(state: States.ScorerState, blackhole: Blackhole): Unit = {
    val scorer = ProposalScorer(state.standardVotesProposal.votes, SequenceVotesCounter, state.nonSequenceRatio)
    blackhole.consume(scorer.pool(state.configuration, state.standardVotesProposal.status))
  }

  @Benchmark
  def scorerHighVotesPool(state: States.ScorerState, blackhole: Blackhole): Unit = {
    val scorer = ProposalScorer(state.highVotesProposal.votes, SequenceVotesCounter, state.nonSequenceRatio)
    blackhole.consume(scorer.pool(state.configuration, state.highVotesProposal.status))
  }
}
