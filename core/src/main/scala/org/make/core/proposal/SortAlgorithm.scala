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
import org.make.core.proposal.indexed.ProposalElasticsearchFieldNames

sealed trait SortAlgorithm {
  def sortDefinition(request: SearchRequest): SearchRequest
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
object RandomAlgorithm { val shortName: String = "random" }

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
            WeightScore(50D, Some(existsQuery(ProposalElasticsearchFieldNames.tagId))),
            WeightScore(10D, Some(scriptQuery(s"doc['${ProposalElasticsearchFieldNames.organisationId}'].size() > 1"))),
            WeightScore(5D, Some(scriptQuery(s"doc['${ProposalElasticsearchFieldNames.organisationId}'].size() == 1"))),
            exponentialScore(field = ProposalElasticsearchFieldNames.createdAt, scale = "7d", origin = "now")
              .weight(30D)
              .decay(0.33D),
            randomScore(seed = seed).fieldName("id").weight(5D)
          )
          .scoreMode("sum")
          .boostMode(CombineFunction.Replace)
      )
  }
}

object TaggedFirstAlgorithm {
  val shortName: String = "taggedFirst"
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
    val scriptTagsCount = s"doc['${ProposalElasticsearchFieldNames.tagId}'].size()"
    val scriptActorVoteCount = s"doc['${ProposalElasticsearchFieldNames.organisationId}'].size()"
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

object TaggedFirstLegacyAlgorithm { val shortName: String = "taggedFirstLegacy" }

// Sorts the proposals by most actor votes and then randomly from the given seed
final case class ActorVoteAlgorithm(override val seed: Int) extends SortAlgorithm with RandomBaseAlgorithm {
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    val scriptActorVoteNumber = s"doc['${ProposalElasticsearchFieldNames.organisationId}'].size()"
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
object ActorVoteAlgorithm { val shortName: String = "actorVote" }

// Filter proposals by trending equal to "controversy"
case object ControversyAlgorithm extends SortAlgorithm {
  val shortName: String = "controversy"
  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request.postFilter(ElasticApi.termQuery(ProposalElasticsearchFieldNames.trending, ControversyAlgorithm.shortName))
  }
}

// Filter proposals by trending equal to "popular"
case object PopularAlgorithm extends SortAlgorithm {

  val shortName: String = "popular"

  override def sortDefinition(request: SearchRequest): SearchRequest = {
    request.postFilter(ElasticApi.termQuery(ProposalElasticsearchFieldNames.trending, PopularAlgorithm.shortName))
  }
}

case object AlgorithmSelector {
  val sortAlgorithmsName: Seq[String] = Seq(
    RandomAlgorithm.shortName,
    ActorVoteAlgorithm.shortName,
    ControversyAlgorithm.shortName,
    PopularAlgorithm.shortName,
    TaggedFirstLegacyAlgorithm.shortName,
    TaggedFirstAlgorithm.shortName
  )

  def select(sortAlgorithm: Option[String], randomSeed: Int): Option[SortAlgorithm] = sortAlgorithm match {
    case Some(RandomAlgorithm.shortName)            => Some(RandomAlgorithm(randomSeed))
    case Some(ActorVoteAlgorithm.shortName)         => Some(ActorVoteAlgorithm(randomSeed))
    case Some(ControversyAlgorithm.shortName)       => Some(ControversyAlgorithm)
    case Some(PopularAlgorithm.shortName)           => Some(PopularAlgorithm)
    case Some(TaggedFirstLegacyAlgorithm.shortName) => Some(TaggedFirstLegacyAlgorithm(randomSeed))
    case Some(TaggedFirstAlgorithm.shortName)       => Some(TaggedFirstAlgorithm(randomSeed))
    case _                                          => None
  }

}
