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

package org.make.api.migrations
import org.make.api.MakeApi
import org.make.api.migrations.ProposalHelper.{FixtureDataLine, UserInfo}
import org.make.core.SlugHelper
import org.make.core.proposal.{QuestionSearchFilter, SearchFilters, SearchQuery, SlugSearchFilter}
import org.make.core.reference.{Country, Language}

import scala.concurrent.Future

object NiceMatinData extends InsertOperationFixtureData {
  override val operationSlug: String = NiceMatinOperation.operationSlug
  override val country: Country = Country("FR")
  override val language: Language = Language("fr")
  override val users: Seq[UserInfo] = Seq(
    UserInfo("yopmail+leila@make.org", "Leila", 33, Country("FR"), Language("fr")),
    UserInfo("yopmail+ariane@make.org", "Ariane", 19, Country("FR"), Language("fr")),
    UserInfo("yopmail+aminata@make.org", "Aminata", 45, Country("FR"), Language("fr")),
    UserInfo("yopmail+josephine@make.org", "Joséphine", 54, Country("FR"), Language("fr")),
    UserInfo("yopmail+joao@make.org", "Joao", 48, Country("FR"), Language("fr")),
    UserInfo("yopmail+isaac@make.org", "Isaac", 38, Country("FR"), Language("fr")),
    UserInfo("yopmail+pierre-marie@make.org", "Pierre-Marie", 50, Country("FR"), Language("fr")),
    UserInfo("yopmail+chen@make.org", "Chen", 17, Country("FR"), Language("fr")),
    UserInfo("yopmail+lucas@make.org", "Lucas", 23, Country("FR"), Language("fr")),
    UserInfo("yopmail+elisabeth@make.org", "Elisabeth", 36, Country("FR"), Language("fr")),
    UserInfo("yopmail+jordi@make.org", "Jordi", 30, Country("FR"), Language("fr")),
    UserInfo("yopmail+sophie@make.org", "Sophie", 39, Country("FR"), Language("fr")),
    UserInfo("yopmail+alek@make.org", "Alek", 21, Country("FR"), Language("fr")),
    UserInfo("yopmail+elisabeth@make.org", "Elisabeth", 65, Country("FR"), Language("fr")),
    UserInfo("yopmail+lucas@make.org", "Lucas", 18, Country("FR"), Language("fr")),
    UserInfo("yopmail+sandrine@make.org", "Sandrine", 35, Country("FR"), Language("fr")),
    UserInfo("yopmail+corinne@make.org", "Corinne", 52, Country("FR"), Language("fr")),
    UserInfo("yopmail+julie@make.org", "Julie", 18, Country("FR"), Language("fr")),
    UserInfo("yopmail+lionel@make.org", "Lionel", 25, Country("FR"), Language("fr")),
    UserInfo("yopmail+jean@make.org", "Jean", 48, Country("FR"), Language("fr")),
    UserInfo("yopmail+odile@make.org", "Odile", 29, Country("FR"), Language("fr")),
    UserInfo("yopmail+nicolas@make.org", "Nicolas", 42, Country("FR"), Language("fr")),
    UserInfo("yopmail+jamel@make.org", "Jamel", 22, Country("FR"), Language("fr")),
    UserInfo("yopmail+laurene@make.org", "Laurène", 27, Country("FR"), Language("fr")),
    UserInfo("yopmail+françois@make.org", "François", 33, Country("FR"), Language("fr")),
    UserInfo("yopmail+aissatou@make.org", "Aissatou", 31, Country("FR"), Language("fr")),
    UserInfo("yopmail+eric@make.org", "Éric", 56, Country("FR"), Language("fr")),
    UserInfo("yopmail+sylvain@make.org", "Sylvain", 46, Country("FR"), Language("fr"))
  )

//  We use the same proposals for Nice Matin and Huffpost Ecologie operations
  override def dataResource: String = "fixtures/huffingpost/proposals_ecologie.csv"

  override def extractDataLine(line: String): Option[FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, proposalTags) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = None,
            operation = None,
            tags = proposalTags.split('|').toSeq,
            labels = Seq.empty,
            country = Country("FR"),
            language = Language("fr"),
            acceptProposal = true
          )
        )
      case _ => None
    }
  }

  //  ovverride migrate because proposal already exist in another operation (ecologie-huffpost)
  override def migrate(api: MakeApi): Future[Unit] = {
    sequentially(readProposalFile(dataResource)) { proposal =>
      api.elasticsearchProposalAPI
        .countProposals(
          SearchQuery(
            filters = Some(
              SearchFilters(
                question = Some(QuestionSearchFilter(question.questionId)),
                slug = Some(SlugSearchFilter(SlugHelper(proposal.content)))
              )
            )
          )
        )
        .flatMap {
          case total if total > 0 => Future.successful {}
          case _ =>
            insertProposal(api, proposal.content, proposal.email, question).flatMap { proposalId =>
              val proposalTags = tags.filter(tag => proposal.tags.contains(tag.label)).map(_.tagId)
              acceptProposal(api, proposalId, proposal.content, question, proposalTags, proposal.labels)
            }
        }
    }
  }

  override val runInProduction: Boolean = true
}
