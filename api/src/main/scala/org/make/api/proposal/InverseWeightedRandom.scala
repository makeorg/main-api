package org.make.api.proposal

import org.make.core.proposal.indexed._

import scala.util.Random

object InverseWeightedRandom {

  /** Returns the index result of a binary search to find @n in the discrete
    * cdf array.
    */
  def search(n: Int, cdf: Array[Int]): Int = {
    if (n > cdf.head) {
      1 + search(n - cdf.head, cdf.tail)
    } else {
      0
    }
  }

  /** Returns the cumulative density function (CDF) of @list (in simple terms,
    * the cumulative sums of the weights).
    */
  def cdf(list: Array[Int]): Array[Int] = list.map {
    var s = 0
    d =>
      {
        s += d; s
      }
  }

  def randomWeighted(list: Array[IndexedProposal]): IndexedProposal = {
    val maxCount = list.map(_.votes.map(_.count).sum).max
    val transformedList = list.map(_.votes.map(_.count).sum).map(maxCount - _)
    list(search(Random.nextInt(transformedList.sum + 1), cdf(transformedList)))
  }

}
