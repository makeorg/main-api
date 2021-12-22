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

package org.make.core.proposal

import com.sksamuel.elastic4s.ElasticApi._
import com.sksamuel.elastic4s.ElasticDsl.{functionScoreQuery, scriptScore}
import com.sksamuel.elastic4s.requests.script.Script
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.collapse.CollapseRequest
import com.sksamuel.elastic4s.requests.searches.queries.InnerHit
import com.sksamuel.elastic4s.requests.searches.queries.funcscorer.{CombineFunction, WeightScore}
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, ScoreSort, SortOrder}
import enumeratum.values.{StringEnum, StringEnumEntry}
import org.make.core.proposal.indexed.ProposalElasticsearchFieldName
import org.make.core.technical.enumeratum.EnumKeys.StringEnumKeys
import org.make.core.user.UserType

sealed trait SortAlgorithm {
  def sortDefinition(request: SearchRequest): SearchRequest
  protected def docField(value: ProposalElasticsearchFieldName): String = s"doc['${value.field}']"
}

trait RandomBaseAlgorithm {
  def seed: Int
}

// Sorts randomly from the given seed
final case class RandomAlgorithm(override val seed: Int) extends SortAlgorithm with RandomBaseAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request.query.map { query =>
      request.query(functionScoreQuery().query(query).functions(randomScore(seed = seed).fieldName("id")))
    }.getOrElse(request)

  }
}

/*
 * This algorithm aims to show hot quality content to the user.
 * Which means:
 * - tagged proposals will be chosen in priority
 * - Recent proposals will have a priority boost
 * - Proposals on which organisations have voted will be boosted
 */
final case class TaggedFirstAlgorithm(override val seed: Int) extends SortAlgorithm with RandomBaseAlgorithm {

  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request
      .query(
        functionScoreQuery()
          .query(request.query.getOrElse(matchAllQuery()))
          .functions(
            WeightScore(50d, Some(existsQuery(ProposalElasticsearchFieldName.tagId.field))),
            WeightScore(
              10d,
              Some(scriptQuery(s"${docField(ProposalElasticsearchFieldName.organisationId)}.size() > 1"))
            ),
            WeightScore(
              5d,
              Some(scriptQuery(s"${docField(ProposalElasticsearchFieldName.organisationId)}.size() == 1"))
            ),
            exponentialScore(field = ProposalElasticsearchFieldName.createdAt.field, scale = "7d", origin = "now")
              .weight(30d)
              .decay(0.33d),
            randomScore(seed = seed).fieldName("id").weight(5d)
          )
          .scoreMode("sum")
          .boostMode(CombineFunction.Replace)
      )
  }
}

// Sorts the proposals by most actor votes and then randomly from the given seed
final case class ActorVoteAlgorithm(override val seed: Int) extends SortAlgorithm with RandomBaseAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    val scriptActorVoteNumber = s"${docField(ProposalElasticsearchFieldName.organisationId)}.size()"
    val actorVoteScript = s"$scriptActorVoteNumber > 0 ? ($scriptActorVoteNumber + 1) * 10 : 1"
    request.query.map { query =>
      request
        .query(
          functionScoreQuery()
            .query(query)
            .functions(scriptScore(Script(script = actorVoteScript)), randomScore(seed = seed).fieldName("id"))
            .scoreMode("sum")
            .boostMode(CombineFunction.Sum)
        )
    }.getOrElse(request)
  }
}

// Sort proposal by their controversy score
case object ControversyAlgorithm extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request.sortByFieldDesc(ProposalElasticsearchFieldName.controversyLowerBound.field)
  }
}

// Sort proposal by their top score
case object PopularAlgorithm extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request.sortByFieldDesc(ProposalElasticsearchFieldName.scoreLowerBound.field)
  }
}

case object B2BFirstAlgorithm extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    val userTypeScript =
      s"""${docField(ProposalElasticsearchFieldName.authorUserType)}.value == \"${UserType.UserTypeUser.value}\" ? 1 : 100"""
    request.query.map { query =>
      request
        .query(
          functionScoreQuery()
            .query(query)
            .functions(scriptScore(Script(script = userTypeScript)))
            .boostMode(CombineFunction.Replace)
        )
        .sortBy(ScoreSort(SortOrder.Desc) +: request.sorts)
    }.getOrElse(request)
  }
}

final case class CreationDateAlgorithm(order: SortOrder) extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest =
    request.sortBy(FieldSort(field = ProposalElasticsearchFieldName.createdAt.field, order = order))
}

final case class SegmentFirstAlgorithm(segment: String) extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    val segmentField = docField(ProposalElasticsearchFieldName.segment)
    val pointsIfMatchesSegmentScript =
      s"""$segmentField.size() > 0 && $segmentField.value == "$segment" ? 10 : 1"""
    request.query.map { query =>
      request.query(
        functionScoreQuery()
          .query(query)
          .functions(
            scriptScore(Script(pointsIfMatchesSegmentScript)),
            gaussianScore(ProposalElasticsearchFieldName.createdAt.field, "now", "7d")
              .decay(0.8d)
          )
          .scoreMode("sum")
          .boostMode(CombineFunction.Replace)
      )
    }.getOrElse(request)
  }
}

case object Featured extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    val sort = FieldSort(field = ProposalElasticsearchFieldName.createdAt.field, order = SortOrder.Desc)
    request
      .collapse(
        CollapseRequest(ProposalElasticsearchFieldName.userId.field).inner(InnerHit("last").sortBy(sort).size(1))
      )
      .sortBy(sort)
  }
}

sealed abstract class AlgorithmSelector(val value: String, val build: Int => SortAlgorithm) extends StringEnumEntry

case object AlgorithmSelector extends StringEnum[AlgorithmSelector] with StringEnumKeys[AlgorithmSelector] {

  case object Random extends AlgorithmSelector("random", randomSeed => RandomAlgorithm(randomSeed))

  case object TaggedFirst extends AlgorithmSelector("taggedFirst", randomSeed => TaggedFirstAlgorithm(randomSeed))

  case object ActorVote extends AlgorithmSelector("actorVote", randomSeed => ActorVoteAlgorithm(randomSeed))

  case object Controversy extends AlgorithmSelector("controversy", _ => ControversyAlgorithm)

  case object Popular extends AlgorithmSelector("popular", _ => PopularAlgorithm)

  case object B2BFirst extends AlgorithmSelector("B2BFirst", _ => B2BFirstAlgorithm)

  override def values: IndexedSeq[AlgorithmSelector] = findValues

  def select(sortAlgorithm: Option[String], randomSeed: Int): Option[SortAlgorithm] =
    sortAlgorithm.flatMap(withValueOpt).map(_.build(randomSeed))

}
