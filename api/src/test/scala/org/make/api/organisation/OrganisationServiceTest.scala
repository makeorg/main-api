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

package org.make.api.organisation

import java.time.ZonedDateTime

import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import org.make.api.MakeUnitTest
import org.make.api.proposal.{
  ProposalResponse,
  ProposalService,
  ProposalServiceComponent,
  ProposalsResultSeededResponse
}
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGenerator, IdGeneratorComponent}
import org.make.api.user.DefaultPersistentUserServiceComponent.UpdateFailed
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user._
import org.make.api.userhistory.UserEvent.{
  OrganisationInitializationEvent,
  OrganisationRegisteredEvent,
  OrganisationUpdatedEvent
}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.core.history.HistoryActions.{Trusted, VoteAndQualifications}
import org.make.core.profile.Profile
import org.make.core.proposal.VoteKey.{Agree, Disagree}
import org.make.core.proposal.indexed.{Author, IndexedProposal, IndexedScores, SequencePool}
import org.make.core.proposal.{ProposalId, ProposalStatus, SearchQuery}
import org.make.core.reference.{Country, Language}
import org.make.core.user.Role.RoleActor
import org.make.core.user._
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify}
import org.mockito.{ArgumentMatcher, ArgumentMatchers, Mockito}
import org.scalatest.RecoverMethods
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class OrganisationServiceTest
    extends MakeUnitTest
    with DefaultOrganisationServiceComponent
    with UserServiceComponent
    with IdGeneratorComponent
    with PersistentUserServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ProposalServiceComponent
    with EventBusServiceComponent
    with OrganisationSearchEngineComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val userService: UserService = mock[UserService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val elasticsearchOrganisationAPI: OrganisationSearchEngine = mock[OrganisationSearchEngine]

  class MatchRegisterEvents(maybeUserId: Option[UserId]) extends ArgumentMatcher[AnyRef] {
    override def matches(argument: AnyRef): Boolean =
      argument match {
        case i: OrganisationRegisteredEvent if maybeUserId.contains(i.userId) => true
        case _                                                                => false
      }
  }

  class MatchOrganisationAskPasswordEvents(maybeUserId: Option[UserId]) extends ArgumentMatcher[AnyRef] {
    override def matches(argument: AnyRef): Boolean =
      argument match {
        case i: OrganisationInitializationEvent if maybeUserId.contains(i.userId) => true
        case _                                                                    => false
      }
  }

  val returnedOrganisation = User(
    userId = UserId("AAA-BBB-CCC"),
    email = "any@mail.com",
    firstName = None,
    lastName = None,
    organisationName = Some("John Doe Corp."),
    lastIp = None,
    hashedPassword = Some("passpass"),
    enabled = true,
    emailVerified = true,
    isOrganisation = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleActor),
    country = Country("FR"),
    language = Language("fr"),
    profile = Some(Profile(None, Some("avatarUrl"), None, None, None, None, None, None, None, None, None, None, None)),
    availableQuestions = Seq.empty
  )

  val returnedOrganisation2 = User(
    userId = UserId("AAA-BBB-CCC-DDD"),
    email = "some@mail.com",
    firstName = None,
    lastName = None,
    organisationName = Some("Jeanne Done Corp."),
    lastIp = None,
    hashedPassword = Some("passpass"),
    enabled = true,
    emailVerified = true,
    isOrganisation = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleActor),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    availableQuestions = Seq.empty
  )

  feature("Get organisation") {
    scenario("get organisation") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))

      whenReady(organisationService.getOrganisation(UserId("AAA-BBB-CCC")), Timeout(2.seconds)) { user =>
        user shouldBe a[Option[_]]
        user.isDefined shouldBe true
        user.get.email shouldBe "any@mail.com"
      }
    }
  }

  feature("register organisation") {
    scenario("successfully register an organisation with password") {
      Mockito.reset(eventBusService)
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      Mockito
        .when(
          persistentUserService
            .persist(any[User])
        )
        .thenReturn(Future.successful(returnedOrganisation))

      val futureOrganisation = organisationService.register(
        OrganisationRegisterData(
          name = "John Doe Corp.",
          email = "any@mail.com",
          password = Some("passopasso"),
          avatar = None,
          description = None,
          country = Country("FR"),
          language = Language("fr")
        ),
        RequestContext.empty
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be("any@mail.com")
        user.organisationName should be(Some("John Doe Corp."))
      }

      verify(eventBusService, times(1))
        .publish(
          ArgumentMatchers
            .argThat(new MatchRegisterEvents(Some(returnedOrganisation.userId)))
        )
    }

    scenario("successfully register an organisation without password") {
      Mockito.reset(eventBusService)
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      Mockito
        .when(
          persistentUserService
            .persist(any[User])
        )
        .thenReturn(Future.successful(returnedOrganisation))

      Mockito.when(userService.requestPasswordReset(any[UserId])).thenReturn(Future.successful(true))

      val futureOrganisation = organisationService.register(
        OrganisationRegisterData(
          name = "John Doe Corp.",
          email = "any@mail.com",
          password = None,
          avatar = None,
          description = None,
          country = Country("FR"),
          language = Language("fr")
        ),
        RequestContext.empty
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be("any@mail.com")
        user.organisationName should be(Some("John Doe Corp."))
      }

      verify(eventBusService, times(1))
        .publish(
          ArgumentMatchers
            .argThat(new MatchRegisterEvents(Some(returnedOrganisation.userId)))
        )

      verify(eventBusService, times(1))
        .publish(
          ArgumentMatchers
            .argThat(new MatchOrganisationAskPasswordEvents(Some(returnedOrganisation.userId)))
        )
    }

    scenario("email already exists") {
      Mockito.reset(eventBusService)
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))

      val futureOrganisation = organisationService.register(
        OrganisationRegisterData(
          name = "John Doe Corp.",
          email = "any@mail.com",
          password = Some("passopasso"),
          avatar = None,
          description = None,
          country = Country("FR"),
          language = Language("fr")
        ),
        RequestContext.empty
      )

      RecoverMethods.recoverToSucceededIf[EmailAlreadyRegisteredException](futureOrganisation)

      verify(eventBusService, times(0))
        .publish(
          ArgumentMatchers
            .argThat(new MatchRegisterEvents(Some(returnedOrganisation.userId)))
        )
    }
  }

  feature("update organisation") {
    scenario("successfully update an organisation by changing the name and avatar") {
      Mockito.reset(eventBusService)
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))
      Mockito.when(persistentUserService.modify(any[User])).thenReturn(Future.successful(Right(returnedOrganisation)))
      Mockito
        .when(proposalService.searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext]))
        .thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))
      Mockito
        .when(userHistoryCoordinatorService.retrieveVotedProposals(any[RequestUserVotedProposals]))
        .thenReturn(Future.successful(Seq.empty))
      Mockito
        .when(userHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestVoteValues]))
        .thenReturn(Future.successful(Map[ProposalId, VoteAndQualifications]()))

      val futureOrganisation =
        organisationService.update(returnedOrganisation, Some(returnedOrganisation.email), RequestContext.empty)

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisation =>
        organisation shouldBe a[UserId]
      }
      verify(eventBusService, times(1))
        .publish(ArgumentMatchers.any[OrganisationUpdatedEvent])
    }

    scenario("successfully update an organisation without changing anything") {
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))
      Mockito.when(persistentUserService.modify(any[User])).thenReturn(Future.successful(Right(returnedOrganisation)))

      val futureOrganisation =
        organisationService.update(returnedOrganisation, Some(returnedOrganisation.email), RequestContext.empty)

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisation =>
        organisation shouldBe a[UserId]
      }
    }

    scenario("try to update with mail already exists") {
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))

      val futureOrganisation =
        organisationService.update(returnedOrganisation, Some(returnedOrganisation.email), RequestContext.empty)

      RecoverMethods.recoverToSucceededIf[EmailAlreadyRegisteredException](futureOrganisation)
    }

    scenario("Fail update") {
      Mockito.when(persistentUserService.modify(any[User])).thenReturn(Future.successful(Left(UpdateFailed())))

      val futureOrganisation =
        organisationService.update(returnedOrganisation, Some(returnedOrganisation.email), RequestContext.empty)

      RecoverMethods.recoverToSucceededIf[UpdateFailed](futureOrganisation)
    }
  }

  feature("Get organisations") {
    scenario("get organisations") {
      Mockito
        .when(persistentUserService.findAllOrganisations())
        .thenReturn(Future.successful(Seq(returnedOrganisation, returnedOrganisation2)))

      whenReady(organisationService.getOrganisations, Timeout(2.seconds)) { organisationList =>
        organisationList shouldBe a[Seq[_]]
        organisationList.size shouldBe 2
        organisationList.head.email shouldBe "any@mail.com"
      }
    }
  }

  feature("search organisations") {
    Mockito
      .when(elasticsearchOrganisationAPI.searchOrganisations(ArgumentMatchers.eq(OrganisationSearchQuery())))
      .thenReturn(
        Future.successful(
          OrganisationSearchResult(
            total = 2L,
            results = Seq(
              IndexedOrganisation.createFromOrganisation(returnedOrganisation),
              IndexedOrganisation.createFromOrganisation(returnedOrganisation2)
            )
          )
        )
      )
    Mockito
      .when(
        elasticsearchOrganisationAPI.searchOrganisations(
          ArgumentMatchers.eq(
            OrganisationSearchQuery(
              filters = Some(
                OrganisationSearchFilters(
                  organisationName = Some(OrganisationNameSearchFilter("John Doe Corp.", Some(Fuzziness.Auto)))
                )
              )
            )
          )
        )
      )
      .thenReturn(
        Future.successful(
          OrganisationSearchResult(
            total = 1L,
            results = Seq(IndexedOrganisation.createFromOrganisation(returnedOrganisation))
          )
        )
      )
    Mockito
      .when(
        elasticsearchOrganisationAPI.searchOrganisations(
          ArgumentMatchers.eq(
            OrganisationSearchQuery(
              filters = Some(
                OrganisationSearchFilters(
                  organisationIds = Some(OrganisationIdsSearchFilter(Seq(UserId("AAA-BBB-CCC"))))
                )
              )
            )
          )
        )
      )
      .thenReturn(
        Future.successful(
          OrganisationSearchResult(
            total = 1L,
            results = Seq(IndexedOrganisation.createFromOrganisation(returnedOrganisation))
          )
        )
      )

    scenario("search all") {
      val futureAllOrganisation = organisationService.search(None, None, None)

      whenReady(futureAllOrganisation, Timeout(2.seconds)) { organisationsList =>
        organisationsList.total shouldBe 2
      }
    }

    scenario("search by organisationName") {
      val futureJohnDoeCorp = organisationService.search(Some("John Doe Corp."), None, None)

      whenReady(futureJohnDoeCorp, Timeout(2.seconds)) { organisationsList =>
        organisationsList.total shouldBe 1
        organisationsList.results.head.organisationName shouldBe Some("John Doe Corp.")
      }
    }

    scenario("search by organisationIds") {
      val futureJohnDoeCorp = organisationService.search(None, None, Some(Seq(UserId("AAA-BBB-CCC"))))

      whenReady(futureJohnDoeCorp, Timeout(2.seconds)) { organisationsList =>
        organisationsList.total shouldBe 1
        organisationsList.results.head.organisationId shouldBe UserId("AAA-BBB-CCC")
      }
    }
  }

  feature("get proposals voted") {
    scenario("successfully get proposals voted") {

      def indexedProposal(id: ProposalId): IndexedProposal = {
        IndexedProposal(
          id = id,
          userId = UserId(s"user-${id.value}"),
          content = s"proposal with id ${id.value}",
          slug = s"proposal-with-id-${id.value}",
          status = ProposalStatus.Pending,
          createdAt = DateHelper.now(),
          updatedAt = None,
          votes = Seq.empty,
          votesCount = 0,
          votesVerifiedCount = 0,
          toEnrich = false,
          scores = IndexedScores.empty,
          context = None,
          trending = None,
          labels = Seq.empty,
          author = Author(
            firstName = Some(id.value),
            organisationName = None,
            organisationSlug = None,
            postalCode = None,
            age = None,
            avatarUrl = None
          ),
          organisations = Seq.empty,
          country = Country("FR"),
          language = Language("fr"),
          themeId = None,
          tags = Seq.empty,
          ideaId = None,
          operationId = None,
          question = None,
          sequencePool = SequencePool.New,
          initialProposal = false,
          refusalReason = None
        )
      }

      Mockito
        .when(userHistoryCoordinatorService.retrieveVotedProposals(any[RequestUserVotedProposals]))
        .thenReturn(Future.successful(Seq(ProposalId("proposal1"), ProposalId("proposal2"))))
      Mockito
        .when(userHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestVoteValues]))
        .thenReturn(
          Future.successful(
            Map(
              ProposalId("proposal2") -> VoteAndQualifications(
                Agree,
                Map.empty,
                ZonedDateTime.parse("2018-03-01T16:09:30.441Z"),
                Trusted
              ),
              ProposalId("proposal1") -> VoteAndQualifications(
                Disagree,
                Map.empty,
                ZonedDateTime.parse("2018-03-02T16:09:30.441Z"),
                Trusted
              )
            )
          )
        )

      Mockito
        .when(
          proposalService.searchForUser(
            ArgumentMatchers.any[Option[UserId]],
            ArgumentMatchers.any[SearchQuery],
            ArgumentMatchers.any[RequestContext]
          )
        )
        .thenReturn(
          Future.successful(
            ProposalsResultSeededResponse(
              total = 2,
              results = Seq(
                ProposalResponse(
                  indexedProposal(ProposalId("proposal2")),
                  myProposal = false,
                  None,
                  proposalKey = "pr0p0541k3y"
                ),
                ProposalResponse(
                  indexedProposal(ProposalId("proposal1")),
                  myProposal = false,
                  None,
                  proposalKey = "pr0p0541k3y"
                )
              ),
              seed = None
            )
          )
        )

      val futureProposalsVoted =
        organisationService.getVotedProposals(UserId("AAA-BBB-CCC"), None, None, None, RequestContext.empty)

      whenReady(futureProposalsVoted, Timeout(2.seconds)) { proposalsList =>
        proposalsList.total shouldBe 2
        proposalsList.results.head.proposal.id shouldBe ProposalId("proposal1")
        proposalsList.results.last.proposal.id shouldBe ProposalId("proposal2")
      }
    }

    scenario("successful return when no proposal are voted") {
      Mockito
        .when(userHistoryCoordinatorService.retrieveVotedProposals(any[RequestUserVotedProposals]))
        .thenReturn(Future.successful(Seq.empty))
      Mockito
        .when(userHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestVoteValues]))
        .thenReturn(Future.successful(Map[ProposalId, VoteAndQualifications]()))

      val futureProposalsVoted =
        organisationService.getVotedProposals(UserId("AAA-BBB-CCC"), None, None, None, RequestContext.empty)

      whenReady(futureProposalsVoted, Timeout(2.seconds)) { proposalsList =>
        proposalsList.total shouldBe 0
      }
    }
  }

}
