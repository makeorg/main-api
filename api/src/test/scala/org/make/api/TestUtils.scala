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

import java.net.URL
import java.time.ZonedDateTime

import org.make.api.proposal.ProposalScorerHelper.ScoreCounts
import org.make.core.article.{Article, ArticleId}
import org.make.core.idea.IdeaId
import org.make.core.operation._
import org.make.core.post.{Post, PostId}
import org.make.core.profile.Profile
import org.make.core.proposal.ProposalStatus.Accepted
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
import org.make.core.proposal.VoteKey.{Agree, Disagree, Neutral}
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.user.Role.RoleCitizen
import org.make.core.user._
import org.make.core.{RequestContext, SlugHelper}

trait TestUtils {

  private val defaultVotes: Seq[Vote] = Seq(
    Vote(
      key = Agree,
      count = 0,
      countVerified = 0,
      countSegment = 0,
      countSequence = 0,
      qualifications = Seq(
        Qualification(key = LikeIt, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = Doable, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = PlatitudeAgree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
      )
    ),
    Vote(
      key = Disagree,
      count = 0,
      countVerified = 0,
      countSegment = 0,
      countSequence = 0,
      qualifications = Seq(
        Qualification(key = NoWay, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = Impossible, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = PlatitudeDisagree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
      )
    ),
    Vote(
      key = Neutral,
      count = 0,
      countVerified = 0,
      countSegment = 0,
      countSequence = 0,
      qualifications = Seq(
        Qualification(key = DoNotUnderstand, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = NoOpinion, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        Qualification(key = DoNotCare, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
      )
    )
  )

  private val defaultIndexedVotes: Seq[IndexedVote] = Seq(
    IndexedVote(
      key = Agree,
      count = 0,
      countVerified = 0,
      countSegment = 0,
      countSequence = 0,
      qualifications = Seq(
        IndexedQualification(key = LikeIt, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        IndexedQualification(key = Doable, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        IndexedQualification(key = PlatitudeAgree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
      )
    ),
    IndexedVote(
      key = Disagree,
      count = 0,
      countVerified = 0,
      countSegment = 0,
      countSequence = 0,
      qualifications = Seq(
        IndexedQualification(key = NoWay, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        IndexedQualification(key = Impossible, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        IndexedQualification(key = PlatitudeDisagree, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
      )
    ),
    IndexedVote(
      key = Neutral,
      count = 0,
      countVerified = 0,
      countSegment = 0,
      countSequence = 0,
      qualifications = Seq(
        IndexedQualification(key = DoNotUnderstand, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        IndexedQualification(key = NoOpinion, count = 0, countVerified = 0, countSequence = 0, countSegment = 0),
        IndexedQualification(key = DoNotCare, count = 0, countVerified = 0, countSequence = 0, countSegment = 0)
      )
    )
  )

  def proposal(
    id: ProposalId,
    votes: Seq[Vote] = defaultVotes,
    author: UserId = UserId("author"),
    tags: Seq[TagId] = Seq.empty,
    organisations: Seq[OrganisationInfo] = Seq.empty,
    questionId: QuestionId = QuestionId("question"),
    operationId: Option[OperationId] = Some(OperationId("operation")),
    requestContext: RequestContext = RequestContext.empty,
    content: String = "Il faut tester l'indexation des propositions",
    country: Country = Country("FR"),
    language: Language = Language("fr"),
    status: ProposalStatus = Accepted,
    idea: Option[IdeaId] = None,
    events: List[ProposalAction] = Nil,
    createdAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2019-10-10T10:10:10.000Z")),
    updatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2019-10-10T15:10:10.000Z"))
  ): Proposal = {
    Proposal(
      proposalId = id,
      votes = votes,
      content = content,
      slug = SlugHelper(content),
      author = author,
      labels = Seq.empty,
      status = status,
      refusalReason = None,
      tags = tags,
      organisations = organisations,
      organisationIds = organisations.map(_.organisationId),
      language = Some(language),
      country = Some(country),
      questionId = Some(questionId),
      creationContext = requestContext,
      idea = idea,
      operation = operationId,
      createdAt = createdAt,
      updatedAt = updatedAt,
      events = events
    )
  }

  private val defaultAuthor: IndexedAuthor = IndexedAuthor(
    firstName = Some("firstname"),
    displayName = Some("firstname"),
    organisationName = None,
    organisationSlug = None,
    postalCode = Some("12345"),
    age = Some(25),
    avatarUrl = Some("http://some-url"),
    anonymousParticipation = false,
    userType = UserType.UserTypeUser
  )

  def indexedProposal(
    id: ProposalId,
    votes: Seq[IndexedVote] = defaultIndexedVotes,
    userId: UserId = UserId("author"),
    author: IndexedAuthor = defaultAuthor,
    questionId: QuestionId = QuestionId("question-id"),
    operationId: Option[OperationId] = Some(OperationId("operation-id")),
    content: String = "Il faut tester l'indexation des propositions",
    country: Country = Country("FR"),
    language: Language = Language("fr"),
    status: ProposalStatus = Accepted,
    refusalReason: Option[String] = None,
    ideaId: Option[IdeaId] = None,
    selectedStakeTag: Option[IndexedTag] = None,
    initialProposal: Boolean = false,
    regularTopScore: Double = 0d,
    regularTopScoreAjustedWithVotes: Double = 0d,
    segmentTopScore: Double = 0d,
    segmentTopScoreAjustedWithVotes: Double = 0d,
    regularTopScoreUpperBound: Double = 0d,
    regularTopScoreLowerBound: Double = 0d,
    segmentTopScoreUpperBound: Double = 0d,
    segmentTopScoreLowerBound: Double = 0d,
    createdAt: ZonedDateTime = ZonedDateTime.parse("2019-10-10T10:10:10.000Z"),
    updatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2019-10-10T15:10:10.000Z")),
    toEnrich: Boolean = false
  ): IndexedProposal = {

    val regularScore = ScoreCounts.fromSequenceVotes(votes)
    val segmentScore = ScoreCounts.fromSegmentVotes(votes)

    IndexedProposal(
      id = id,
      userId = userId,
      content = content,
      slug = SlugHelper(content),
      status = status,
      createdAt = createdAt,
      updatedAt = updatedAt,
      votes = votes,
      votesCount = votes.map(_.count).sum,
      votesVerifiedCount = votes.map(_.countVerified).sum,
      votesSequenceCount = votes.map(_.countSequence).sum,
      votesSegmentCount = votes.map(_.countSegment).sum,
      toEnrich = toEnrich,
      scores = IndexedScores(
        engagement = regularScore.engagement(),
        agreement = regularScore.agreement(),
        adhesion = regularScore.adhesion(),
        realistic = regularScore.realistic(),
        platitude = regularScore.platitude(),
        topScore = regularTopScore,
        topScoreAjustedWithVotes = regularTopScoreAjustedWithVotes,
        controversy = regularScore.controversy(),
        rejection = regularScore.rejection(),
        scoreUpperBound = regularTopScoreUpperBound,
        scoreLowerBound = regularTopScoreLowerBound
      ),
      segmentScores = IndexedScores(
        engagement = segmentScore.engagement(),
        agreement = segmentScore.agreement(),
        adhesion = segmentScore.adhesion(),
        realistic = segmentScore.realistic(),
        platitude = segmentScore.platitude(),
        topScore = segmentTopScore,
        topScoreAjustedWithVotes = segmentTopScoreAjustedWithVotes,
        controversy = segmentScore.controversy(),
        rejection = segmentScore.rejection(),
        scoreUpperBound = segmentTopScoreUpperBound,
        scoreLowerBound = segmentTopScoreLowerBound
      ),
      context = None,
      trending = None,
      labels = Seq.empty,
      author = author,
      organisations = Seq.empty,
      country = country,
      language = language,
      tags = Seq.empty,
      selectedStakeTag = selectedStakeTag,
      ideaId = ideaId,
      operationId = operationId,
      question = Some(
        IndexedProposalQuestion(
          questionId = questionId,
          slug = questionId.value,
          title = questionId.value,
          question = questionId.value,
          startDate = None,
          endDate = None,
          isOpen = true
        )
      ),
      sequencePool = SequencePool.New,
      sequenceSegmentPool = SequencePool.New,
      initialProposal = initialProposal,
      refusalReason = refusalReason,
      operationKind = None,
      segment = None
    )
  }

  def user(
    id: UserId,
    anonymousParticipation: Boolean = false,
    email: String = "test@make.org",
    firstName: Option[String] = Some("Joe"),
    lastName: Option[String] = Some("Chip"),
    lastIp: Option[String] = None,
    hashedPassword: Option[String] = None,
    enabled: Boolean = true,
    emailVerified: Boolean = true,
    lastConnection: ZonedDateTime = ZonedDateTime.parse("1992-08-23T02:02:02.020Z"),
    verificationToken: Option[String] = None,
    verificationTokenExpiresAt: Option[ZonedDateTime] = None,
    resetToken: Option[String] = None,
    resetTokenExpiresAt: Option[ZonedDateTime] = None,
    roles: Seq[Role] = Seq(RoleCitizen),
    country: Country = Country("FR"),
    language: Language = Language("fr"),
    profile: Option[Profile] = None,
    createdAt: Option[ZonedDateTime] = None,
    updatedAt: Option[ZonedDateTime] = None,
    lastMailingError: Option[MailingErrorLog] = None,
    organisationName: Option[String] = None,
    availableQuestions: Seq[QuestionId] = Seq.empty,
    userType: UserType = UserType.UserTypeUser
  ): User = {
    User(
      userId = id,
      email = email,
      firstName = firstName,
      lastName = lastName,
      lastIp = lastIp,
      hashedPassword = hashedPassword,
      enabled = enabled,
      emailVerified = emailVerified,
      lastConnection = lastConnection,
      verificationToken = verificationToken,
      verificationTokenExpiresAt = verificationTokenExpiresAt,
      resetToken = resetToken,
      resetTokenExpiresAt = resetTokenExpiresAt,
      roles = roles,
      country = country,
      language = language,
      profile = profile,
      createdAt = createdAt,
      updatedAt = updatedAt,
      lastMailingError = lastMailingError,
      organisationName = organisationName,
      availableQuestions = availableQuestions,
      anonymousParticipation = anonymousParticipation,
      userType = userType
    )
  }

  def question(
    id: QuestionId,
    slug: String = "question-slug",
    country: Country = Country("FR"),
    language: Language = Language("fr"),
    question: String = "How to ask a question ?",
    shortTitle: Option[String] = None,
    operationId: Option[OperationId] = None
  ): Question =
    Question(
      questionId = id,
      slug = slug,
      country = country,
      language = language,
      question = question,
      shortTitle = shortTitle,
      operationId = operationId
    )

  def simpleOperation(
    id: OperationId,
    status: OperationStatus = OperationStatus.Active,
    slug: String = "operation-slug",
    allowedSources: Seq[String] = Seq.empty,
    defaultLanguage: Language = Language("fr"),
    operationKind: OperationKind = OperationKind.PublicConsultation,
    createdAt: Option[ZonedDateTime] = None,
    updatedAt: Option[ZonedDateTime] = None
  ): SimpleOperation =
    SimpleOperation(
      operationId = id,
      status = status,
      slug = slug,
      defaultLanguage = defaultLanguage,
      allowedSources = allowedSources,
      operationKind = operationKind,
      createdAt = createdAt,
      updatedAt = updatedAt
    )

  val defaultMetas = Metas(title = Some("Metas title"), description = Some("Meta description"), picture = None)
  def operationOfQuestion(
    questionId: QuestionId,
    operationId: OperationId,
    startDate: Option[ZonedDateTime] = None,
    endDate: Option[ZonedDateTime] = None,
    operationTitle: String = "operation title",
    landingSequenceId: SequenceId = SequenceId("sequence-id"),
    canPropose: Boolean = true,
    sequenceCardsConfiguration: SequenceCardsConfiguration = SequenceCardsConfiguration.default,
    aboutUrl: Option[String] = None,
    metas: Metas = defaultMetas,
    theme: QuestionTheme = QuestionTheme.default,
    description: String = OperationOfQuestion.defaultDescription,
    consultationImage: Option[String] = Some("image-url"),
    descriptionImage: Option[String] = None,
    displayResults: Boolean = false,
    resultsLink: Option[String] = None,
    proposalsCount: Int = 42,
    participantsCount: Int = 84,
    actions: Option[String] = None,
    featured: Boolean = true
  ) = OperationOfQuestion(
    questionId = questionId,
    operationId = operationId,
    startDate = startDate,
    endDate = endDate,
    operationTitle = operationTitle,
    landingSequenceId = landingSequenceId,
    canPropose = canPropose,
    sequenceCardsConfiguration = sequenceCardsConfiguration,
    aboutUrl = aboutUrl,
    metas = metas,
    theme = theme,
    description = description,
    consultationImage = consultationImage,
    descriptionImage = descriptionImage,
    displayResults = displayResults,
    resultsLink = resultsLink,
    proposalsCount = proposalsCount,
    participantsCount = participantsCount,
    actions = actions,
    featured = featured
  )

  def postGen(
    postId: PostId,
    name: String = "post name",
    slug: String = "post-slug",
    displayHome: Boolean = true,
    postDate: ZonedDateTime = ZonedDateTime.parse("2020-06-10T10:10:10.000Z"),
    thumbnailUrl: URL = new URL("https://example.com/thumbnail"),
    thumbnailAlt: Option[String] = Some("image alternative"),
    sourceUrl: URL = new URL("https://example.com/source"),
    summary: String = "This is a summary for an awesome post."
  ): Post = Post(
    postId = postId,
    name = name,
    slug = slug,
    displayHome = displayHome,
    postDate = postDate,
    thumbnailUrl = thumbnailUrl,
    thumbnailAlt = thumbnailAlt,
    sourceUrl = sourceUrl,
    summary = summary
  )

}

object TestUtils extends TestUtils
