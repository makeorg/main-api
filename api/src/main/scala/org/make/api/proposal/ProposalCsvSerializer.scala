package org.make.api.proposal

import org.make.core.proposal.VoteKey
import org.make.core.proposal.indexed.{IndexedProposal, IndexedVote}
import org.make.core.reference.Theme

object ProposalCsvSerializer {

  val START = "\""
  val END = "\""
  val SEPARATOR = ","

  val proposalsCsvHeaders: String =
    "Theme,Operation,Tags,Content,User Firstname,Labels,Total votes,Agree,LikeIt,Doable," +
      "Platitude Agree,Disagree,No Way,Impossible,Platitude Disagree,Neutral,Do Not Understand," +
      "No Opinion,Do Not Care,User PostalCode,User Age,UserId,Created at,Updated at"

  def proposalsToRow(proposals: Seq[IndexedProposal], themes: Seq[Theme]): Seq[String] = {
    proposals.map { proposal =>
      Seq(
        themes
          .find(_.themeId.value == proposal.themeId.map(_.value).getOrElse(""))
          .flatMap { theme =>
            theme.translations
              .find(_.language == "fr") // TODO get language from config files
              .map(START + _.title + END)
          }
          .getOrElse(""),
        proposal.context.flatMap(_.operation.map(operation => START + operation + END)).getOrElse(""),
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
