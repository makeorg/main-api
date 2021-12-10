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

package org.make.api

import scala.io.Source
import com.github.tototoshi.csv.CSVReader
import org.make.api.TestUtils
import org.make.core.proposal.{ProposalId, ProposalKeywordKey, QualificationKey, VoteKey}
import java.time.ZonedDateTime
import org.make.core.proposal.VoteKey.{Agree, Disagree, Neutral}
import org.make.core.user.UserId
import org.make.core.proposal.QualificationKey.{
  DoNotCare,
  DoNotUnderstand,
  Doable,
  Impossible,
  LikeIt,
  NoOpinion,
  NoWay,
  PlatitudeAgree,
  PlatitudeDisagree
}
import org.make.core.proposal.indexed.{IndexedProposal, IndexedProposalKeyword, IndexedQualification, IndexedVote}

object ProposalsUtils {

  private def parseVote(
    values: Map[String, String],
    key: VoteKey,
    qualifications: Seq[(QualificationKey, String)]
  ): IndexedVote = {
    IndexedVote(
      key = key,
      count = values(s"vote_verified_${key.value}").toInt,
      countVerified = values(s"vote_verified_${key.value}").toInt,
      countSequence = values(s"vote_sequence_${key.value}").toInt,
      countSegment = 0,
      qualifications = qualifications.map {
        case (k, label) =>
          IndexedQualification(
            key = k,
            count = values(s"vote_verified_${key.value}_$label").toInt,
            countVerified = values(s"vote_verified_${key.value}_$label").toInt,
            countSequence = values(s"vote_sequence_${key.value}_$label").toInt,
            countSegment = 0
          )
      }
    )
  }

  private def parseVotes(values: Map[String, String]): Seq[IndexedVote] = {
    val agree = parseVote(
      values = values,
      key = Agree,
      qualifications =
        Seq[(QualificationKey, String)](LikeIt -> "like", Doable -> "doable", PlatitudeAgree -> "platitudeagree")
    )
    val disagree = parseVote(
      values = values,
      key = Disagree,
      qualifications = Seq[(QualificationKey, String)](
        NoWay -> "noway",
        Impossible -> "impossible",
        PlatitudeDisagree -> "platitudedisagree"
      )
    )
    val neutral = parseVote(
      values = values,
      key = Neutral,
      qualifications = Seq[(QualificationKey, String)](
        DoNotUnderstand -> "donotunderstand",
        NoOpinion -> "noopinion",
        DoNotCare -> "donotcare"
      )
    )
    Seq(agree, disagree, neutral)
  }

  private def parseProposal(values: Map[String, String]): IndexedProposal = {
    TestUtils.indexedProposal(
      id = ProposalId(values("id")),
      userId = UserId(values("id")),
      votes = this.parseVotes(values),
      createdAt = ZonedDateTime.parse(values("date_created")),
      keywords = values("keywords")
        .split('|')
        .map(k => IndexedProposalKeyword(ProposalKeywordKey(k), k))
        .toSeq
    )
  }

  def getProposalsFromCsv(csvName: String): Seq[IndexedProposal] = {
    CSVReader
      .open(Source.fromResource(csvName))
      .iteratorWithHeaders
      .map(parseProposal(_))
      .toSeq
  }

  private val maxVotes = 2000

  def getNewAndTestedProposals(csvName: String): (Seq[IndexedProposal], Seq[IndexedProposal]) = {
    getProposalsFromCsv(csvName)
      .filter(_.votesSequenceCount < maxVotes)
      .partition(_.votesSequenceCount <= 10)
  }
}
