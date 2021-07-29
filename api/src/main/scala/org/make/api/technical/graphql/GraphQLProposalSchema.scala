/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical.graphql

import java.time.ZonedDateTime

import enumeratum.{Circe, Enum, EnumEntry}
import io.circe.Decoder
import org.make.api.proposal.{ContextFilterRequest, ProposalContextResponse, ProposalResponse, VoteResponse}
import org.make.api.technical.MakeRandom
import org.make.api.technical.graphql.GraphQLRuntimeComponent.RuntimeType
import org.make.core.common.indexed.Sort
import org.make.core.idea.IdeaId
import org.make.core.operation.{OperationId, OperationKind}
import org.make.core.proposal._
import org.make.core.proposal.indexed.ProposalElasticsearchFieldName
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.UserType
import org.make.core.{Order, RequestContext}
import zio.RIO
import zio.query.{DataSource, ZQuery}

final case class GraphQLProposal(
  id: ProposalId,
  content: String,
  slug: String,
  status: ProposalStatus,
  createdAt: ZonedDateTime,
  updatedAt: Option[ZonedDateTime],
  myProposal: Boolean,
  votes: Seq[VoteResponse],
  context: Option[ProposalContextResponse],
  proposalKey: String,
  author: ZQuery[Any, Throwable, GraphQLAuthor],
  question: Option[ZQuery[Any, Throwable, GraphQLQuestion]],
  organisations: ZQuery[Any, Throwable, Seq[GraphQLOrganisation]],
  tags: ZQuery[Any, Throwable, Seq[GraphQLTag]],
  selectedStakeTag: Option[GraphQLTag],
  idea: Option[ZQuery[Any, Throwable, GraphQLIdea]]
)

object GraphQLProposal {

  def fromProposalResponse(proposal: ProposalResponse)(
    userDataSource: DataSource[Any, GetAuthor],
    questionDataSource: DataSource[Any, GetQuestion],
    organisationDataSource: DataSource[Any, GetOrganisations],
    tagDataSource: DataSource[Any, GetTags],
    ideaDataSource: DataSource[Any, GetIdea]
  ): GraphQLProposal = {
    GraphQLProposal(
      id = proposal.id,
      content = proposal.content,
      slug = proposal.slug,
      status = proposal.status,
      createdAt = proposal.createdAt,
      updatedAt = proposal.updatedAt,
      myProposal = proposal.myProposal,
      votes = proposal.votes,
      context = proposal.context,
      proposalKey = proposal.proposalKey,
      author = ZQuery.fromRequest(GetAuthor(proposal.userId))(userDataSource),
      question = proposal.question.map { question =>
        ZQuery.fromRequest(GetQuestion(question.questionId))(questionDataSource)
      },
      organisations = ZQuery
        .fromRequest(GetOrganisations(proposal.organisations.map(_.organisationId).toList))(organisationDataSource),
      tags = ZQuery.fromRequest(GetTags(proposal.tags.map(_.tagId).toList))(tagDataSource),
      selectedStakeTag = proposal.selectedStakeTag.map(GraphQLTag.fromTag),
      idea = proposal.idea.map { ideaId =>
        ZQuery.fromRequest(GetIdea(ideaId))(ideaDataSource)
      }
    )
  }
}

object GraphQLProposalQuery {

  sealed abstract class ProposalSortableFieldNames(val value: String) extends EnumEntry

  object ProposalSortableFieldNames extends Enum[ProposalSortableFieldNames] {

    case object Slug extends ProposalSortableFieldNames(ProposalElasticsearchFieldName.slug.value)
    case object CreatedAt extends ProposalSortableFieldNames(ProposalElasticsearchFieldName.createdAt.value)
    case object UpdatedAt extends ProposalSortableFieldNames(ProposalElasticsearchFieldName.updatedAt.value)
    case object Country extends ProposalSortableFieldNames(ProposalElasticsearchFieldName.country.value)
    case object Language extends ProposalSortableFieldNames(ProposalElasticsearchFieldName.language.value)
    case object TopScoreAjustedWithVotes
        extends ProposalSortableFieldNames(ProposalElasticsearchFieldName.topScoreAjustedWithVotes.value)

    override def values: IndexedSeq[ProposalSortableFieldNames] = findValues
    implicit val decoder: Decoder[ProposalSortableFieldNames] = Circe.decodeCaseInsensitive(this)

  }

  final case class ProposalSearchParams(
    proposalIds: Option[Seq[ProposalId]],
    questionIds: Option[Seq[QuestionId]],
    tagsIds: Option[Seq[TagId]],
    operationId: Option[OperationId],
    content: Option[String],
    slug: Option[String],
    seed: Option[Int],
    source: Option[String],
    location: Option[String],
    question: Option[String],
    language: Option[Language],
    country: Option[Country],
    sort: Option[ProposalSortableFieldNames],
    order: Option[Order],
    limit: Option[Int],
    skip: Option[Int],
    sortAlgorithm: Option[AlgorithmSelector],
    operationKinds: Option[Seq[OperationKind]],
    userTypes: Option[Seq[UserType]],
    ideaIds: Option[Seq[IdeaId]]
  ) {
    def toSearchQuery(requestContext: RequestContext): SearchQuery = {
      val context: Option[ContextFilterRequest] = ContextFilterRequest.parse(operationId, source, location, question)

      val filters: Option[SearchFilters] =
        SearchFilters.parse(
          proposals = proposalIds.map(ProposalSearchFilter.apply),
          tags = tagsIds.map(TagsSearchFilter.apply),
          operation = operationId.map(opId => OperationSearchFilter(Seq(opId))),
          question = questionIds.map(QuestionSearchFilter.apply),
          content = content.map(ContentSearchFilter.apply),
          slug = slug.map(value => SlugSearchFilter(value)),
          context = context.map(_.toContext),
          language = language.map(LanguageSearchFilter.apply),
          country = country.map(CountrySearchFilter.apply),
          operationKinds = operationKinds.map(OperationKindsSearchFilter.apply),
          userTypes = userTypes.map(UserTypesSearchFilter.apply),
          idea = ideaIds.map(IdeaSearchFilter.apply)
        )

      SearchQuery(
        filters = filters,
        sort = Sort.parse(sort.map(_.value), order),
        limit = limit,
        skip = skip,
        language = requestContext.language,
        sortAlgorithm = sortAlgorithm.map(_.build(seed.getOrElse(MakeRandom.nextInt())))
      )
    }
  }

  final case class GraphQLProposalQueries(search: ProposalSearchParams => RIO[RuntimeType, Seq[GraphQLProposal]])

}
