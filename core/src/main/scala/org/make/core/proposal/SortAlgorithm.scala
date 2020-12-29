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

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.ElasticApi._
import com.sksamuel.elastic4s.http.ElasticDsl.{functionScoreQuery, scriptScore}
import com.sksamuel.elastic4s.script.Script
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.queries.funcscorer.{CombineFunction, WeightScore}
import com.sksamuel.elastic4s.searches.sort.{FieldSort, ScoreSort, SortOrder}
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

/*
 * The ordering of the consultation feed must respect the following rule:
 * - tagged proposals with at least one vote from actor
 * - non tagged proposals with at least one vote from actor
 * - tagged proposals without vote from actor
 * - non tagged proposals without vote from actor
 *
 * To do so, a score is computed based on the number of actors votes and tags.
 * The higher the score, the closer to first place the proposal will be.
 * Since we want proposals with actors votes first, this number is put forward (thus the multiplication by 50).
 */
final case class TaggedFirstLegacyAlgorithm(override val seed: Int) extends SortAlgorithm with RandomBaseAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    val scriptTagsCount = s"${docField(ProposalElasticsearchFieldName.tagId)}.size()"
    val scriptActorVoteCount = s"${docField(ProposalElasticsearchFieldName.organisationId)}.size()"
    val orderingByActorVoteAndTagsCountScript =
      s"($scriptActorVoteCount > 0 && $scriptTagsCount > 0) ? ($scriptActorVoteCount * 50 + $scriptTagsCount) * 100 :" +
        s"$scriptActorVoteCount > 0 ? $scriptActorVoteCount * 100 :" +
        s"$scriptTagsCount > 0 ? $scriptTagsCount * 10 : 1"
    request.query.map { query =>
      request
        .query(
          functionScoreQuery()
            .query(query)
            .functions(
              scriptScore(Script(script = orderingByActorVoteAndTagsCountScript)),
              randomScore(seed = seed).fieldName("id")
            )
            .scoreMode("sum")
            .boostMode(CombineFunction.Sum)
        )
    }.getOrElse(request)
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
final case class ControversyAlgorithm(threshold: Double, votesCountThreshold: Int) extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request
      .sortByFieldDesc(ProposalElasticsearchFieldName.controversy.field)
      .postFilter(
        ElasticApi
          .boolQuery()
          .must(
            ElasticApi.rangeQuery(ProposalElasticsearchFieldName.controversy.field).gte(threshold),
            ElasticApi.rangeQuery(ProposalElasticsearchFieldName.votesCount.field).gte(votesCountThreshold)
          )
      )
  }
}

// Sort proposal by their top score
final case class PopularAlgorithm(votesCountThreshold: Int) extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request
      .sortByFieldDesc(ProposalElasticsearchFieldName.scoreLowerBound.field)
      .postFilter(ElasticApi.rangeQuery(ProposalElasticsearchFieldName.votesCount.field).gte(votesCountThreshold))
  }
}

// Sort proposal by their realistic score
final case class RealisticAlgorithm(threshold: Double, votesCountThreshold: Int) extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request
      .sortByFieldDesc(ProposalElasticsearchFieldName.scoreRealistic.field)
      .postFilter(
        ElasticApi
          .boolQuery()
          .must(
            ElasticApi.rangeQuery(ProposalElasticsearchFieldName.scoreRealistic.field).gte(threshold),
            ElasticApi.rangeQuery(ProposalElasticsearchFieldName.votesCount.field).gte(votesCountThreshold)
          )
      )
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
        .sortBy(ScoreSort(SortOrder.DESC) +: request.sorts)
    }.getOrElse(request)
  }
}

final case class CreationDateAlgorithm(order: SortOrder) extends SortAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest =
    request.sortBy(FieldSort(field = ProposalElasticsearchFieldName.createdAt.field, order = order))
}

sealed abstract class AlgorithmSelector(
  val value: String,
  val build: (Int, SortAlgorithmConfiguration) => SortAlgorithm
) extends StringEnumEntry

case object AlgorithmSelector extends StringEnum[AlgorithmSelector] with StringEnumKeys[AlgorithmSelector] {

  case object Random extends AlgorithmSelector("random", (randomSeed, _) => RandomAlgorithm(randomSeed))

  case object TaggedFirst extends AlgorithmSelector("taggedFirst", (randomSeed, _) => TaggedFirstAlgorithm(randomSeed))

  case object TaggedFirstLegacy
      extends AlgorithmSelector("taggedFirstLegacy", (randomSeed, _) => TaggedFirstLegacyAlgorithm(randomSeed))

  case object ActorVote extends AlgorithmSelector("actorVote", (randomSeed, _) => ActorVoteAlgorithm(randomSeed))

  case object Controversy
      extends AlgorithmSelector(
        "controversy",
        (_, conf) => ControversyAlgorithm(conf.controversyThreshold, conf.controversyVoteCountThreshold)
      )

  case object Popular
      extends AlgorithmSelector("popular", (_, conf) => PopularAlgorithm(conf.popularVoteCountThreshold))

  case object Realistic
      extends AlgorithmSelector(
        "realistic",
        (_, conf) => RealisticAlgorithm(conf.realisticThreshold, conf.realisticVoteCountThreshold)
      )

  case object B2BFirst extends AlgorithmSelector("B2BFirst", (_, _) => B2BFirstAlgorithm)

  override def values: IndexedSeq[AlgorithmSelector] = findValues

  def select(sortAlgorithm: Option[String], randomSeed: Int, conf: SortAlgorithmConfiguration): Option[SortAlgorithm] =
    sortAlgorithm.flatMap(withValueOpt).map(_.build(randomSeed, conf))

}
