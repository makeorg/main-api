package org.make.api.proposal

import com.typesafe.scalalogging.StrictLogging
import org.make.core.proposal.ProposalId
import org.make.core.proposal.indexed._

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

object InverseWeightedRandom extends StrictLogging {

  /** Returns the index result of a binary search to find @n in the discrete
    * cdf array.
    */
  @tailrec
  def search(n: Int, cdf: Seq[Int], index: Int = 0): Int = {
    if (n <= cdf.head) {
      index
    } else {
      search(n - cdf.head, cdf.tail, index + 1)
    }
  }

  /** Returns the cumulative density function (CDF) of @list (in simple terms,
    * the cumulative sums of the weights).
    */
  def cdf(list: Seq[Int]): Seq[Int] = {
    var s = 0
    list.map { d =>
      s += d
      s
    }
  }

  def randomWeighted(list: Seq[IndexedProposal]): IndexedProposal = {
    val maxCount = list.map(_.votes.map(_.count).sum).max
    val transformedList = list.map(p => maxCount - p.votes.map(_.count).sum)
    list(search(Random.nextInt(transformedList.sum + 1), cdf(transformedList)))
  }

}

object SelectionAlgorithm extends StrictLogging {

  /**
    * @param targetLength          number of elements in the sequence
    * @param getSearchSpace        function that returns a list of randomly chosen proposals
    * @param getSimilarForProposal function that returns a list of similar proposal ids for a given proposal id
    * @return selected list of proposals for the sequence
    */
  def getProposalsForSequence(targetLength: Int,
                              minLength: Int,
                              getSearchSpace: Seq[ProposalId]   => Future[Seq[IndexedProposal]],
                              getSimilarForProposal: ProposalId => Future[Seq[ProposalId]],
                              getProposals: (Seq[ProposalId])   => Future[Seq[IndexedProposal]],
                              includeList: Seq[ProposalId],
                              retries: Int = 0): Future[Seq[IndexedProposal]] = {

    val potentials: Future[Seq[IndexedProposal]] =
      Future
        .sequence(includeList.map(getSimilarForProposal))
        .map(_.flatten)
        .map(_ ++ includeList)
        .flatMap(getSearchSpace)

    potentials.flatMap { proposals =>
      val allIds = includeList ++ proposals.map(_.id)
      val duplicates: Future[Map[ProposalId, Seq[ProposalId]]] = Future
        .traverse(allIds) { id =>
          getSimilarForProposal(id).map(similar => id -> similar)
        }
        .map(_.toMap)

      duplicates.flatMap { duplicates =>
        var included = includeList.toList
        var searchSpace: Seq[IndexedProposal] = proposals

        while (searchSpace.nonEmpty && included.length < targetLength) {
          included = InverseWeightedRandom.randomWeighted(searchSpace).id :: included

          val forbiddenOnes = included ++ included.foldLeft(Set.empty[ProposalId]) { (accumulator, id) =>
            accumulator ++ duplicates(id).toSet
          }

          searchSpace = searchSpace.filter(proposal => !forbiddenOnes.contains(proposal.id))
        }

        if (included.length == targetLength || retries > 3 && included.length >= minLength) {
          getProposals(included)
        } else if (retries > 3) {
          Future.successful(Seq.empty)
        } else {
          getProposalsForSequence(
            targetLength,
            minLength,
            getSearchSpace,
            getSimilarForProposal,
            getProposals,
            included,
            retries + 1
          )
        }
      }
    }
  }

}
