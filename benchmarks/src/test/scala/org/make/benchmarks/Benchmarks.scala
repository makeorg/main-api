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

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.infra.Blackhole

import org.make.api.ProposalsUtils
import org.make.api.sequence.SelectionAlgorithm.ExplorationSelectionAlgorithm
import org.make.core.sequence.{ExplorationSequenceConfiguration, ExplorationSequenceConfigurationId}
import org.make.core.proposal.indexed.IndexedProposal

object States {

  private val (newProposals, testedProposals): (Seq[IndexedProposal], Seq[IndexedProposal]) =
    ProposalsUtils.getNewAndTestedProposals("sequence_simulation.csv")

  @State(Scope.Thread)
  class MyState {
    val configuration: ExplorationSequenceConfiguration =
      ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId("bandit"))
    val nonSequenceRatio: Double = 0.5
    val newProposals = States.newProposals
    val testedProposals = States.testedProposals
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
}
