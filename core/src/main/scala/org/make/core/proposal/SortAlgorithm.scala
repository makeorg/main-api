package org.make.core.proposal

import com.sksamuel.elastic4s.http.ElasticDsl.{functionScoreQuery, randomScore}
import com.sksamuel.elastic4s.searches.SearchDefinition

sealed trait SortAlgorithm {
  val shortName: String
  def sortDefinition(request: SearchDefinition): SearchDefinition
}

case class RandomAlgorithm(maybeSeed: Option[Int] = None) extends SortAlgorithm {
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
