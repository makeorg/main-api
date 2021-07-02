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

import cats.data.NonEmptyList
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.MaxSize
import eu.timepit.refined.types.numeric.NonNegInt
import org.make.api.proposal.ProposalScorer
import org.make.api.proposal.ProposalScorer.VotesCounter
import org.make.api.technical.elasticsearch.ProposalIndexationStream
import org.make.core.sequence.{
  ExplorationSequenceConfiguration,
  ExplorationSequenceConfigurationId,
  SequenceConfiguration,
  SequenceId,
  SpecificSequenceConfiguration,
  SpecificSequenceConfigurationId
}
import org.make.core.auth.{Client, ClientId}
import org.make.core.idea.{Idea, IdeaId, IdeaStatus}
import org.make.core.keyword.Keyword
import org.make.core.operation._
import org.make.core.operation.indexed.IndexedOperationOfQuestion
import org.make.core.post.indexed.IndexedPost
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
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.{Tag, TagDisplay, TagId, TagType, TagTypeId}
import org.make.core.user.Role.RoleCitizen
import org.make.core.user._
import org.make.core.{RequestContext, SlugHelper}

import java.net.URL
import java.time.ZonedDateTime

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
    status: ProposalStatus = Accepted,
    refusalReason: Option[String] = None,
    idea: Option[IdeaId] = None,
    events: List[ProposalAction] = Nil,
    theme: Option[ThemeId] = None,
    createdAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2019-10-10T10:10:10.000Z")),
    updatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2019-10-10T15:10:10.000Z")),
    keywords: Seq[ProposalKeyword] = Nil
  ): Proposal = {
    Proposal(
      proposalId = id,
      votes = votes,
      content = content,
      slug = SlugHelper(content),
      author = author,
      labels = Seq.empty,
      theme = theme,
      status = status,
      refusalReason = refusalReason,
      tags = tags,
      organisations = organisations,
      organisationIds = organisations.map(_.organisationId),
      questionId = Some(questionId),
      creationContext = requestContext,
      idea = idea,
      operation = operationId,
      createdAt = createdAt,
      updatedAt = updatedAt,
      events = events,
      keywords = keywords
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
    userType = UserType.UserTypeUser,
    profession = Some("Tarot Reader")
  )

  def indexedProposal(
    id: ProposalId,
    votes: Seq[IndexedVote] = defaultIndexedVotes,
    userId: UserId = UserId("author"),
    author: IndexedAuthor = defaultAuthor,
    questionId: QuestionId = QuestionId("question-id"),
    operationId: Option[OperationId] = Some(OperationId("operation-id")),
    startDate: ZonedDateTime = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
    endDate: ZonedDateTime = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
    requestContext: Option[RequestContext] = None,
    content: String = "Il faut tester l'indexation des propositions",
    countries: NonEmptyList[Country] = NonEmptyList.of(Country("FR")),
    language: Language = Language("fr"),
    status: ProposalStatus = Accepted,
    refusalReason: Option[String] = None,
    ideaId: Option[IdeaId] = None,
    selectedStakeTag: Option[IndexedTag] = None,
    initialProposal: Boolean = false,
    createdAt: ZonedDateTime = ZonedDateTime.parse("2019-10-10T10:10:10.000Z"),
    updatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2019-10-10T15:10:10.000Z")),
    toEnrich: Boolean = false,
    keywords: Seq[IndexedProposalKeyword] = Nil,
    tags: Seq[IndexedTag] = Nil,
    sequencePool: SequencePool = SequencePool.New,
    sequenceSegmentPool: SequencePool = SequencePool.New,
    segment: Option[String] = None
  ): IndexedProposal = {

    val regularScore = ProposalScorer(votes, VotesCounter.SequenceVotesCounter, 0.5)
    val segmentScore = ProposalScorer(votes, VotesCounter.SegmentVotesCounter, 0.5)

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
      scores = ProposalIndexationStream.buildScore(regularScore),
      segmentScores = ProposalIndexationStream.buildScore(segmentScore),
      context = requestContext.map(IndexedContext.apply(_, false)),
      trending = None,
      labels = Seq.empty,
      author = author,
      organisations = Seq.empty,
      tags = tags,
      selectedStakeTag = selectedStakeTag,
      ideaId = ideaId,
      operationId = operationId,
      question = Some(
        IndexedProposalQuestion(
          questionId = questionId,
          slug = questionId.value,
          title = questionId.value,
          question = questionId.value,
          countries = countries,
          language = language,
          startDate = startDate,
          endDate = endDate,
          isOpen = true
        )
      ),
      sequencePool = sequencePool,
      sequenceSegmentPool = sequenceSegmentPool,
      initialProposal = initialProposal,
      refusalReason = refusalReason,
      operationKind = None,
      segment = segment,
      keywords = keywords
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
    lastConnection: Option[ZonedDateTime] = Some(ZonedDateTime.parse("1992-08-23T02:02:02.020Z")),
    verificationToken: Option[String] = None,
    verificationTokenExpiresAt: Option[ZonedDateTime] = None,
    resetToken: Option[String] = None,
    resetTokenExpiresAt: Option[ZonedDateTime] = None,
    roles: Seq[Role] = Seq(RoleCitizen),
    country: Country = Country("FR"),
    profile: Option[Profile] = None,
    createdAt: Option[ZonedDateTime] = None,
    updatedAt: Option[ZonedDateTime] = None,
    lastMailingError: Option[MailingErrorLog] = None,
    organisationName: Option[String] = None,
    availableQuestions: Seq[QuestionId] = Seq.empty,
    userType: UserType = UserType.UserTypeUser,
    privacyPolicyApprovalDate: Option[ZonedDateTime] = None
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
      profile = profile,
      createdAt = createdAt,
      updatedAt = updatedAt,
      lastMailingError = lastMailingError,
      organisationName = organisationName,
      availableQuestions = availableQuestions,
      anonymousParticipation = anonymousParticipation,
      userType = userType,
      privacyPolicyApprovalDate = privacyPolicyApprovalDate
    )
  }

  def question(
    id: QuestionId,
    slug: String = "question-slug",
    countries: NonEmptyList[Country] = NonEmptyList.of(Country("FR")),
    language: Language = Language("fr"),
    question: String = "How to ask a question ?",
    shortTitle: Option[String] = None,
    operationId: Option[OperationId] = None
  ): Question =
    Question(
      questionId = id,
      slug = slug,
      countries = countries,
      language = language,
      question = question,
      shortTitle = shortTitle,
      operationId = operationId
    )

  def simpleOperation(
    id: OperationId,
    status: OperationStatus = OperationStatus.Active,
    slug: String = "operation-slug",
    operationKind: OperationKind = OperationKind.BusinessConsultation,
    createdAt: Option[ZonedDateTime] = None,
    updatedAt: Option[ZonedDateTime] = None
  ): SimpleOperation =
    SimpleOperation(
      operationId = id,
      status = status,
      slug = slug,
      operationKind = operationKind,
      createdAt = createdAt,
      updatedAt = updatedAt
    )

  def operation(
    operationId: OperationId,
    status: OperationStatus = OperationStatus.Active,
    slug: String = "operation-slug",
    events: List[OperationAction] = List.empty,
    questions: Seq[QuestionWithDetails] = Seq.empty,
    operationKind: OperationKind = OperationKind.BusinessConsultation,
    createdAt: Option[ZonedDateTime] = None,
    updatedAt: Option[ZonedDateTime] = None
  ): Operation = Operation(
    operationId = operationId,
    status = status,
    slug = slug,
    operationKind = operationKind,
    createdAt = createdAt,
    updatedAt = updatedAt,
    events = events,
    questions = questions
  )

  val defaultMetas: Metas = Metas(title = Some("Metas title"), description = Some("Meta description"), picture = None)
  def operationOfQuestion(
    questionId: QuestionId,
    operationId: OperationId,
    startDate: ZonedDateTime = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
    endDate: ZonedDateTime = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
    operationTitle: String = "operation title",
    landingSequenceId: SequenceId = SequenceId("sequence-id"),
    canPropose: Boolean = true,
    sequenceCardsConfiguration: SequenceCardsConfiguration = SequenceCardsConfiguration.default,
    aboutUrl: Option[String] = None,
    metas: Metas = defaultMetas,
    theme: QuestionTheme = QuestionTheme.default,
    description: String = OperationOfQuestion.defaultDescription,
    consultationImage: Option[String] = Some("image-url"),
    consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]] = Some("image alternative"),
    descriptionImage: Option[String] = None,
    descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]] = None,
    resultsLink: Option[ResultsLink] = None,
    proposalsCount: Int = 42,
    participantsCount: Int = 84,
    actions: Option[String] = None,
    featured: Boolean = false,
    votesCount: Int = 0,
    votesTarget: Int = 100_000,
    timeline: OperationOfQuestionTimeline = OperationOfQuestionTimeline(None, None, None),
    createdAt: ZonedDateTime = ZonedDateTime.parse("1968-06-03T00:00:00.000Z")
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
    consultationImageAlt = consultationImageAlt,
    descriptionImage = descriptionImage,
    descriptionImageAlt = descriptionImageAlt,
    resultsLink = resultsLink,
    proposalsCount = proposalsCount,
    participantsCount = participantsCount,
    actions = actions,
    featured = featured,
    votesCount = votesCount,
    votesTarget = votesTarget,
    timeline = timeline,
    createdAt = createdAt
  )

  def indexedOperationOfQuestion(
    questionId: QuestionId,
    operationId: OperationId,
    question: String = "What's the question?",
    slug: String = "question-slug",
    questionShortTitle: Option[String] = Some("short title"),
    startDate: ZonedDateTime = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
    endDate: ZonedDateTime = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
    status: OperationOfQuestion.Status = OperationOfQuestion.Status.Open,
    theme: QuestionTheme = QuestionTheme.default,
    description: String = OperationOfQuestion.defaultDescription,
    consultationImage: Option[String] = Some("image-url"),
    consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]] = Some("image alternative"),
    descriptionImage: Option[String] = None,
    descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]] = None,
    countries: NonEmptyList[Country] = NonEmptyList(Country("FR"), List.empty),
    language: Language = Language("fr"),
    operationTitle: String = "operation title",
    operationKind: String = OperationKind.GreatCause.value,
    aboutUrl: Option[String] = None,
    resultsLink: Option[String] = None,
    proposalsCount: Int = 42,
    participantsCount: Int = 84,
    actions: Option[String] = None,
    featured: Boolean = true,
    top20ConsensusThreshold: Option[Double] = None
  ) = IndexedOperationOfQuestion(
    questionId = questionId,
    question = question,
    slug = slug,
    questionShortTitle = questionShortTitle,
    startDate = startDate,
    endDate = endDate,
    status = status,
    theme = theme,
    description = description,
    consultationImage = consultationImage,
    consultationImageAlt = consultationImageAlt,
    descriptionImage = descriptionImage,
    descriptionImageAlt = descriptionImageAlt,
    countries = countries,
    language = language,
    operationId = operationId,
    operationTitle = operationTitle,
    operationKind = operationKind,
    aboutUrl = aboutUrl,
    resultsLink = resultsLink,
    proposalsCount = proposalsCount,
    participantsCount = participantsCount,
    actions = actions,
    featured = featured,
    top20ConsensusThreshold = top20ConsensusThreshold
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
    summary: String = "This is a summary for an awesome post.",
    country: Country = Country("FR")
  ): Post = Post(
    postId = postId,
    name = name,
    slug = slug,
    displayHome = displayHome,
    postDate = postDate,
    thumbnailUrl = thumbnailUrl,
    thumbnailAlt = thumbnailAlt,
    sourceUrl = sourceUrl,
    summary = summary,
    country = country
  )

  def indexedPost(
    postId: PostId,
    name: String = "post name",
    slug: String = "post-slug",
    displayHome: Boolean = true,
    postDate: ZonedDateTime = ZonedDateTime.parse("2020-06-10T10:10:10.000Z"),
    thumbnailUrl: URL = new URL("https://example.com/thumbnail"),
    thumbnailAlt: Option[String] = Some("image alternative"),
    sourceUrl: URL = new URL("https://example.com/source"),
    summary: String = "This is a summary for an awesome post.",
    country: Country = Country("FR")
  ): IndexedPost =
    IndexedPost(
      postId = postId,
      name = name,
      slug = slug,
      displayHome = displayHome,
      postDate = postDate,
      thumbnailUrl = thumbnailUrl,
      thumbnailAlt = thumbnailAlt,
      sourceUrl = sourceUrl,
      summary = summary,
      country = country
    )

  def client(
    clientId: ClientId,
    name: String = "default",
    allowedGrantTypes: Seq[String] = Seq.empty,
    secret: Option[String] = None,
    scope: Option[String] = None,
    redirectUri: Option[String] = None,
    defaultUserId: Option[UserId] = None,
    roles: Seq[Role] = Seq.empty,
    tokenExpirationSeconds: Int = 300,
    refreshExpirationSeconds: Int = 400,
    reconnectExpirationSeconds: Int = 900
  ): Client = {
    Client(
      clientId = clientId,
      name = name,
      allowedGrantTypes = allowedGrantTypes,
      secret = secret,
      scope = scope,
      redirectUri = redirectUri,
      defaultUserId = defaultUserId,
      roles = roles,
      tokenExpirationSeconds = tokenExpirationSeconds,
      refreshExpirationSeconds = refreshExpirationSeconds,
      reconnectExpirationSeconds = reconnectExpirationSeconds
    )
  }

  def idea(
    ideaId: IdeaId,
    name: String = "idea name",
    question: Option[String] = None,
    operationId: Option[OperationId] = None,
    questionId: Option[QuestionId] = None,
    status: IdeaStatus = IdeaStatus.Activated,
    createdAt: Option[ZonedDateTime] = None,
    updatedAt: Option[ZonedDateTime] = None
  ): Idea =
    Idea(
      ideaId = ideaId,
      name = name,
      question = question,
      operationId = operationId,
      questionId = questionId,
      status = status,
      createdAt = createdAt,
      updatedAt = updatedAt
    )

  def tag(
    tagId: TagId,
    label: String = "tag label",
    display: TagDisplay = TagDisplay.Inherit,
    tagTypeId: TagTypeId = TagType.LEGACY.tagTypeId,
    weight: Float = 42f,
    operationId: Option[OperationId] = None,
    questionId: Option[QuestionId] = None
  ): Tag = Tag(
    tagId = tagId,
    label = label,
    display = display,
    tagTypeId = tagTypeId,
    weight = weight,
    operationId = operationId,
    questionId = questionId
  )

  def sequenceConfiguration(
    questionId: QuestionId,
    sequenceId: SequenceId = SequenceId("deprecated-sequence-id"),
    mainSequence: ExplorationSequenceConfiguration =
      ExplorationSequenceConfiguration.default(ExplorationSequenceConfigurationId("main-id")),
    controversial: SpecificSequenceConfiguration = SpecificSequenceConfiguration(
      SpecificSequenceConfigurationId("controversial-id")
    ),
    popular: SpecificSequenceConfiguration = SpecificSequenceConfiguration(
      SpecificSequenceConfigurationId("popular-id")
    ),
    keyword: SpecificSequenceConfiguration = SpecificSequenceConfiguration(
      SpecificSequenceConfigurationId("keyword-id")
    ),
    newProposalsVoteThreshold: Int = 10,
    testedProposalsEngagementThreshold: Option[Double] = None,
    testedProposalsScoreThreshold: Option[Double] = None,
    testedProposalsControversyThreshold: Option[Double] = None,
    testedProposalsMaxVotesThreshold: Option[Int] = None,
    nonSequenceVotesWeight: Double = 0.5
  ): SequenceConfiguration =
    SequenceConfiguration(
      sequenceId = sequenceId,
      questionId = questionId,
      mainSequence = mainSequence,
      controversial = controversial,
      popular = popular,
      keyword = keyword,
      newProposalsVoteThreshold = newProposalsVoteThreshold,
      testedProposalsEngagementThreshold = testedProposalsEngagementThreshold,
      testedProposalsScoreThreshold = testedProposalsScoreThreshold,
      testedProposalsControversyThreshold = testedProposalsControversyThreshold,
      testedProposalsMaxVotesThreshold = testedProposalsMaxVotesThreshold,
      nonSequenceVotesWeight = nonSequenceVotesWeight
    )

  def keyword(
    questionId: QuestionId,
    key: String,
    label: String = "label",
    score: Float = 0.42f,
    count: NonNegInt = 21,
    topKeyword: Boolean = false
  ): Keyword =
    Keyword(questionId = questionId, key = key, label = label, score = score, count = count, topKeyword = topKeyword)

}

object TestUtils extends TestUtils
