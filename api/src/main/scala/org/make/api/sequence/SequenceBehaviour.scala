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
import org.make.api.technical.MakeRandom
import org.make.core.proposal
import org.make.core.proposal.indexed.{IndexedProposal, SequencePool, Zone}
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.sequence.{SequenceConfiguration, SpecificSequenceConfiguration}
import org.make.core.session.SessionId
import org.make.core.tag.TagId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait SequenceBehaviour {
  type SearchFunction = (
    QuestionId,
    Option[String],
    Option[SequencePool],
    proposal.SearchQuery,
    SortAlgorithm
  ) => Future[Seq[IndexedProposal]]

  val configuration: SequenceConfiguration
  val specificConfiguration: SpecificSequenceConfiguration
  val questionId: QuestionId
  val maybeSegment: Option[String]
  val sessionId: SessionId

  def newProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = Future.successful(Nil)
  def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = Future.successful(Nil)
  def fallbackProposals(currentSequenceSize: Int, search: SearchFunction): Future[Seq[IndexedProposal]] =
    Future.successful(Nil)
}

object SequenceBehaviour extends Logging {

  final case class Standard(
    override val configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) extends SequenceBehaviour {

    override val specificConfiguration: SpecificSequenceConfiguration = configuration.mainSequence

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

  final case class Consensus(
    futureConsensusThreshold: Future[Option[Double]],
    override val configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) extends SequenceBehaviour {
    override val specificConfiguration: SpecificSequenceConfiguration = configuration.popular

    override def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      futureConsensusThreshold.flatMap { consensusThreshold =>
        search(
          questionId,
          maybeSegment,
          Some(SequencePool.Tested),
          proposal.SearchQuery(filters = Some(
            proposal.SearchFilters(
              zone = Some(ZoneSearchFilter(Zone.Consensus)),
              minScoreLowerBound = consensusThreshold.map(MinScoreLowerBoundSearchFilter)
            )
          )
          ),
          RandomAlgorithm(MakeRandom.nextInt())
        )
      }
    }
  }

  final case class ZoneDefault(
    zone: Zone,
    override val configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) extends SequenceBehaviour {

    override val specificConfiguration: SpecificSequenceConfiguration = configuration.controversial

    override def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.Tested),
        proposal.SearchQuery(filters = Some(proposal.SearchFilters(zone = Some(ZoneSearchFilter(zone))))),
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
  ) extends SequenceBehaviour {

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
    tagsIds: Seq[TagId],
    override val configuration: SequenceConfiguration,
    questionId: QuestionId,
    maybeSegment: Option[String],
    sessionId: SessionId
  ) extends SequenceBehaviour {

    override val specificConfiguration: SpecificSequenceConfiguration = configuration.mainSequence

    override def newProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.New),
        proposal.SearchQuery(filters = Some(proposal.SearchFilters(tags = Some(proposal.TagsSearchFilter(tagsIds))))),
        CreationDateAlgorithm(SortOrder.Asc)
      )
    }

    override def testedProposals(search: SearchFunction): Future[Seq[IndexedProposal]] = {
      search(
        questionId,
        maybeSegment,
        Some(SequencePool.Tested),
        proposal.SearchQuery(filters = Some(proposal.SearchFilters(tags = Some(proposal.TagsSearchFilter(tagsIds))))),
        RandomAlgorithm(MakeRandom.nextInt())
      )
    }
  }
}
