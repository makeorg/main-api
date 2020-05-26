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

package org.make.api.technical.elasticsearch

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZonedDateTime}

import akka.stream.FlowShape
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition, Sink, Source}
import akka.{Done, NotUsed}
import cats.data.OptionT
import cats.implicits._
import com.sksamuel.elastic4s.IndexAndType
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemComponent
import org.make.api.operation.{OperationOfQuestionServiceComponent, OperationServiceComponent}
import org.make.api.organisation.OrganisationServiceComponent
import org.make.api.proposal.ProposalScorerHelper.ScoreCounts
import org.make.api.proposal.{
  ProposalCoordinatorServiceComponent,
  ProposalScorerHelper,
  ProposalSearchEngine,
  ProposalSearchEngineComponent
}
import org.make.api.question.QuestionServiceComponent
import org.make.api.segment.SegmentServiceComponent
import org.make.api.semantic.SemanticComponent
import org.make.api.sequence.{SequenceConfiguration, SequenceConfigurationComponent}
import org.make.api.tag.TagServiceComponent
import org.make.api.tagtype.TagTypeServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.operation.{OperationOfQuestion, SimpleOperation}
import org.make.core.proposal._
import org.make.core.proposal.indexed.{
  IndexedAuthor,
  IndexedGetParameters,
  IndexedOrganisationInfo,
  IndexedProposal,
  IndexedProposalQuestion,
  IndexedScores,
  IndexedTag,
  IndexedVote,
  IndexedContext => ProposalContext
}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.{Tag, TagType}
import org.make.core.user.User
import org.make.core.SlugHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait ProposalIndexationStream
    extends IndexationStream
    with ProposalCoordinatorServiceComponent
    with UserServiceComponent
    with OrganisationServiceComponent
    with OperationOfQuestionServiceComponent
    with OperationServiceComponent
    with QuestionServiceComponent
    with TagServiceComponent
    with TagTypeServiceComponent
    with ActorSystemComponent
    with ProposalSearchEngineComponent
    with SequenceConfigurationComponent
    with SemanticComponent
    with StrictLogging
    with SegmentServiceComponent {

  object ProposalStream {
    val maybeIndexedProposal: Flow[ProposalId, Option[IndexedProposal], NotUsed] =
      Flow[ProposalId].mapAsync(parallelism)(proposalId => getIndexedProposal(proposalId))

    def runIndexProposals(proposalIndexName: String): Flow[Seq[IndexedProposal], Done, NotUsed] =
      Flow[Seq[IndexedProposal]].mapAsync(parallelism)(proposals => executeIndexProposals(proposals, proposalIndexName))

    val findOrElseIndexedProposal: Flow[IndexedProposal, ProposalFlow, NotUsed] =
      Flow[IndexedProposal]
        .mapAsync(parallelism) { proposal =>
          elasticsearchProposalAPI.findProposalById(proposal.id).map {
            case Some(_) => UpdateProposalFlow(proposal)
            case _       => IndexProposalFlow(proposal)
          }
        }

    val indexProposals: Flow[Seq[IndexedProposal], Seq[IndexedProposal], NotUsed] =
      Flow[Seq[IndexedProposal]].mapAsync(singleAsync) { proposals =>
        elasticsearchProposalAPI.indexProposals(proposals)
      }

    val updateProposals: Flow[Seq[IndexedProposal], Seq[IndexedProposal], NotUsed] =
      Flow[Seq[IndexedProposal]].mapAsync(singleAsync) { proposals =>
        elasticsearchProposalAPI.updateProposals(proposals)
      }

    val semanticIndex: Flow[Seq[IndexedProposal], Done, NotUsed] =
      Flow[Seq[IndexedProposal]].mapAsync(parallelism) { proposals =>
        semanticService.indexProposals(proposals).map(_ => Done)
      }

    def flowIndexProposals(proposalIndexName: String): Flow[ProposalId, Done, NotUsed] =
      maybeIndexedProposal
        .via(filterIsDefined[IndexedProposal])
        .groupedWithin(100, 500.milliseconds)
        .via(runIndexProposals(proposalIndexName))

    val indexOrUpdateFlow: Flow[ProposalId, Seq[IndexedProposal], NotUsed] =
      Flow.fromGraph[ProposalId, Seq[IndexedProposal], NotUsed](GraphDSL.create() {
        implicit builder: GraphDSL.Builder[NotUsed] =>
          val source = builder.add(maybeIndexedProposal)
          val partition = builder.add(Partition[ProposalFlow](outputPorts = 2, partitioner = {
            case IndexProposalFlow(_)  => 0
            case UpdateProposalFlow(_) => 1
          }))
          val merge = builder.add(Merge[Seq[IndexedProposal]](2))

          val filterIndex: Flow[ProposalFlow, IndexedProposal, NotUsed] =
            Flow[ProposalFlow].filter {
              case IndexProposalFlow(_)  => true
              case UpdateProposalFlow(_) => false
            }.map(_.proposal)

          val filterUpdate: Flow[ProposalFlow, IndexedProposal, NotUsed] =
            Flow[ProposalFlow].filter {
              case IndexProposalFlow(_)  => false
              case UpdateProposalFlow(_) => true
            }.map(_.proposal)

          source.out ~> filterIsDefined[IndexedProposal] ~> findOrElseIndexedProposal ~> partition.in

          partition.out(0) ~> filterIndex  ~> grouped[IndexedProposal] ~> indexProposals  ~> merge
          partition.out(1) ~> filterUpdate ~> grouped[IndexedProposal] ~> updateProposals ~> merge

          FlowShape(source.in, merge.out)
      })
  }

  private def getQuestion: Option[QuestionId] => Future[Option[Question]] = {
    case Some(questionId) => questionService.getQuestion(questionId)
    case None             => Future.successful[Option[Question]](None)
  }

  private def getSelectedStakeTag(tags: Seq[Tag], tagTypes: Seq[TagType]): Future[Option[IndexedTag]] = {
    tagTypes.find(_.label.toLowerCase == "stake") match {
      case None => Future.failed(new IllegalStateException("Unable to find stake tag types"))
      case Some(stakeTypeTag) =>
        val stakeTags: Seq[Tag] = tags.filter(_.tagTypeId.value == stakeTypeTag.tagTypeId.value)
        tagService.retrieveIndexedTags(stakeTags, Seq(stakeTypeTag)).filter(_.display) match {
          case Seq()         => Future.successful(None)
          case Seq(stakeTag) => Future.successful(Some(stakeTag))
          case indexedTags =>
            Source(indexedTags)
              .mapAsync(5) { tag =>
                elasticsearchProposalAPI
                  .countProposals(
                    SearchQuery(filters = Some(SearchFilters(tags = Some(TagsSearchFilter(Seq(tag.tagId))))))
                  )
                  .map(tag -> _)
              }
              .runWith(Sink.seq)
              .map {
                _.sortBy {
                  case (tag, count) => (count * -1, tag.label)
                }.collectFirst {
                  case (tag, _) => tag
                }
              }
        }
    }
  }

  def getIndexedProposal(proposalId: ProposalId): Future[Option[IndexedProposal]] = {

    val maybeResult: OptionT[Future, IndexedProposal] = for {
      proposal         <- OptionT(proposalCoordinatorService.getProposal(proposalId))
      user             <- OptionT(userService.getUser(proposal.author))
      tags             <- OptionT(tagService.findByTagIds(proposal.tags).map(Option.apply))
      tagTypes         <- OptionT(tagTypeService.findAll().map(Option.apply))
      selectedStakeTag <- OptionT(getSelectedStakeTag(tags, tagTypes).map(Option.apply))
      organisationInfos <- OptionT(
        Source(proposal.organisationIds)
          .mapAsync(5)(organisationService.getOrganisation)
          .runWith(Sink.seq)
          .map(organisations => Option(organisations.flatten))
      )
      question            <- OptionT(getQuestion(proposal.questionId))
      operationOfQuestion <- OptionT(operationOfQuestionService.findByQuestionId(question.questionId))
      sequenceConfiguration <- OptionT(
        sequenceConfigurationService.getSequenceConfigurationByQuestionId(question.questionId).map(Option.apply)
      )
      operation <- OptionT(operationService.findOneSimple(operationOfQuestion.operationId))
      // in order to insert this in this for-comprehension correctly we need to transform the Future[Option[String]]
      // into a Future[Option[Option[String]]] since we want to keep an Option[String] in the end.
      segment <- OptionT(segmentService.resolveSegment(proposal.creationContext).map(Option(_)))
    } yield {
      createIndexedProposal(
        proposal,
        segment,
        sequenceConfiguration,
        user,
        organisationInfos,
        tagService.retrieveIndexedTags(tags, tagTypes),
        Proposal.needsEnrichment(proposal.status, tagTypes, tags.map(_.tagTypeId)),
        selectedStakeTag,
        question,
        operationOfQuestion,
        operation
      )
    }

    maybeResult.value
  }

  private def createIndexedProposal(
    proposal: Proposal,
    segment: Option[String],
    sequenceConfiguration: SequenceConfiguration,
    user: User,
    organisationInfos: Seq[User],
    tags: Seq[IndexedTag],
    needsEnrichment: Boolean,
    selectedStakeTag: Option[IndexedTag],
    question: Question,
    operationOfQuestion: OperationOfQuestion,
    operation: SimpleOperation
  ): IndexedProposal = {

    val isBeforeContextSourceFeature: Boolean =
      proposal.createdAt.exists(_.isBefore(ZonedDateTime.parse("2018-09-01T00:00:00Z")))
    val regularScore = ScoreCounts.fromSequenceVotes(proposal.votes)
    val scoreWithEverything = ScoreCounts.fromVerifiedVotes(proposal.votes)

    // If the proposal is not segmented, the scores should all be at 0
    val segmentScore = segment.map { _ =>
      ScoreCounts.fromSegmentVotes(proposal.votes)
    }.getOrElse(ScoreCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0))

    IndexedProposal(
      id = proposal.proposalId,
      userId = proposal.author,
      content = proposal.content,
      slug = proposal.slug,
      status = proposal.status,
      createdAt = proposal.createdAt match {
        case Some(date) => date
        case _          => throw new IllegalStateException("created at is required")
      },
      updatedAt = proposal.updatedAt,
      votes = proposal.votes.map(IndexedVote.apply),
      votesCount = proposal.votes.map(_.count).sum,
      votesVerifiedCount = proposal.votes.map(_.countVerified).sum,
      votesSequenceCount = proposal.votes.map(_.countSequence).sum,
      votesSegmentCount = proposal.votes.map(_.countSegment).sum,
      toEnrich = needsEnrichment,
      scores = IndexedScores(
        engagement = regularScore.engagement(),
        agreement = regularScore.agreement(),
        adhesion = regularScore.adhesion(),
        realistic = regularScore.realistic(),
        platitude = regularScore.platitude(),
        topScore = ScoreCounts.topScore(sequenceConfiguration, scoreWithEverything, regularScore),
        topScoreAjustedWithVotes = ScoreCounts.topScoreAjustedWithVotes(
          sequenceConfiguration,
          scoreWithEverything,
          regularScore,
          proposal.votes.map(_.countVerified).sum
        ),
        controversy = regularScore.controversy(),
        rejection = regularScore.rejection(),
        scoreUpperBound = ScoreCounts.topScoreUpperBound(sequenceConfiguration, scoreWithEverything, regularScore),
        scoreLowerBound = ScoreCounts.topScoreLowerBound(sequenceConfiguration, scoreWithEverything, regularScore)
      ),
      segmentScores = IndexedScores(
        engagement = segmentScore.engagement(),
        agreement = segmentScore.agreement(),
        adhesion = segmentScore.adhesion(),
        realistic = segmentScore.realistic(),
        platitude = segmentScore.platitude(),
        topScore = ScoreCounts.topScore(sequenceConfiguration, scoreWithEverything, segmentScore),
        topScoreAjustedWithVotes = ScoreCounts.topScoreAjustedWithVotes(
          sequenceConfiguration,
          scoreWithEverything,
          segmentScore,
          proposal.votes.map(_.countSegment).sum
        ),
        controversy = segmentScore.controversy(),
        rejection = segmentScore.rejection(),
        scoreUpperBound = ScoreCounts.topScoreUpperBound(sequenceConfiguration, scoreWithEverything, regularScore),
        scoreLowerBound = ScoreCounts.topScoreLowerBound(sequenceConfiguration, scoreWithEverything, regularScore)
      ),
      context = Some(
        ProposalContext(
          operation = proposal.creationContext.operationId,
          source = proposal.creationContext.source.filter(!_.isEmpty) match {
            case None if isBeforeContextSourceFeature => Some("core")
            case other                                => other
          },
          location = proposal.creationContext.location,
          question = proposal.creationContext.question,
          getParameters = proposal.creationContext.getParameters
            .map(_.toSeq.map {
              case (key, value) => IndexedGetParameters(key, value)
            })
            .getOrElse(Seq.empty)
        )
      ),
      trending = None,
      labels = proposal.labels.map(_.value),
      author = IndexedAuthor(
        firstName = user.firstName,
        displayName = user.displayName,
        organisationName = user.organisationName,
        organisationSlug = user.organisationName.map(name => SlugHelper(name)),
        postalCode = user.profile.flatMap(_.postalCode),
        age = user.profile
          .flatMap(_.dateOfBirth)
          .map(date => ChronoUnit.YEARS.between(date, LocalDate.now()).toInt),
        avatarUrl = user.profile.flatMap(_.avatarUrl),
        anonymousParticipation = user.anonymousParticipation,
        userType = user.userType
      ),
      organisations = organisationInfos
        .map(
          organisation =>
            IndexedOrganisationInfo(
              organisation.userId,
              organisation.organisationName,
              organisation.organisationName.map(name => SlugHelper(name))
            )
        ),
      country = proposal.country.getOrElse(Country("FR")),
      language = proposal.language.getOrElse(Language("fr")),
      tags = tags,
      selectedStakeTag = selectedStakeTag,
      ideaId = proposal.idea,
      operationId = proposal.operation,
      question = proposal.questionId.map(
        questionId =>
          IndexedProposalQuestion(
            questionId = questionId,
            slug = question.slug,
            title = operationOfQuestion.operationTitle,
            question = question.question,
            startDate = operationOfQuestion.startDate,
            endDate = operationOfQuestion.endDate,
            isOpen = operationOfQuestion.status == OperationOfQuestion.Status.Open
          )
      ),
      sequencePool = ProposalScorerHelper
        .sequencePool(sequenceConfiguration, proposal.status, ScoreCounts.fromSequenceVotes(proposal.votes)),
      sequenceSegmentPool = ProposalScorerHelper
        .sequencePool(sequenceConfiguration, proposal.status, ScoreCounts.fromSegmentVotes(proposal.votes)),
      initialProposal = proposal.initialProposal,
      refusalReason = proposal.refusalReason,
      operationKind = Option(operation.operationKind),
      segment = segment
    )
  }

  private def executeIndexProposals(proposals: Seq[IndexedProposal], indexName: String): Future[Done] = {
    elasticsearchProposalAPI
      .indexProposals(proposals, Some(IndexAndType(indexName, ProposalSearchEngine.proposalIndexName)))
      .flatMap { proposals =>
        semanticService.indexProposals(proposals).map(_ => Done)
      }
      .recoverWith {
        case e =>
          logger.error("Indexing proposals in proposal index OR in semantic index failed", e)
          Future.successful(Done)
      }
  }

}

sealed trait ProposalFlow {
  val proposal: IndexedProposal
}
case class IndexProposalFlow(override val proposal: IndexedProposal) extends ProposalFlow
case class UpdateProposalFlow(override val proposal: IndexedProposal) extends ProposalFlow
