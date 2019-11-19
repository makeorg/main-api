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

import java.time.ZonedDateTime

import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
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
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{MailingErrorLog, Role, User, UserId}
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
    ),
  )

  def proposal(id: ProposalId,
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
               theme: Option[ThemeId] = None,
               createdAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2019-10-10T10:10:10.000Z")),
               updatedAt: Option[ZonedDateTime] = Some(ZonedDateTime.parse("2019-10-10T15:10:10.000Z"))): Proposal = {
    Proposal(
      proposalId = id,
      votes = votes,
      content = content,
      slug = SlugHelper(content),
      author = author,
      labels = Seq.empty,
      theme = theme,
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

  def user(id: UserId,
           anonymousParticipation: Boolean = false,
           email: String = "test@make.org",
           firstName: Option[String] = Some("Joe"),
           lastName: Option[String] = Some("Chip"),
           lastIp: Option[String] = None,
           hashedPassword: Option[String] = None,
           enabled: Boolean = true,
           emailVerified: Boolean = true,
           lastConnexion: ZonedDateTime = ZonedDateTime.parse("1992-08-23T02:02:02.020Z"),
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
           availableQuestions: Seq[QuestionId] = Seq.empty): User = {
    User(
      userId = id,
      email = email,
      firstName = firstName,
      lastName = lastName,
      lastIp = lastIp,
      hashedPassword = hashedPassword,
      enabled = enabled,
      emailVerified = emailVerified,
      lastConnection = lastConnexion,
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
      anonymousParticipation = anonymousParticipation
    )
  }
}

object TestUtils extends TestUtils
