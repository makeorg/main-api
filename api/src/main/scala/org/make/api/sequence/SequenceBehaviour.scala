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

import com.sksamuel.elastic4s.searches.sort.SortOrder
import grizzled.slf4j.Logging
import SelectionAlgorithm.{ExplorationSelectionAlgorithm, RandomSelectionAlgorithm, RoundRobinSelectionAlgorithm}
import org.make.api.sequence.SequenceBehaviour.ConsensusParam
import org.make.api.technical.MakeRandom
import org.make.core.proposal
import org.make.core.proposal._
import org.make.core.proposal.indexed.{IndexedProposal, SequencePool, Zone}
import org.make.core.question.QuestionId
import org.make.core.sequence._
import org.make.core.session.SessionId
import org.make.core.tag.TagId

import eu.timepit.refined.auto._

import scala.concurrent.Future

sealed trait SequenceBehaviour {
  type Configuration <: BasicSequenceConfiguration

  type SearchFunction = (
    QuestionId,
    Option[String],
    Option[SequencePool],
    proposal.SearchQuery,
    SortAlgorithm
  ) => Future[Seq[IndexedProposal]]

  val configuration: SequenceConfiguration
  def specificConfiguration: Configuration
  val questionId: QuestionId
  val maybeSegment: Option[String]
  val sessionId: SessionId

  def newProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = Future.successful(Nil)
  def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = Future.successful(Nil)
  def fallbackProposals(currentSequenceSize: Int, search: SearchFunction): Future[Seq[IndexedProposal]] =
    Future.successful(Nil)
  def selectProposals(
    includedProposals: Seq[IndexedProposal],
    newProposals: Seq[IndexedProposal],
    testedProposals: Seq[IndexedProposal]
  ): Seq[IndexedProposal]
}

object SequenceBehaviour extends Logging {
  trait RoundRobinOrRandomBehavior {
    self: SequenceBehaviour =>

    override type Configuration = SpecificSequenceConfiguration

    override def selectProposals(
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal],
      testedProposals: Seq[IndexedProposal]
    ): Seq[IndexedProposal] = {
      specificConfiguration.selectionAlgorithmName match {
        case SelectionAlgorithmName.RoundRobin =>
          RoundRobinSelectionAlgorithm.selectProposalsForSequence(
            self.specificConfiguration,
            includedProposals,
            newProposals,
            testedProposals
          )
        case _ =>
          RandomSelectionAlgorithm.selectProposalsForSequence(
            self.specificConfiguration,
            includedProposals,
            newProposals,
            testedProposals
          )
      }
    }
  }

  trait ExplorationBehavior {
    self: SequenceBehaviour =>

    override type Configuration = ExplorationSequenceConfiguration

    override def specificConfiguration: ExplorationSequenceConfiguration = self.configuration.mainSequence

    override def selectProposals(
      includedProposals: Seq[IndexedProposal],
      newProposals: Seq[IndexedProposal],
      testedProposals: Seq[IndexedProposal]
    ): Seq[IndexedProposal] = {
      ExplorationSelectionAlgorithm.selectProposalsForSequence(
        specificConfiguration,
        self.configuration.nonSequenceVotesWeight,
        includedProposals,
        newProposals,
        testedProposals,
        self.maybeSegment
      )
    }

    override def fallbackProposals(currentSequenceSize: Int, search: SearchFunction): Future[Seq[IndexedProposal]] = {
      if (currentSequenceSize < specificConfiguration.sequenceSize) {
        logger.warn(s"Sequence fallback for session ${sessionId.value} and question ${questionId.value}")
        def sortAlgorithm: SortAlgorithm =
          maybeSegment.fold[SortAlgorithm](CreationDateAlgorithm(SortOrder.Desc))(SegmentFirstAlgorithm.apply)

        search(questionId, maybeSegment, None, proposal.SearchQuery(), sortAlgorithm)
      } else {
        Future.successful(Nil)
      }
    }
  }

  final case class Standard(
    override val configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) extends SequenceBehaviour
      with ExplorationBehavior {

    override def newProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.New),
        proposal.SearchQuery(),
        CreationDateAlgorithm(SortOrder.Asc)
      )
    }

    override def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.Tested),
        proposal.SearchQuery(),
        RandomAlgorithm(MakeRandom.nextInt())
      )
    }
  }

  final case class ConsensusParam(top20ConsensusThreshold: Option[Double])

  final case class Consensus(
    consensusParam: ConsensusParam,
    override val configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) extends SequenceBehaviour
      with RoundRobinOrRandomBehavior {
    override val specificConfiguration: SpecificSequenceConfiguration = configuration.popular

    override def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.Tested),
        proposal.SearchQuery(filters = Some(
          proposal.SearchFilters(
            zone = Some(ZoneSearchFilter(Zone.Consensus)),
            minScoreLowerBound = consensusParam.top20ConsensusThreshold.map(MinScoreLowerBoundSearchFilter)
          )
        )
        ),
        RandomAlgorithm(MakeRandom.nextInt())
      )
    }
  }

  final case class Controversy(
    override val configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) extends SequenceBehaviour
      with RoundRobinOrRandomBehavior {

    override val specificConfiguration: SpecificSequenceConfiguration = configuration.controversial

    override def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.Tested),
        proposal.SearchQuery(filters = Some(proposal.SearchFilters(zone = Some(ZoneSearchFilter(Zone.Controversy))))),
        RandomAlgorithm(MakeRandom.nextInt())
      )
    }
  }

  final case class Keyword(
    keyword: ProposalKeywordKey,
    override val configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) extends SequenceBehaviour
      with RoundRobinOrRandomBehavior {

    override val specificConfiguration: SpecificSequenceConfiguration = configuration.keyword

    override def newProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.New),
        proposal.SearchQuery(filters = Some(proposal.SearchFilters(keywords = Some(KeywordsSearchFilter(Seq(keyword)))))
        ),
        CreationDateAlgorithm(SortOrder.Asc)
      )
    }

    override def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.Tested),
        proposal.SearchQuery(filters = Some(proposal.SearchFilters(keywords = Some(KeywordsSearchFilter(Seq(keyword)))))
        ),
        RandomAlgorithm(MakeRandom.nextInt())
      )
    }
  }

  final case class Tags(
    tagsIds: Option[Seq[TagId]],
    override val configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) extends SequenceBehaviour
      with ExplorationBehavior {

    override def newProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.New),
        proposal.SearchQuery(filters = Some(proposal.SearchFilters(tags = tagsIds.map(proposal.TagsSearchFilter.apply)))
        ),
        CreationDateAlgorithm(SortOrder.Asc)
      )
    }

    override def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.Tested),
        proposal.SearchQuery(filters = Some(proposal.SearchFilters(tags = tagsIds.map(proposal.TagsSearchFilter.apply)))
        ),
        RandomAlgorithm(MakeRandom.nextInt())
      )
    }
  }
}

trait SequenceBehaviourProvider[T] {
  def behaviour(
    param: T,
    configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ): SequenceBehaviour
}

object SequenceBehaviourProvider {

  def apply[T](implicit bp: SequenceBehaviourProvider[T]): SequenceBehaviourProvider[T] = bp

  implicit val standard: SequenceBehaviourProvider[Unit] = (
    _: Unit,
    configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) => SequenceBehaviour.Standard(configuration, questionId, maybeSegment, sessionId)

  implicit val consensus: SequenceBehaviourProvider[ConsensusParam] = (
    consensusParam: ConsensusParam,
    configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) => SequenceBehaviour.Consensus(consensusParam, configuration, questionId, maybeSegment, sessionId)

  implicit val controversy: SequenceBehaviourProvider[Zone.Controversy.type] =
    (
      _: Zone.Controversy.type,
      configuration: SequenceConfiguration,
      questionId: QuestionId,
      maybeSegment: Option[String],
      sessionId: SessionId
    ) => SequenceBehaviour.Controversy(configuration, questionId, maybeSegment, sessionId)

  implicit val keyword: SequenceBehaviourProvider[ProposalKeywordKey] = (
    keyword: ProposalKeywordKey,
    configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) => SequenceBehaviour.Keyword(keyword, configuration, questionId, maybeSegment, sessionId)

  implicit val tags: SequenceBehaviourProvider[Option[Seq[TagId]]] = (
    tagsIds: Option[Seq[TagId]],
    configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) => SequenceBehaviour.Tags(tagsIds, configuration, questionId, maybeSegment, sessionId)

}
