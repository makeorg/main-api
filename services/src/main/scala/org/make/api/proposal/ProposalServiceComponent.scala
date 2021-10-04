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

import akka.Done
import org.make.api.question.AuthorRequest
import org.make.api.semantic.SimilarIdea
import org.make.api.technical.crm.QuestionResolver
import org.make.core.common.indexed.Sort
import org.make.core.idea.IdeaId
import org.make.core.proposal.indexed.{IndexedProposal, ProposalsSearchResult}
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.question.{Question, QuestionId, TopProposalsMode}
import org.make.core.reference.Country
import org.make.core.session.SessionId
import org.make.core.tag.TagId
import org.make.core.user._
import org.make.core._

import java.time.ZonedDateTime
import scala.concurrent.Future

trait ProposalServiceComponent {
  def proposalService: ProposalService
}

trait ProposalService {

  def getProposalById(proposalId: ProposalId, requestContext: RequestContext): Future[Option[IndexedProposal]]

  def getProposalsById(proposalIds: Seq[ProposalId], requestContext: RequestContext): Future[Seq[IndexedProposal]]

  def getModerationProposalById(proposalId: ProposalId): Future[Option[ModerationProposalResponse]]

  def getEventSourcingProposal(proposalId: ProposalId, requestContext: RequestContext): Future[Option[Proposal]]

  def getSimilar(userId: UserId, proposal: IndexedProposal, requestContext: RequestContext): Future[Seq[SimilarIdea]]

  def search(userId: Option[UserId], query: SearchQuery, requestContext: RequestContext): Future[ProposalsSearchResult]

  def searchForUser(
    userId: Option[UserId],
    query: SearchQuery,
    requestContext: RequestContext
  ): Future[ProposalsResultSeededResponse]

  def getTopProposals(
    maybeUserId: Option[UserId],
    questionId: QuestionId,
    size: Int,
    mode: Option[TopProposalsMode],
    requestContext: RequestContext
  ): Future[ProposalsResultResponse]

  def searchProposalsVotedByUser(
    userId: UserId,
    filterVotes: Option[Seq[VoteKey]],
    filterQualifications: Option[Seq[QualificationKey]],
    sort: Option[Sort],
    limit: Option[Int],
    skip: Option[Int],
    requestContext: RequestContext
  ): Future[ProposalsResultResponse]

  def propose(
    user: User,
    requestContext: RequestContext,
    createdAt: ZonedDateTime,
    content: String,
    question: Question,
    initialProposal: Boolean
  ): Future[ProposalId]

  def update(
    proposalId: ProposalId,
    moderator: UserId,
    requestContext: RequestContext,
    updatedAt: ZonedDateTime,
    newContent: Option[String],
    question: Question,
    tags: Seq[TagId],
    predictedTags: Option[Seq[TagId]],
    predictedTagsModelName: Option[String]
  ): Future[Option[ModerationProposalResponse]]

  def updateVotes(
    proposalId: ProposalId,
    moderator: UserId,
    requestContext: RequestContext,
    updatedAt: ZonedDateTime,
    votesVerified: Seq[UpdateVoteRequest]
  ): Future[Option[ModerationProposalResponse]]

  def validateProposal(
    proposalId: ProposalId,
    moderator: UserId,
    requestContext: RequestContext,
    question: Question,
    newContent: Option[String],
    sendNotificationEmail: Boolean,
    tags: Seq[TagId],
    predictedTags: Option[Seq[TagId]],
    predictedTagsModelName: Option[String]
  ): Future[Option[ModerationProposalResponse]]

  def refuseProposal(
    proposalId: ProposalId,
    moderator: UserId,
    requestContext: RequestContext,
    request: RefuseProposalRequest
  ): Future[Option[ModerationProposalResponse]]

  def postponeProposal(
    proposalId: ProposalId,
    moderator: UserId,
    requestContext: RequestContext
  ): Future[Option[ModerationProposalResponse]]

  def voteProposal(
    proposalId: ProposalId,
    maybeUserId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    proposalKey: Option[String]
  ): Future[Option[Vote]]

  def unvoteProposal(
    proposalId: ProposalId,
    maybeUserId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    proposalKey: Option[String]
  ): Future[Option[Vote]]

  def qualifyVote(
    proposalId: ProposalId,
    maybeUserId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    qualificationKey: QualificationKey,
    proposalKey: Option[String]
  ): Future[Option[Qualification]]

  def unqualifyVote(
    proposalId: ProposalId,
    maybeUserId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    qualificationKey: QualificationKey,
    proposalKey: Option[String]
  ): Future[Option[Qualification]]

  def lockProposal(
    proposalId: ProposalId,
    moderatorId: UserId,
    moderatorFullName: Option[String],
    requestContext: RequestContext
  ): Future[Unit]

  def lockProposals(
    proposalIds: Seq[ProposalId],
    moderatorId: UserId,
    moderatorFullName: Option[String],
    requestContext: RequestContext
  ): Future[Unit]

  def patchProposal(
    proposalId: ProposalId,
    userId: UserId,
    requestContext: RequestContext,
    changes: PatchProposalRequest
  ): Future[Option[ModerationProposalResponse]]

  def changeProposalsIdea(proposalIds: Seq[ProposalId], moderatorId: UserId, ideaId: IdeaId): Future[Seq[Proposal]]

  def searchAndLockAuthorToModerate(
    questionId: QuestionId,
    moderator: UserId,
    moderatorFullName: Option[String],
    requestContext: RequestContext,
    toEnrich: Boolean,
    minVotesCount: Option[Int],
    minScore: Option[Double]
  ): Future[Option[ModerationAuthorResponse]]

  def searchAndLockProposalToModerate(
    questionId: QuestionId,
    moderator: UserId,
    moderatorFullName: Option[String],
    requestContext: RequestContext,
    toEnrich: Boolean,
    minVotesCount: Option[Int],
    minScore: Option[Double]
  ): Future[Option[ModerationProposalResponse]]

  def createInitialProposal(
    content: String,
    question: Question,
    country: Country,
    tags: Seq[TagId],
    author: AuthorRequest,
    moderator: UserId,
    moderatorRequestContext: RequestContext
  ): Future[ProposalId]

  def getTagsForProposal(proposal: Proposal): Future[TagsForProposalResponse]

  def resetVotes(adminUserId: UserId, requestContext: RequestContext): Future[Done]

  def resolveQuestionFromVoteEvent(
    resolver: QuestionResolver,
    context: RequestContext,
    proposalId: ProposalId
  ): Future[Option[Question]]

  def resolveQuestionFromUserProposal(
    questionResolver: QuestionResolver,
    requestContext: RequestContext,
    userId: UserId,
    eventDate: ZonedDateTime
  ): Future[Option[Question]]

  def questionFeaturedProposals(
    questionId: QuestionId,
    maxPartnerProposals: Int,
    limit: Int,
    seed: Option[Int],
    maybeUserId: Option[UserId],
    requestContext: RequestContext
  ): Future[ProposalsResultSeededResponse]

  def setKeywords(
    proposalKeywordsList: Seq[ProposalKeywordRequest],
    requestContext: RequestContext
  ): Future[Seq[ProposalKeywordsResponse]]

  def acceptAll(
    proposalIds: Seq[ProposalId],
    moderator: UserId,
    requestContext: RequestContext
  ): Future[BulkActionResponse]

  def refuseAll(
    proposalIds: Seq[ProposalId],
    moderator: UserId,
    requestContext: RequestContext
  ): Future[BulkActionResponse]

  def addTagsToAll(
    proposalIds: Seq[ProposalId],
    tagIds: Seq[TagId],
    moderator: UserId,
    requestContext: RequestContext
  ): Future[BulkActionResponse]

  def deleteTagFromAll(
    proposalIds: Seq[ProposalId],
    tagId: TagId,
    moderator: UserId,
    requestContext: RequestContext
  ): Future[BulkActionResponse]

  def getHistory(proposalId: ProposalId): Future[Option[Seq[ProposalActionResponse]]]

  // TODO remove the salt param and use it internally from this service's components
  def generateProposalKeyHash(
    proposalId: ProposalId,
    sessionId: SessionId,
    location: Option[String],
    salt: String
  ): String
}
