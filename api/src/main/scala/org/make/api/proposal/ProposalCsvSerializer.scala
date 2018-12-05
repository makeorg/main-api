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

package org.make.api.proposal

import org.make.core.operation.Operation
import org.make.core.proposal.VoteKey
import org.make.core.proposal.indexed.{IndexedProposal, IndexedVote}
import org.make.core.reference.{Language, Theme}

object ProposalCsvSerializer {

  val START = "\""
  val END = "\""
  val SEPARATOR = ","

  val proposalsCsvHeaders: String =
    "Theme,Operation,Tags,Content,User Firstname,Labels,Total votes,Agree,LikeIt,Doable," +
      "Platitude Agree,Disagree,No Way,Impossible,Platitude Disagree,Neutral,Do Not Understand," +
      "No Opinion,Do Not Care,User PostalCode,User Age,UserId,Created at,Updated at"

  def proposalsToRow(proposals: Seq[IndexedProposal], themes: Seq[Theme], operations: Seq[Operation]): Seq[String] = {
    proposals.map { proposal =>
      Seq(
        themes
          .find(_.themeId.value == proposal.themeId.map(_.value).getOrElse(""))
          .flatMap { theme =>
            theme.translations
              .find(_.language == Language("fr")) // TODO get language from config files
              .map(START + _.title + END)
          }
          .getOrElse(""),
        proposal.operationId.flatMap { operationId =>
          operations.find(_.operationId.value == operationId.value).flatMap { operation =>
            operation.questions
              .find(_.question.language == operation.defaultLanguage)
              .map(START + _.details.operationTitle + END)
          }
        }.getOrElse(""),
        proposal.tags.map(_.label).mkString(START, SEPARATOR, END),
        START + proposal.content + END,
        proposal.author.firstName.map(firstname => START + firstname + END).getOrElse(""),
        proposal.labels.mkString(START, SEPARATOR, END),
        votesToRow(proposal.votes),
        proposal.author.postalCode.getOrElse(""),
        proposal.author.age.map(_.toString).getOrElse(""),
        proposal.userId.value,
        proposal.createdAt.toLocalDateTime.toString.split("T").mkString(" - "),
        proposal.updatedAt.map(_.toLocalDateTime.toString.split("T").mkString(" - ")).getOrElse("")
      ).mkString(SEPARATOR)
    }
  }

  def votesToRow(votes: Seq[IndexedVote]): String = {
    Seq(
      votes.map(_.count).sum.toString,
      votes
        .find(_.key == VoteKey.Agree)
        .map { vote =>
          s"${vote.count}," + vote.qualifications.map(qualifiction => qualifiction.count.toString).mkString(SEPARATOR)
        }
        .getOrElse("0,0,0,0"),
      votes
        .find(_.key == VoteKey.Disagree)
        .map { vote =>
          s"${vote.count}," + vote.qualifications.map(qualifiction => qualifiction.count.toString).mkString(SEPARATOR)
        }
        .getOrElse("0,0,0,0"),
      votes
        .find(_.key == VoteKey.Neutral)
        .map { vote =>
          s"${vote.count}," + vote.qualifications.map(qualifiction => qualifiction.count.toString).mkString(SEPARATOR)
        }
        .getOrElse("0,0,0,0")
    ).mkString(SEPARATOR)
  }
}
