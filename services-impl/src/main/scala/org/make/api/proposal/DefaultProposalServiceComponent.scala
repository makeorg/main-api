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
import akka.stream.scaladsl.{Sink, Source}
import cats.data.{NonEmptyList, OptionT}
import cats.implicits._
import com.sksamuel.elastic4s.searches.sort.SortOrder
import grizzled.slf4j.Logging
import kamon.Kamon
import kamon.tag.TagSet
import org.make.api.ActorSystemComponent
import org.make.api.idea.IdeaServiceComponent
import org.make.api.partner.PartnerServiceComponent
import org.make.api.question.{AuthorRequest, QuestionServiceComponent}
import org.make.api.segment.SegmentServiceComponent
import org.make.api.semantic.{GetPredictedTagsResponse, PredictedTagsEvent, SemanticComponent, SimilarIdea}
import org.make.api.sessionhistory._
import org.make.api.tag.TagServiceComponent
import org.make.api.tagtype.TagTypeServiceComponent
import org.make.api.technical.Futures.RichFutures
import org.make.api.technical.crm.QuestionResolver
import org.make.api.technical.security.{SecurityConfigurationComponent, SecurityHelper}
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, MakeRandom, ReadJournalComponent}
import org.make.api.user.UserServiceComponent
import org.make.api.userhistory._
import org.make.core.common.indexed.Sort
import org.make.core.history.HistoryActions.VoteTrust._
import org.make.core.history.HistoryActions.{VoteAndQualifications, VoteTrust}
import org.make.core.idea.IdeaId
import org.make.core.partner.Partner
import org.make.core.proposal.ProposalStatus.Pending
import org.make.core.proposal.indexed.{IndexedProposal, ProposalElasticsearchFieldName, ProposalsSearchResult}
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.question.TopProposalsMode.IdeaMode
import org.make.core.question.{Question, QuestionId, TopProposalsMode}
import org.make.core.reference.Country
import org.make.core.session.SessionId
import org.make.core.tag.{Tag, TagId, TagType}
import org.make.core.technical.Pagination.Start
import org.make.core.user._
import org.make.core._

import java.time.ZonedDateTime
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DefaultProposalServiceComponent extends ProposalServiceComponent with CirceFormatters with Logging {
  this: ProposalServiceComponent
    with ActorSystemComponent
    with EventBusServiceComponent
    with IdeaServiceComponent
    with IdGeneratorComponent
    with PartnerServiceComponent
    with ProposalCoordinatorServiceComponent
    with ProposalSearchEngineComponent
    with QuestionServiceComponent
    with ReadJournalComponent
    with SecurityConfigurationComponent
    with SegmentServiceComponent
    with SemanticComponent
    with SessionHistoryCoordinatorServiceComponent
    with TagServiceComponent
    with TagTypeServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with UserServiceComponent =>

  override lazy val proposalService: DefaultProposalService = new DefaultProposalService

  class DefaultProposalService extends ProposalService {

    override def createInitialProposal(
      content: String,
      question: Question,
      country: Country,
      tags: Seq[TagId],
      author: AuthorRequest,
      moderator: UserId,
      moderatorRequestContext: RequestContext
    ): Future[ProposalId] = {

      def buildRequestContext(user: User): Future[RequestContext] = Future.successful(
        RequestContext.empty.copy(
          userId = Some(user.userId),
          country = Some(country),
          language = BusinessConfig.supportedCountries.collectFirst {
            case CountryConfiguration(`country`, language, _) => language
          },
          operationId = question.operationId,
          questionId = Some(question.questionId),
          applicationName = moderatorRequestContext.applicationName
        )
      )

      for {
        user           <- userService.retrieveOrCreateVirtualUser(author, country)
        requestContext <- buildRequestContext(user)
        proposalId     <- propose(user, requestContext, DateHelper.now(), content, question, initialProposal = true)
        _ <- validateProposal(
          proposalId = proposalId,
          moderator = moderator,
          requestContext = moderatorRequestContext,
          question = question,
          newContent = None,
          sendNotificationEmail = false,
          tags = tags,
          predictedTags = None,
          predictedTagsModelName = None
        )
      } yield proposalId
    }

    override def searchProposalsVotedByUser(
      userId: UserId,
      filterVotes: Option[Seq[VoteKey]],
      filterQualifications: Option[Seq[QualificationKey]],
      sort: Option[Sort],
      limit: Option[Int],
      skip: Option[Int],
      requestContext: RequestContext
    ): Future[ProposalsResultResponse] = {
      val votedProposals: Future[Map[ProposalId, VoteAndQualifications]] =
        for {
          proposalIds <- userHistoryCoordinatorService.retrieveVotedProposals(
            RequestUserVotedProposals(userId = userId, filterVotes, filterQualifications)
          )
          withVote <- userHistoryCoordinatorService.retrieveVoteAndQualifications(userId, proposalIds)
        } yield withVote

      votedProposals.flatMap {
        case proposalIdsWithVotes if proposalIdsWithVotes.isEmpty =>
          Future.successful((0L, Seq.empty))
        case proposalIdsWithVotes =>
          val proposalIds: Seq[ProposalId] = proposalIdsWithVotes.toSeq.sortWith {
            case ((_, firstVotesAndQualifications), (_, nextVotesAndQualifications)) =>
              firstVotesAndQualifications.date.isAfter(nextVotesAndQualifications.date)
          }.map {
            case (proposalId, _) => proposalId
          }
          proposalService
            .searchForUser(
              Some(userId),
              SearchQuery(
                filters = Some(SearchFilters(proposal = Some(ProposalSearchFilter(proposalIds = proposalIds)))),
                sort = sort,
                limit = limit,
                skip = skip
              ),
              requestContext
            )
            .map { proposalResultSeededResponse =>
              val proposalResult = proposalResultSeededResponse.results.sortWith {
                case (first, next) => proposalIds.indexOf(first.id) < proposalIds.indexOf(next.id)
              }
              (proposalResultSeededResponse.total, proposalResult)
            }
      }.map {
        case (total, proposalResult) =>
          ProposalsResultResponse(total = total, results = proposalResult)
      }
    }

    override def getProposalById(
      proposalId: ProposalId,
      requestContext: RequestContext
    ): Future[Option[IndexedProposal]] = {
      proposalCoordinatorService.viewProposal(proposalId, requestContext)
      elasticsearchProposalAPI.findProposalById(proposalId)
    }

    override def getProposalsById(
      proposalIds: Seq[ProposalId],
      requestContext: RequestContext
    ): Future[Seq[IndexedProposal]] = {
      Source(proposalIds).mapAsync(5)(id => getProposalById(id, requestContext)).runWith(Sink.seq).map(_.flatten)
    }

    override def getModerationProposalById(proposalId: ProposalId): Future[Option[ModerationProposalResponse]] = {
      toModerationProposalResponse(proposalCoordinatorService.getProposal(proposalId))
    }

    override def getEventSourcingProposal(
      proposalId: ProposalId,
      requestContext: RequestContext
    ): Future[Option[Proposal]] = {
      proposalCoordinatorService.viewProposal(proposalId, requestContext)
    }

    override def search(
      maybeUserId: Option[UserId],
      query: SearchQuery,
      requestContext: RequestContext
    ): Future[ProposalsSearchResult] = {
      val logSearchContent: Future[Unit] = query.filters.flatMap(_.content) match {
        case Some(contentFilter) =>
          sessionHistoryCoordinatorService.logTransactionalHistory(
            LogSessionSearchProposalsEvent(
              requestContext.sessionId,
              requestContext,
              SessionAction(
                DateHelper.now(),
                LogSessionSearchProposalsEvent.action,
                SessionSearchParameters(contentFilter.text)
              )
            )
          )
        case _ => Future.unit
      }
      logSearchContent.flatMap { _ =>
        elasticsearchProposalAPI.searchProposals(query)
      }
    }

    private def enrich(
      search: (Option[UserId], RequestContext) => Future[ProposalsSearchResult],
      maybeUserId: Option[UserId],
      requestContext: RequestContext
    ): Future[ProposalsResultResponse] = {
      search(maybeUserId, requestContext).flatMap { searchResult =>
        maybeUserId.map { userId =>
          userHistoryCoordinatorService.retrieveVoteAndQualifications(userId = userId, searchResult.results.map(_.id))
        }.getOrElse {
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(
              RequestSessionVoteValues(sessionId = requestContext.sessionId, searchResult.results.map(_.id))
            )
        }.map { votes =>
          val proposals = searchResult.results.map { indexedProposal =>
            val proposalKey =
              generateProposalKeyHash(
                indexedProposal.id,
                requestContext.sessionId,
                requestContext.location,
                securityConfiguration.secureVoteSalt
              )
            ProposalResponse.apply(
              indexedProposal,
              myProposal = maybeUserId.contains(indexedProposal.userId),
              votes.get(indexedProposal.id),
              proposalKey
            )
          }
          ProposalsResultResponse(searchResult.total, proposals)
        }
      }
    }

    override def searchForUser(
      maybeUserId: Option[UserId],
      query: SearchQuery,
      requestContext: RequestContext
    ): Future[ProposalsResultSeededResponse] = {
      enrich(search(_, query, _), maybeUserId, requestContext).map(
        proposalResultResponse =>
          ProposalsResultSeededResponse(proposalResultResponse.total, proposalResultResponse.results, query.getSeed)
      )
    }

    override def getTopProposals(
      maybeUserId: Option[UserId],
      questionId: QuestionId,
      size: Int,
      mode: Option[TopProposalsMode],
      requestContext: RequestContext
    ): Future[ProposalsResultResponse] = {
      val search = mode match {
        case Some(IdeaMode) =>
          elasticsearchProposalAPI.getTopProposals(questionId, size, ProposalElasticsearchFieldName.ideaId)
        case _ =>
          elasticsearchProposalAPI.getTopProposals(questionId, size, ProposalElasticsearchFieldName.selectedStakeTagId)
      }
      enrich((_, _) => search.map(results => ProposalsSearchResult(results.size, results)), maybeUserId, requestContext)
        .map(
          proposalResultResponse =>
            ProposalsResultResponse(proposalResultResponse.total, proposalResultResponse.results)
        )
    }

    override def propose(
      user: User,
      requestContext: RequestContext,
      createdAt: ZonedDateTime,
      content: String,
      question: Question,
      initialProposal: Boolean
    ): Future[ProposalId] = {

      proposalCoordinatorService.propose(
        proposalId = idGenerator.nextProposalId(),
        requestContext = requestContext,
        user = user,
        createdAt = createdAt,
        content = content,
        question = question,
        initialProposal = initialProposal
      )
    }

    override def update(
      proposalId: ProposalId,
      moderator: UserId,
      requestContext: RequestContext,
      updatedAt: ZonedDateTime,
      newContent: Option[String],
      question: Question,
      tags: Seq[TagId],
      predictedTags: Option[Seq[TagId]],
      predictedTagsModelName: Option[String]
    ): Future[Option[ModerationProposalResponse]] = {

      (predictedTags, predictedTagsModelName) match {
        case (Some(pTags), Some(modelName)) =>
          eventBusService.publish(PredictedTagsEvent(proposalId, pTags, tags, modelName))
        case _ =>
      }
      toModerationProposalResponse(
        proposalCoordinatorService.update(
          moderator = moderator,
          proposalId = proposalId,
          requestContext = requestContext,
          updatedAt = updatedAt,
          newContent = newContent,
          question = question,
          tags = tags
        )
      )
    }

    private def getEvents(proposal: Proposal): Future[Seq[ProposalActionResponse]] = {
      val eventsUserIds: Seq[UserId] = proposal.events.map(_.user).distinct
      val futureEventsUsers: Future[Seq[User]] = userService.getUsersByUserIds(eventsUserIds)

      futureEventsUsers.map { eventsUsers =>
        val userById = eventsUsers.map(u => u.userId -> u.displayName).toMap
        proposal.events.map { action =>
          ProposalActionResponse(
            date = action.date,
            user = userById.get(action.user).map(name => ProposalActionAuthorResponse(action.user, name)),
            actionType = action.actionType,
            arguments = action.arguments
          )
        }
      }
    }

    private def toModerationProposalResponse(
      futureProposal: Future[Option[Proposal]]
    ): Future[Option[ModerationProposalResponse]] = {
      val futureMaybeProposalAuthor: Future[Option[(Proposal, User)]] = (
        for {
          proposal <- OptionT(futureProposal)
          author   <- OptionT(userService.getUser(proposal.author))
        } yield (proposal, author)
      ).value

      futureMaybeProposalAuthor.flatMap {
        case None => Future.successful(None)
        case Some((proposal, author)) =>
          getEvents(proposal).map { events =>
            Some(
              ModerationProposalResponse(
                id = proposal.proposalId,
                proposalId = proposal.proposalId,
                slug = proposal.slug,
                content = proposal.content,
                author = ModerationProposalAuthorResponse(author),
                labels = proposal.labels,
                status = proposal.status,
                refusalReason = proposal.refusalReason,
                tags = proposal.tags,
                votes = proposal.votes,
                context = proposal.creationContext,
                createdAt = proposal.createdAt,
                updatedAt = proposal.updatedAt,
                events = events,
                idea = proposal.idea,
                ideaProposals = Seq.empty,
                operationId = proposal.operation,
                questionId = proposal.questionId,
                keywords = proposal.keywords
              )
            )
          }
      }
    }

    override def updateVotes(
      proposalId: ProposalId,
      moderator: UserId,
      requestContext: RequestContext,
      updatedAt: ZonedDateTime,
      votesVerified: Seq[UpdateVoteRequest]
    ): Future[Option[ModerationProposalResponse]] = {
      toModerationProposalResponse(
        proposalCoordinatorService.updateVotes(
          moderator = moderator,
          proposalId = proposalId,
          requestContext = requestContext,
          updatedAt = updatedAt,
          votes = votesVerified
        )
      )
    }

    override def validateProposal(
      proposalId: ProposalId,
      moderator: UserId,
      requestContext: RequestContext,
      question: Question,
      newContent: Option[String],
      sendNotificationEmail: Boolean,
      tags: Seq[TagId],
      predictedTags: Option[Seq[TagId]],
      predictedTagsModelName: Option[String]
    ): Future[Option[ModerationProposalResponse]] = {

      (predictedTags, predictedTagsModelName) match {
        case (Some(pTags), Some(modelName)) =>
          eventBusService.publish(PredictedTagsEvent(proposalId, pTags, tags, modelName))
        case _ =>
      }
      toModerationProposalResponse(
        proposalCoordinatorService.accept(
          proposalId = proposalId,
          moderator = moderator,
          requestContext = requestContext,
          sendNotificationEmail = sendNotificationEmail,
          newContent = newContent,
          question = question,
          tags = tags
        )
      )
    }

    override def refuseProposal(
      proposalId: ProposalId,
      moderator: UserId,
      requestContext: RequestContext,
      request: RefuseProposalRequest
    ): Future[Option[ModerationProposalResponse]] = {
      toModerationProposalResponse(
        proposalCoordinatorService.refuse(
          proposalId = proposalId,
          moderator = moderator,
          requestContext = requestContext,
          sendNotificationEmail = request.sendNotificationEmail,
          refusalReason = request.refusalReason
        )
      )
    }

    override def postponeProposal(
      proposalId: ProposalId,
      moderator: UserId,
      requestContext: RequestContext
    ): Future[Option[ModerationProposalResponse]] = {

      toModerationProposalResponse(
        proposalCoordinatorService
          .postpone(proposalId = proposalId, moderator = moderator, requestContext = requestContext)
      )
    }

    override def getSimilar(
      userId: UserId,
      proposal: IndexedProposal,
      requestContext: RequestContext
    ): Future[Seq[SimilarIdea]] = {
      userHistoryCoordinatorService.logHistory(
        LogGetProposalDuplicatesEvent(
          userId,
          requestContext,
          UserAction(DateHelper.now(), LogGetProposalDuplicatesEvent.action, proposal.id)
        )
      )
      val similarIdeas = 10
      semanticService.getSimilarIdeas(proposal, similarIdeas).recover {
        case error: Exception =>
          logger.error("", error)
          Seq.empty
      }
    }

    private def retrieveVoteHistory(
      proposalId: ProposalId,
      maybeUserId: Option[UserId],
      sessionId: SessionId
    ): Future[Map[ProposalId, VoteAndQualifications]] = {
      val votesHistory = maybeUserId match {
        case Some(userId) =>
          userHistoryCoordinatorService.retrieveVoteAndQualifications(userId, Seq(proposalId))
        case None =>
          sessionHistoryCoordinatorService
            .retrieveVoteAndQualifications(
              RequestSessionVoteValues(sessionId = sessionId, proposalIds = Seq(proposalId))
            )
      }
      votesHistory
    }

    private def retrieveUser(maybeUserId: Option[UserId]): Future[Option[User]] = {
      maybeUserId.map { userId =>
        userService.getUser(userId)
      }.getOrElse(Future.successful(None))
    }

    private def incrementTrollCounter(requestContext: RequestContext) = {
      Kamon
        .counter("vote_trolls")
        .withTags(
          TagSet.from(
            Map(
              "application" -> requestContext.applicationName.map(_.value).getOrElse("unknown"),
              "location" -> requestContext.location.flatMap(_.split(" ").headOption).getOrElse("unknown")
            )
          )
        )
        .increment()
    }

    private val sequenceLocations: Set[String] = Set("sequence", "widget", "sequence-popular", "sequence-controversial")

    def resolveVoteTrust(
      proposalKey: Option[String],
      proposalId: ProposalId,
      maybeUserSegment: Option[String],
      maybeProposalSegment: Option[String],
      requestContext: RequestContext
    ): VoteTrust = {

      val newHash =
        generateProposalKeyHash(
          proposalId,
          requestContext.sessionId,
          requestContext.location,
          securityConfiguration.secureVoteSalt
        )
      val page = requestContext.location.flatMap(_.split(" ").headOption)

      val isInSegment = (
        for {
          userSegment     <- maybeUserSegment
          proposalSegment <- maybeProposalSegment
        } yield userSegment == proposalSegment
      ).exists(identity)

      val inSequence = page.exists(sequenceLocations.contains)
      (proposalKey, proposalKey.contains(newHash), inSequence, isInSegment) match {
        case (None, _, _, _) =>
          logger.warn(s"No proposal key for proposal ${proposalId.value}, on context ${requestContext.toString}")
          incrementTrollCounter(requestContext)
          Troll
        case (Some(_), false, _, _) =>
          logger.warn(s"Bad proposal key found for proposal ${proposalId.value}, on context ${requestContext.toString}")
          incrementTrollCounter(requestContext)
          Troll
        case (Some(_), true, true, true) => Segment
        case (Some(_), true, true, _)    => Sequence
        case (Some(_), true, _, _)       => Trusted
      }
    }

    private def getSegmentForProposal(proposalId: ProposalId): Future[Option[String]] = {
      proposalCoordinatorService.getProposal(proposalId).flatMap {
        case None           => Future.successful(None)
        case Some(proposal) => segmentService.resolveSegment(proposal.creationContext)
      }
    }

    override def voteProposal(
      proposalId: ProposalId,
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      voteKey: VoteKey,
      proposalKey: Option[String]
    ): Future[Option[Vote]] = {

      val result = for {
        _                    <- sessionHistoryCoordinatorService.lockSessionForVote(requestContext.sessionId, proposalId)
        votes                <- retrieveVoteHistory(proposalId, maybeUserId, requestContext.sessionId)
        user                 <- retrieveUser(maybeUserId)
        maybeProposalSegment <- getSegmentForProposal(proposalId)
        maybeUserSegment     <- segmentService.resolveSegment(requestContext)
        vote <- proposalCoordinatorService.vote(
          proposalId = proposalId,
          maybeUserId = maybeUserId,
          requestContext = requestContext,
          voteKey = voteKey,
          maybeOrganisationId = user.filter(_.userType == UserType.UserTypeOrganisation).map(_.userId),
          vote = votes.get(proposalId),
          voteTrust = resolveVoteTrust(proposalKey, proposalId, maybeUserSegment, maybeProposalSegment, requestContext)
        )
        _ <- sessionHistoryCoordinatorService.unlockSessionForVote(requestContext.sessionId, proposalId)
      } yield vote

      result.recoverWith {
        case e: ConcurrentModification => Future.failed(e)
        case other =>
          sessionHistoryCoordinatorService.unlockSessionForVote(requestContext.sessionId, proposalId).flatMap { _ =>
            Future.failed(other)
          }
      }

    }

    override def unvoteProposal(
      proposalId: ProposalId,
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      voteKey: VoteKey,
      proposalKey: Option[String]
    ): Future[Option[Vote]] = {

      val result = for {
        _                    <- sessionHistoryCoordinatorService.lockSessionForVote(requestContext.sessionId, proposalId)
        votes                <- retrieveVoteHistory(proposalId, maybeUserId, requestContext.sessionId)
        user                 <- retrieveUser(maybeUserId)
        maybeUserSegment     <- segmentService.resolveSegment(requestContext)
        maybeProposalSegment <- getSegmentForProposal(proposalId)
        unvote <- proposalCoordinatorService.unvote(
          proposalId = proposalId,
          maybeUserId = maybeUserId,
          requestContext = requestContext,
          voteKey = voteKey,
          maybeOrganisationId = user.filter(_.userType == UserType.UserTypeOrganisation).map(_.userId),
          vote = votes.get(proposalId),
          voteTrust = resolveVoteTrust(proposalKey, proposalId, maybeUserSegment, maybeProposalSegment, requestContext)
        )
        _ <- sessionHistoryCoordinatorService.unlockSessionForVote(requestContext.sessionId, proposalId)
      } yield unvote

      result.recoverWith {
        case e: ConcurrentModification => Future.failed(e)
        case other =>
          sessionHistoryCoordinatorService.unlockSessionForVote(requestContext.sessionId, proposalId).flatMap { _ =>
            Future.failed(other)
          }
      }
    }

    override def qualifyVote(
      proposalId: ProposalId,
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      voteKey: VoteKey,
      qualificationKey: QualificationKey,
      proposalKey: Option[String]
    ): Future[Option[Qualification]] = {

      val result = for {
        _ <- sessionHistoryCoordinatorService.lockSessionForQualification(
          requestContext.sessionId,
          proposalId,
          qualificationKey
        )
        votes                <- retrieveVoteHistory(proposalId, maybeUserId, requestContext.sessionId)
        maybeUserSegment     <- segmentService.resolveSegment(requestContext)
        maybeProposalSegment <- getSegmentForProposal(proposalId)
        qualify <- proposalCoordinatorService.qualification(
          proposalId = proposalId,
          maybeUserId = maybeUserId,
          requestContext = requestContext,
          voteKey = voteKey,
          qualificationKey = qualificationKey,
          vote = votes.get(proposalId),
          voteTrust = resolveVoteTrust(proposalKey, proposalId, maybeUserSegment, maybeProposalSegment, requestContext)
        )
        _ <- sessionHistoryCoordinatorService.unlockSessionForQualification(
          requestContext.sessionId,
          proposalId,
          qualificationKey
        )
      } yield qualify

      result.recoverWith {
        case e: ConcurrentModification => Future.failed(e)
        case other =>
          sessionHistoryCoordinatorService
            .unlockSessionForQualification(requestContext.sessionId, proposalId, qualificationKey)
            .flatMap { _ =>
              Future.failed(other)
            }
      }

    }

    override def unqualifyVote(
      proposalId: ProposalId,
      maybeUserId: Option[UserId],
      requestContext: RequestContext,
      voteKey: VoteKey,
      qualificationKey: QualificationKey,
      proposalKey: Option[String]
    ): Future[Option[Qualification]] = {

      val result = for {
        _ <- sessionHistoryCoordinatorService.lockSessionForQualification(
          requestContext.sessionId,
          proposalId,
          qualificationKey
        )
        votes                <- retrieveVoteHistory(proposalId, maybeUserId, requestContext.sessionId)
        maybeUserSegment     <- segmentService.resolveSegment(requestContext)
        maybeProposalSegment <- getSegmentForProposal(proposalId)
        removeQualification <- proposalCoordinatorService.unqualification(
          proposalId = proposalId,
          maybeUserId = maybeUserId,
          requestContext = requestContext,
          voteKey = voteKey,
          qualificationKey = qualificationKey,
          vote = votes.get(proposalId),
          voteTrust = resolveVoteTrust(proposalKey, proposalId, maybeUserSegment, maybeProposalSegment, requestContext)
        )
        _ <- sessionHistoryCoordinatorService.unlockSessionForQualification(
          requestContext.sessionId,
          proposalId,
          qualificationKey
        )
      } yield removeQualification

      result.recoverWith {
        case e: ConcurrentModification => Future.failed(e)
        case other =>
          sessionHistoryCoordinatorService
            .unlockSessionForQualification(requestContext.sessionId, proposalId, qualificationKey)
            .flatMap { _ =>
              Future.failed(other)
            }
      }

    }

    override def lockProposal(
      proposalId: ProposalId,
      moderatorId: UserId,
      moderatorFullName: Option[String],
      requestContext: RequestContext
    ): Future[Unit] = {
      proposalCoordinatorService
        .lock(
          proposalId = proposalId,
          moderatorId = moderatorId,
          moderatorName = moderatorFullName,
          requestContext = requestContext
        )
        .toUnit
    }

    override def lockProposals(
      proposalIds: Seq[ProposalId],
      moderatorId: UserId,
      moderatorFullName: Option[String],
      requestContext: RequestContext
    ): Future[Unit] = {
      Source(proposalIds)
        .mapAsync(5)(
          id =>
            proposalCoordinatorService.lock(
              proposalId = id,
              moderatorId = moderatorId,
              moderatorName = moderatorFullName,
              requestContext = requestContext
            )
        )
        .runWith(Sink.seq)
        .toUnit
    }

    // Very permissive (no event generated etc), use carefully and only for one shot migrations.
    override def patchProposal(
      proposalId: ProposalId,
      userId: UserId,
      requestContext: RequestContext,
      changes: PatchProposalRequest
    ): Future[Option[ModerationProposalResponse]] = {
      toModerationProposalResponse(
        proposalCoordinatorService
          .patch(proposalId = proposalId, userId = userId, changes = changes, requestContext = requestContext)
      )
    }

    override def changeProposalsIdea(
      proposalIds: Seq[ProposalId],
      moderatorId: UserId,
      ideaId: IdeaId
    ): Future[Seq[Proposal]] = {
      Future
        .sequence(proposalIds.map { proposalId =>
          proposalCoordinatorService.patch(
            proposalId = proposalId,
            userId = moderatorId,
            changes = PatchProposalRequest(ideaId = Some(ideaId)),
            requestContext = RequestContext.empty
          )
        })
        .map(_.flatten)
    }

    private def getSearchFilters(
      questionId: QuestionId,
      toEnrich: Boolean,
      minVotesCount: Option[Int],
      minScore: Option[Double]
    ): SearchFilters = {
      if (toEnrich) {
        SearchFilters(
          question = Some(QuestionSearchFilter(Seq(questionId))),
          status = Some(StatusSearchFilter(Seq(ProposalStatus.Accepted))),
          toEnrich = Some(ToEnrichSearchFilter(toEnrich)),
          minVotesCount = minVotesCount.map(MinVotesCountSearchFilter.apply),
          minScore = minScore.map(MinScoreSearchFilter.apply)
        )
      } else {
        SearchFilters(
          question = Some(QuestionSearchFilter(Seq(questionId))),
          status = Some(StatusSearchFilter(Seq(ProposalStatus.Pending)))
        )
      }
    }

    override def searchAndLockAuthorToModerate(
      questionId: QuestionId,
      moderatorId: UserId,
      moderatorFullName: Option[String],
      requestContext: RequestContext,
      toEnrich: Boolean,
      minVotesCount: Option[Int],
      minScore: Option[Double]
    ): Future[Option[ModerationAuthorResponse]] = {
      val searchFilters = getSearchFilters(questionId, toEnrich, minVotesCount, minScore)
      search(
        maybeUserId = Some(moderatorId),
        requestContext = requestContext,
        query = SearchQuery(
          filters = Some(searchFilters),
          sort = Some(Sort(Some(ProposalElasticsearchFieldName.createdAt.field), Some(SortOrder.ASC))),
          limit = Some(1000),
          language = None,
          sortAlgorithm = Some(B2BFirstAlgorithm)
        )
      ).flatMap { searchResults =>
        // Group proposals to moderate by author, in a LinkedHashMap to transfer proposal search sort order to authors
        @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
        val candidates =
          searchResults.results.foldLeft(mutable.LinkedHashMap.empty[UserId, NonEmptyList[IndexedProposal]]) {
            case (map, proposal) =>
              val list = map.get(proposal.userId).fold(NonEmptyList.of(proposal))(_ :+ proposal)
              map.put(proposal.userId, list)
              map
          }

        // For current author candidate, try to lock and turn every proposal into a moderation response
        def futureMaybeSuccessfulLocks(
          indexedProposals: NonEmptyList[IndexedProposal],
          tags: Seq[Tag],
          tagTypes: Seq[TagType]
        ): Future[NonEmptyList[Option[ModerationProposalResponse]]] = {
          indexedProposals.traverse { proposal =>
            getModerationProposalById(proposal.id).flatMap {
              case None => Future.successful(None)
              case Some(proposal) =>
                val isValid: Boolean = if (toEnrich) {
                  val proposalTags = tags.filter(tag => proposal.tags.contains(tag.tagId))
                  Proposal.needsEnrichment(proposal.status, tagTypes, proposalTags.map(_.tagTypeId))
                } else {
                  proposal.status == Pending
                }

                if (!isValid) {
                  // Current proposal was moderated in the meantime, do nothing
                  Future.successful(None)
                } else {
                  proposalCoordinatorService
                    .lock(proposal.proposalId, moderatorId, moderatorFullName, requestContext)
                    .map { _ =>
                      searchFilters.status.foreach(
                        filter =>
                          if (!filter.status.contains(proposal.status)) {
                            logger.error(
                              s"Proposal id=${proposal.proposalId.value} with status=${proposal.status} incorrectly candidate for moderation, questionId=${questionId.value} moderator=${moderatorId.value} toEnrich=$toEnrich searchFilters=$searchFilters requestContext=$requestContext"
                            )
                          }
                      )
                      Some(proposal)
                    }
                    .recoverWith { case _ => Future.successful(None) }
                }
            }
          }
        }

        def getAndLockFirstAuthor(tags: Seq[Tag], tagTypes: Seq[TagType]) =
          Source(candidates.view.values.toSeq)
            .foldAsync[Option[NonEmptyList[ModerationProposalResponse]]](None) {
              case (None, indexedProposals) =>
                // For current author entry, build a future list of options of locked proposals, each element being
                // a moderation response defined if corresponding proposal was still valid and could be locked.
                // Then turn it into a future option of a list of locks if every one succeeded.
                futureMaybeSuccessfulLocks(indexedProposals, tags, tagTypes).map(_.sequence)
              case (Some(list), _) =>
                Future.successful(Some(list))
            }
            // Find the first author for which all proposals could be locked and were turned into a list of moderation responses
            .collect { case Some(list) => list }
            .runWith(Sink.headOption)
            .map(_.map { list =>
              ModerationAuthorResponse(list.head.author, list.toList, searchResults.total.toInt)
            })

        for {
          tags           <- tagService.findByQuestionId(questionId)
          tagTypes       <- tagTypeService.findAll(requiredForEnrichmentFilter = Some(true))
          authorResponse <- getAndLockFirstAuthor(tags, tagTypes)
        } yield authorResponse

      }
    }

    override def searchAndLockProposalToModerate(
      questionId: QuestionId,
      moderator: UserId,
      moderatorFullName: Option[String],
      requestContext: RequestContext,
      toEnrich: Boolean,
      minVotesCount: Option[Int],
      minScore: Option[Double]
    ): Future[Option[ModerationProposalResponse]] = {
      val defaultNumberOfProposals = 50
      val searchFilters = getSearchFilters(questionId, toEnrich, minVotesCount, minScore)
      search(
        maybeUserId = Some(moderator),
        requestContext = requestContext,
        query = SearchQuery(
          filters = Some(searchFilters),
          sort = Some(Sort(Some(ProposalElasticsearchFieldName.createdAt.field), Some(SortOrder.ASC))),
          limit = Some(defaultNumberOfProposals),
          language = None,
          sortAlgorithm = Some(B2BFirstAlgorithm)
        )
      ).flatMap { results =>
        @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
        def recursiveLock(availableProposals: List[ProposalId]): Future[Option[ModerationProposalResponse]] = {
          availableProposals match {
            case Nil => Future.successful(None)
            case currentProposalId :: otherProposalIds =>
              getModerationProposalById(currentProposalId).flatMap {
                // If, for some reason, the proposal is not found in event sourcing, ignore it
                case None => recursiveLock(otherProposalIds)
                case Some(proposal) =>
                  val isValid: Future[Boolean] = if (toEnrich) {
                    for {
                      tags     <- tagService.findByTagIds(proposal.tags)
                      tagTypes <- tagTypeService.findAll(requiredForEnrichmentFilter = Some(true))
                    } yield {
                      Proposal.needsEnrichment(proposal.status, tagTypes, tags.map(_.tagTypeId))
                    }
                  } else {
                    Future.successful(proposal.status == Pending)
                  }

                  isValid.flatMap {
                    case false => recursiveLock(otherProposalIds)
                    case true =>
                      proposalCoordinatorService
                        .lock(proposal.proposalId, moderator, moderatorFullName, requestContext)
                        .map { _ =>
                          searchFilters.status.foreach(
                            filter =>
                              if (!filter.status.contains(proposal.status)) {
                                logger.error(
                                  s"Proposal id=${proposal.proposalId.value} with status=${proposal.status} incorrectly candidate for moderation, questionId=${questionId.value} moderator=${moderator.value} toEnrich=$toEnrich searchFilters=$searchFilters requestContext=$requestContext"
                                )
                              }
                          )
                          Some(proposal)
                        }
                        .recoverWith { case _ => recursiveLock(otherProposalIds) }
                  }
              }
          }
        }
        recursiveLock(results.results.map(_.id).toList)
      }
    }

    override def getTagsForProposal(proposal: Proposal): Future[TagsForProposalResponse] = {
      proposal.questionId.map { questionId =>
        def futurePredictedTags: Future[GetPredictedTagsResponse] = {
          if (proposal.tags.isEmpty) {
            semanticService.getPredictedTagsForProposal(proposal).recover {
              case error: Exception =>
                logger.error("", error)
                GetPredictedTagsResponse.none
            }
          } else {
            Future.successful(GetPredictedTagsResponse.none)
          }
        }
        val futureTags: Future[(Seq[Tag], GetPredictedTagsResponse)] = for {
          questionTags  <- tagService.findByQuestionId(questionId)
          predictedTags <- futurePredictedTags
        } yield (questionTags, predictedTags)
        futureTags.map {
          case (questionTags, predictedTags) =>
            val predictedSet = predictedTags.tags.map(_.tagId).toSet
            val tags = questionTags.map { tag =>
              val predicted = predictedSet.contains(tag.tagId)
              val checked = proposal.tags.contains(tag.tagId) || (proposal.tags.isEmpty && predicted)
              TagForProposalResponse(tag = tag, checked = checked, predicted = predicted)
            }
            TagsForProposalResponse(tags = tags, modelName = predictedTags.modelName)
        }
      }.getOrElse(Future.successful(TagsForProposalResponse.empty))
    }

    def trolledQualification(qualification: Qualification): Boolean =
      qualification.count != qualification.countVerified

    def trolledVote(vote: Vote): Boolean =
      vote.count != vote.countVerified || vote.qualifications.exists(trolledQualification)

    def needVoteReset(proposal: Proposal): Boolean =
      proposal.status == ProposalStatus.Accepted && proposal.votes.exists(trolledVote)

    override def resetVotes(adminUserId: UserId, requestContext: RequestContext): Future[Done] = {
      val start = System.currentTimeMillis()

      proposalJournal
        .currentPersistenceIds()
        .mapAsync(4) { id =>
          proposalCoordinatorService.getProposal(ProposalId(id))
        }
        .collect {
          case Some(proposal) if needVoteReset(proposal) => proposal
        }
        .mapAsync(4) { proposal =>
          proposalCoordinatorService.updateVotes(
            moderator = adminUserId,
            proposalId = proposal.proposalId,
            requestContext = requestContext,
            updatedAt = DateHelper.now(),
            votes = proposal.votes.collect {
              case vote if trolledVote(vote) =>
                UpdateVoteRequest(
                  key = vote.key,
                  count = Some(vote.countVerified),
                  countVerified = None,
                  countSequence = None,
                  countSegment = None,
                  qualifications = vote.qualifications.collect {
                    case qualification if trolledQualification(qualification) =>
                      UpdateQualificationRequest(
                        key = qualification.key,
                        count = Some(qualification.countVerified),
                        countVerified = None,
                        countSequence = None,
                        countSegment = None
                      )
                  }
                )
            }
          )
        }
        .runWith(Sink.ignore)
        .map { res =>
          val time = System.currentTimeMillis() - start
          logger.info(s"ResetVotes ended in $time ms")
          res
        }
    }

    override def resolveQuestionFromVoteEvent(
      resolver: QuestionResolver,
      context: RequestContext,
      proposalId: ProposalId
    ): Future[Option[Question]] = {
      resolver
        .extractQuestionWithOperationFromRequestContext(context) match {
        case Some(question) => Future.successful(Some(question))
        case None =>
          proposalCoordinatorService
            .getProposal(proposalId)
            .map { maybeProposal =>
              resolver.findQuestionWithOperation { question =>
                maybeProposal.flatMap(_.questionId).contains(question.questionId)
              }
            }
      }
    }

    private def searchUserProposals(userId: UserId): Future[ProposalsSearchResult] = {
      val filters =
        SearchFilters(
          users = Some(UserSearchFilter(Seq(userId))),
          status = Some(StatusSearchFilter(ProposalStatus.values))
        )
      elasticsearchProposalAPI
        .countProposals(SearchQuery(filters = Some(filters)))
        .flatMap { count =>
          if (count == 0) {
            Future.successful(ProposalsSearchResult(0L, Seq.empty))
          } else {
            elasticsearchProposalAPI
              .searchProposals(SearchQuery(filters = Some(filters), limit = Some(count.intValue())))
          }
        }
    }

    override def resolveQuestionFromUserProposal(
      questionResolver: QuestionResolver,
      requestContext: RequestContext,
      userId: UserId,
      eventDate: ZonedDateTime
    ): Future[Option[Question]] = {
      questionResolver
        .extractQuestionWithOperationFromRequestContext(requestContext) match {
        case Some(question) => Future.successful(Some(question))
        case None           =>
          // If we can't resolve the question, retrieve the user proposals,
          // and search for the one proposed at the event date
          searchUserProposals(userId).map { proposalResult =>
            proposalResult.results
              .find(_.createdAt == eventDate)
              .flatMap(_.question)
              .map(_.questionId)
              .flatMap { questionId =>
                questionResolver
                  .findQuestionWithOperation(question => questionId == question.questionId)
              }
          }
      }
    }

    override def questionFeaturedProposals(
      questionId: QuestionId,
      maxPartnerProposals: Int,
      limit: Int,
      seed: Option[Int],
      maybeUserId: Option[UserId],
      requestContext: RequestContext
    ): Future[ProposalsResultSeededResponse] = {
      val randomSeed: Int = seed.getOrElse(MakeRandom.nextInt())
      val futurePartnerProposals: Future[ProposalsResultSeededResponse] = Math.min(maxPartnerProposals, limit) match {
        case 0 => Future.successful(ProposalsResultSeededResponse.empty)
        case posInt =>
          partnerService
            .find(
              start = Start.zero,
              end = None,
              sort = None,
              order = None,
              questionId = Some(questionId),
              organisationId = None,
              partnerKind = None
            )
            .flatMap { partners =>
              partners.collect {
                case Partner(_, _, _, _, Some(orgaId), _, _, _) => orgaId
              } match {
                case Seq() => Future.successful(ProposalsResultSeededResponse.empty)
                case orgaIds =>
                  searchForUser(
                    maybeUserId,
                    query = SearchQuery(
                      filters = Some(
                        SearchFilters(
                          question = Some(QuestionSearchFilter(Seq(questionId))),
                          users = Some(UserSearchFilter(orgaIds))
                        )
                      ),
                      sortAlgorithm = Some(RandomAlgorithm(randomSeed)),
                      limit = Some(posInt)
                    ),
                    requestContext = requestContext
                  )
              }
            }
      }
      val futureProposalsRest: Future[ProposalsResultSeededResponse] = {
        enrich(
          (_, _) =>
            elasticsearchProposalAPI.getFeaturedProposals(
              SearchQuery(
                filters = Some(
                  SearchFilters(
                    question = Some(QuestionSearchFilter(Seq(questionId))),
                    userTypes = Some(UserTypesSearchFilter(Seq(UserType.UserTypeUser)))
                  )
                ),
                limit = Some(limit)
              )
            ),
          maybeUserId,
          requestContext
        ).map(r => ProposalsResultSeededResponse(r.total, r.results, None))
      }
      for {
        partnerProposals <- futurePartnerProposals
        rest             <- futureProposalsRest
      } yield ProposalsResultSeededResponse(
        partnerProposals.total + rest.total,
        partnerProposals.results ++ rest.results.take(limit - partnerProposals.results.size),
        Some(randomSeed)
      )

    }

    private def setProposalKeywords(
      proposalId: ProposalId,
      keywords: Seq[ProposalKeyword],
      requestContext: RequestContext
    ): Future[ProposalKeywordsResponse] = {
      proposalCoordinatorService.setKeywords(proposalId, keywords, requestContext).map {
        case Some(proposal) =>
          ProposalKeywordsResponse(proposal.proposalId, status = ProposalKeywordsResponseStatus.Ok, message = None)
        case None =>
          ProposalKeywordsResponse(
            proposalId,
            status = ProposalKeywordsResponseStatus.Error,
            message = Some(s"Proposal ${proposalId.value} not found")
          )
      }
    }

    override def setKeywords(
      proposalKeywordsList: Seq[ProposalKeywordRequest],
      requestContext: RequestContext
    ): Future[Seq[ProposalKeywordsResponse]] = {
      Source(proposalKeywordsList)
        .mapAsync(3) { proposalKeywords =>
          setProposalKeywords(proposalKeywords.proposalId, proposalKeywords.keywords, requestContext)
        }
        .runWith(Sink.seq)
    }

    override def acceptAll(
      proposalIds: Seq[ProposalId],
      moderator: UserId,
      requestContext: RequestContext
    ): Future[BulkActionResponse] = {
      bulkAction(acceptAction(moderator, requestContext), proposalIds)
    }

    override def refuseAll(
      proposalIds: Seq[ProposalId],
      moderator: UserId,
      requestContext: RequestContext
    ): Future[BulkActionResponse] = {
      bulkAction(refuseAction(moderator, requestContext), proposalIds)
    }

    override def addTagsToAll(
      proposalIds: Seq[ProposalId],
      tagIds: Seq[TagId],
      moderator: UserId,
      requestContext: RequestContext
    ): Future[BulkActionResponse] = {
      bulkAction(updateTagsAction(moderator, requestContext, current => (current ++ tagIds).distinct), proposalIds)
    }

    override def deleteTagFromAll(
      proposalIds: Seq[ProposalId],
      tagId: TagId,
      moderator: UserId,
      requestContext: RequestContext
    ): Future[BulkActionResponse] = {
      bulkAction(updateTagsAction(moderator, requestContext, _.filterNot(_ == tagId)), proposalIds)
    }

    private def acceptAction(
      moderator: UserId,
      requestContext: RequestContext
    )(proposal: IndexedProposal, question: Question): Future[Option[Proposal]] =
      proposalCoordinatorService
        .accept(
          proposalId = proposal.id,
          moderator = moderator,
          requestContext = requestContext,
          sendNotificationEmail = true,
          newContent = None,
          question = question,
          tags = proposal.tags.map(_.tagId)
        )

    private def refuseAction(
      moderator: UserId,
      requestContext: RequestContext
    )(proposal: IndexedProposal, question: Question): Future[Option[Proposal]] =
      if (proposal.initialProposal) {
        proposalCoordinatorService
          .refuse(
            proposalId = proposal.id,
            moderator = moderator,
            requestContext = requestContext,
            sendNotificationEmail = false,
            refusalReason = Some("other")
          )
      } else {
        Future.failed(
          ValidationFailedError(
            Seq(ValidationError(field = "initial", key = "not-initial", message = Some("The proposal is not initial")))
          )
        )
      }

    private def updateTagsAction(
      moderator: UserId,
      requestContext: RequestContext,
      transformTags: Seq[TagId] => Seq[TagId]
    )(proposal: IndexedProposal, question: Question): Future[Option[Proposal]] =
      proposalCoordinatorService
        .update(
          moderator = moderator,
          proposalId = proposal.id,
          requestContext = requestContext,
          updatedAt = DateHelper.now(),
          newContent = None,
          tags = transformTags(proposal.tags.map(_.tagId)),
          question = question
        )

    private def bulkAction(
      action: (IndexedProposal, Question) => Future[Option[Proposal]],
      proposalIds: Seq[ProposalId]
    ): Future[BulkActionResponse] = {
      val getProposals: Future[Map[ProposalId, (IndexedProposal, Option[QuestionId])]] =
        elasticsearchProposalAPI
          .searchProposals(
            SearchQuery(
              filters = Some(
                SearchFilters(
                  proposal = Some(ProposalSearchFilter(proposalIds)),
                  status = Some(StatusSearchFilter(ProposalStatus.values))
                )
              ),
              limit = Some(proposalIds.size + 1)
            )
          )
          .map(_.results.map(p => (p.id, (p, p.question.map(_.questionId)))).toMap)

      def getQuestions(proposalMap: Map[ProposalId, (IndexedProposal, Option[QuestionId])]): Future[Seq[Question]] =
        questionService.getQuestions(proposalMap.values.collect { case (_, Some(id)) => id }.toSeq.distinct)

      def runActionOnAll(
        action: (IndexedProposal, Question) => Future[Option[Proposal]],
        proposalIds: Seq[ProposalId],
        proposals: Map[ProposalId, (IndexedProposal, Option[QuestionId])],
        questions: Seq[Question]
      ): Future[Seq[SingleActionResponse]] = {
        Source(proposalIds).map { id =>
          proposals.get(id) match {
            case Some((p, Some(qId))) => (id, Some(p), questions.find(_.questionId == qId), Some(qId))
            case Some((p, None))      => (id, Some(p), None, None)
            case None                 => (id, None, None, None)
          }
        }.mapAsync(5) {
            case (_, Some(proposal), Some(question), _) => runAction(proposal.id, action(proposal, question))
            case (id, None, _, _) =>
              Future.successful(SingleActionResponse(id, ActionKey.NotFound, Some("Proposal not found")))
            case (id, Some(_), _, qId) =>
              Future.successful(
                SingleActionResponse(id, ActionKey.QuestionNotFound, Some(s"Question not found from id $qId"))
              )
          }
          .runWith(Sink.seq)
      }

      def runAction(id: ProposalId, action: Future[Option[Proposal]]): Future[SingleActionResponse] = {
        action.map {
          case None => SingleActionResponse(id, ActionKey.NotFound, Some(s"Proposal not found"))
          case _    => SingleActionResponse(id, ActionKey.OK, None)
        }.recover {
          case ValidationFailedError(Seq(error)) =>
            SingleActionResponse(id, ActionKey.ValidationError(error.key), error.message)
          case e =>
            SingleActionResponse(id, ActionKey.Unknown, Some(e.getMessage))
        }
      }

      for {
        proposalMap <- getProposals
        questions   <- getQuestions(proposalMap)
        actions     <- runActionOnAll(action, proposalIds, proposalMap, questions)
      } yield {
        val (successes, failures) = actions.partition(_.key == ActionKey.OK)
        BulkActionResponse(successes.map(_.proposalId), failures)
      }
    }

    override def getHistory(proposalId: ProposalId): Future[Option[Seq[ProposalActionResponse]]] = {
      for {
        proposal <- proposalCoordinatorService.getProposal(proposalId)
        events   <- proposal.traverse(getEvents)
      } yield events
    }

    override def generateProposalKeyHash(
      proposalId: ProposalId,
      sessionId: SessionId,
      location: Option[String],
      salt: String
    ): String = {
      val rawString: String = s"${proposalId.value}${sessionId.value}${location.getOrElse("")}"
      SecurityHelper.generateHash(value = rawString, salt = salt)
    }

  }
}
