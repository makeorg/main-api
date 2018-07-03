package org.make.core.proposal

import com.sksamuel.elastic4s.http.ElasticDsl.{functionScoreQuery, randomScore, scriptScore}
import com.sksamuel.elastic4s.script.ScriptDefinition
import com.sksamuel.elastic4s.searches.SearchDefinition
import org.elasticsearch.common.lucene.search.function.CombineFunction
import org.make.core.proposal.indexed.ProposalElasticsearchFieldNames

sealed trait SortAlgorithm {
  val shortName: String
  def sortDefinition(request: SearchDefinition): SearchDefinition
}

trait RandomBaseAlgorithm {
  def maybeSeed: Option[Int]
}

final case class RandomAlgorithm(override val maybeSeed: Option[Int] = None)
    extends SortAlgorithm
    with RandomBaseAlgorithm {
  override val shortName: String = "random"

  override def sortDefinition(request: SearchDefinition): SearchDefinition = {
    (
      for {
        seed  <- maybeSeed
        query <- request.query
      } yield request.query(functionScoreQuery().query(query).scorers(randomScore(seed)))
    ).getOrElse(request)

  }
}

final case class ActorVoteAlgorithm(override val maybeSeed: Option[Int] = None)
    extends SortAlgorithm
    with RandomBaseAlgorithm {
  override val shortName: String = "actorVote"

  override def sortDefinition(request: SearchDefinition): SearchDefinition = {
    val scriptActorVoteNumber = s"doc['${ProposalElasticsearchFieldNames.organisationId}'].values.size()"
    val actorVoteScript = s"$scriptActorVoteNumber > 0 ? ($scriptActorVoteNumber + 1) * 10 : 1"
    request.query.map { query =>
      maybeSeed.map { seed =>
        request
          .query(
            functionScoreQuery()
              .query(query)
              .scorers(scriptScore(ScriptDefinition(script = actorVoteScript)), randomScore(seed))
              .scoreMode("sum")
              .boostMode(CombineFunction.SUM)
          )
      }.getOrElse(
        request
          .query(
            functionScoreQuery()
              .query(query)
              .scorers(scriptScore(ScriptDefinition(script = actorVoteScript)))
          )
      )
    }.getOrElse(request)
  }
}

case object AlgorithmSelector {
  def select(sortAlgorithm: Option[String], randomSeed: Int): Option[SortAlgorithm] = sortAlgorithm match {
    case Some(name) if name == RandomAlgorithm().shortName    => Some(RandomAlgorithm(Some(randomSeed)))
    case Some(name) if name == ActorVoteAlgorithm().shortName => Some(ActorVoteAlgorithm(Some(randomSeed)))
    case _                                                    => None
  }

}
