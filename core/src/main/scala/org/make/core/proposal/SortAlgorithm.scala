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
import com.sksamuel.elastic4s.http.ElasticDsl.{functionScoreQuery, randomScore, scriptScore}
import com.sksamuel.elastic4s.script.ScriptDefinition
import com.sksamuel.elastic4s.searches.SearchDefinition
import com.sksamuel.elastic4s.searches.queries.funcscorer.CombineFunction
import org.make.core.proposal.indexed.ProposalElasticsearchFieldNames

sealed trait SortAlgorithm {
  def sortDefinition(request: SearchDefinition): SearchDefinition
}

trait RandomBaseAlgorithm {
  def maybeSeed: Option[Int]
}

// Sorts randomly from the given seed
final case class RandomAlgorithm(override val maybeSeed: Option[Int] = None)
    extends SortAlgorithm
    with RandomBaseAlgorithm {
  override def sortDefinition(request: SearchDefinition): SearchDefinition = {
    (
      for {
        seed  <- maybeSeed
        query <- request.query
      } yield request.query(functionScoreQuery().query(query).functions(randomScore(seed)))
    ).getOrElse(request)

  }
}
object RandomAlgorithm { val shortName: String = "random" }

// Sorts the proposals by most actor votes and then randomly from the given seed
final case class ActorVoteAlgorithm(override val maybeSeed: Option[Int] = None)
    extends SortAlgorithm
    with RandomBaseAlgorithm {
  override def sortDefinition(request: SearchDefinition): SearchDefinition = {
    val scriptActorVoteNumber = s"doc['${ProposalElasticsearchFieldNames.organisationId}'].values.size()"
    val actorVoteScript = s"$scriptActorVoteNumber > 0 ? ($scriptActorVoteNumber + 1) * 10 : 1"
    request.query.map { query =>
      maybeSeed.map { seed =>
        request
          .query(
            functionScoreQuery()
              .query(query)
              .functions(scriptScore(ScriptDefinition(script = actorVoteScript)), randomScore(seed))
              .scoreMode("sum")
              .boostMode(CombineFunction.Sum)
          )
      }.getOrElse(
        request
          .query(
            functionScoreQuery()
              .query(query)
              .functions(scriptScore(ScriptDefinition(script = actorVoteScript)))
          )
      )
    }.getOrElse(request)
  }
}
object ActorVoteAlgorithm { val shortName: String = "actorVote" }

// Filter proposals by trending equal to "controversy"
final case class ControversyAlgorithm(override val maybeSeed: Option[Int] = None)
    extends SortAlgorithm
    with RandomBaseAlgorithm {
  override def sortDefinition(request: SearchDefinition): SearchDefinition = {
    request.postFilter(ElasticApi.termQuery(ProposalElasticsearchFieldNames.trending, ControversyAlgorithm.shortName))
  }
}
object ControversyAlgorithm { val shortName: String = "controversy" }

// Filter proposals by trending equal to "popular"
final case class PopularAlgorithm(override val maybeSeed: Option[Int] = None)
    extends SortAlgorithm
    with RandomBaseAlgorithm {

  override def sortDefinition(request: SearchDefinition): SearchDefinition = {
    request.postFilter(ElasticApi.termQuery(ProposalElasticsearchFieldNames.trending, PopularAlgorithm.shortName))
  }
}
object PopularAlgorithm { val shortName: String = "popular" }

case object AlgorithmSelector {
  val sortAlgorithmsName: Seq[String] = Seq(
    RandomAlgorithm.shortName,
    ActorVoteAlgorithm.shortName,
    ControversyAlgorithm.shortName,
    PopularAlgorithm.shortName
  )

  def select(sortAlgorithm: Option[String], randomSeed: Int): Option[SortAlgorithm] = sortAlgorithm match {
    case Some(RandomAlgorithm.shortName)      => Some(RandomAlgorithm(Some(randomSeed)))
    case Some(ActorVoteAlgorithm.shortName)   => Some(ActorVoteAlgorithm(Some(randomSeed)))
    case Some(ControversyAlgorithm.shortName) => Some(ControversyAlgorithm(Some(randomSeed)))
    case Some(PopularAlgorithm.shortName)     => Some(PopularAlgorithm(Some(randomSeed)))
    case _                                    => None
  }

}
