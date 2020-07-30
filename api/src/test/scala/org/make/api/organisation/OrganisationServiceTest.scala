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

import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.{MakeUnitTest, TestUtils}
import org.make.api.proposal.{
  ProposalResponse,
  ProposalSearchEngine,
  ProposalSearchEngineComponent,
  ProposalService,
  ProposalServiceComponent,
  ProposalsResultSeededResponse
}
import org.make.api.technical.auth.{UserTokenGenerator, UserTokenGeneratorComponent}
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGeneratorComponent}
import org.make.api.user.DefaultPersistentUserServiceComponent.UpdateFailed
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user._
import org.make.api.userhistory.{
  OrganisationEmailChangedEvent,
  OrganisationRegisteredEvent,
  OrganisationUpdatedEvent,
  UserHistoryCoordinatorService,
  UserHistoryCoordinatorServiceComponent
}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.profile.Profile
import org.make.core.proposal.VoteKey.{Agree, Disagree}
import org.make.core.proposal.indexed.{
  IndexedAuthor,
  IndexedProposal,
  IndexedScores,
  ProposalsSearchResult,
  SequencePool
}
import org.make.core.proposal.{ProposalId, ProposalStatus, SearchQuery}
import org.make.core.reference.{Country, Language}
import org.make.core.technical.IdGenerator
import org.make.core.user.Role.RoleActor
import org.make.core.user._
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult}
import org.make.core.{DateHelper, RequestContext}
import org.scalatest.RecoverMethods
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class OrganisationServiceTest
    extends MakeUnitTest
    with DefaultOrganisationServiceComponent
    with UserServiceComponent
    with IdGeneratorComponent
    with PersistentUserServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ProposalServiceComponent
    with EventBusServiceComponent
    with ProposalSearchEngineComponent
    with OrganisationSearchEngineComponent
    with PersistentUserToAnonymizeServiceComponent
    with UserTokenGeneratorComponent
    with MakeSettingsComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val userService: UserService = mock[UserService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val elasticsearchOrganisationAPI: OrganisationSearchEngine = mock[OrganisationSearchEngine]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val persistentUserToAnonymizeService: PersistentUserToAnonymizeService =
    mock[PersistentUserToAnonymizeService]
  override val userTokenGenerator: UserTokenGenerator = mock[UserTokenGenerator]
  override val makeSettings: MakeSettings = mock[MakeSettings]

  when(makeSettings.resetTokenB2BExpiresIn).thenReturn(Duration("3 days"))

  val returnedOrganisation = TestUtils.user(
    id = UserId("AAA-BBB-CCC"),
    email = "any@mail.com",
    firstName = None,
    lastName = None,
    organisationName = Some("John Doe Corp."),
    lastIp = None,
    hashedPassword = Some("passpass"),
    roles = Seq(RoleActor),
    profile = Profile.parseProfile(avatarUrl = Some("avatarUrl")),
    userType = UserType.UserTypeOrganisation
  )

  val returnedOrganisation2 = TestUtils.user(
    id = UserId("AAA-BBB-CCC-DDD"),
    email = "some@mail.com",
    firstName = None,
    lastName = None,
    organisationName = Some("Jeanne Done Corp."),
    lastIp = None,
    hashedPassword = Some("passpass"),
    roles = Seq(RoleActor),
    userType = UserType.UserTypeOrganisation
  )

  Feature("Get organisation") {
    Scenario("get organisation") {
      when(persistentUserService.findByUserIdAndUserType(any[UserId], any[UserType]))
        .thenReturn(Future.successful(Some(returnedOrganisation)))

      whenReady(organisationService.getOrganisation(UserId("AAA-BBB-CCC")), Timeout(2.seconds)) { user =>
        user shouldBe a[Option[_]]
        user.isDefined shouldBe true
        user.get.email shouldBe "any@mail.com"
      }
    }
  }

  Feature("register organisation") {
    Scenario("successfully register an organisation with password") {
      reset(eventBusService)
      when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))

      when(
        persistentUserService
          .persist(any[User])
      ).thenReturn(Future.successful(returnedOrganisation))

      when(userTokenGenerator.generateResetToken()).thenReturn(Future.successful(("TOKEN", "HASHED_TOKEN")))

      val futureOrganisation = organisationService.register(
        OrganisationRegisterData(
          name = "John Doe Corp.",
          email = "any@mail.com",
          password = Some("passopasso"),
          avatar = None,
          description = None,
          country = Country("FR"),
          language = Language("fr"),
          website = Some("http://example.com")
        ),
        RequestContext.empty
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { user =>
        user shouldBe a[User]
        user.email should be("any@mail.com")
        user.organisationName should be(Some("John Doe Corp."))
      }

      verify(eventBusService, times(1)).publish(argMatching[AnyRef]({
        case OrganisationRegisteredEvent(_, _, returnedOrganisation.userId, _, _, _, _) =>
      }))
    }

    Scenario("email already exists") {
      reset(eventBusService)
      when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))

      val futureOrganisation = organisationService.register(
        OrganisationRegisterData(
          name = "John Doe Corp.",
          email = "any@mail.com",
          password = Some("passopasso"),
          avatar = None,
          description = None,
          country = Country("FR"),
          language = Language("fr"),
          website = None
        ),
        RequestContext.empty
      )

      RecoverMethods.recoverToSucceededIf[EmailAlreadyRegisteredException](futureOrganisation)

      verify(eventBusService, times(0)).publish(argMatching[AnyRef]({
        case OrganisationRegisteredEvent(_, _, returnedOrganisation.userId, _, _, _, _) =>
      }))
    }
  }

  Feature("update organisation") {
    Scenario("successfully update an organisation with an email update") {

      val oldEmail = returnedOrganisation.email
      val updatedOrganisation = returnedOrganisation.copy(email = "new-email@example.com")

      reset(eventBusService)
      when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))
      when(persistentUserService.modifyOrganisation(any[User]))
        .thenReturn(Future.successful(Right(updatedOrganisation)))
      when(proposalService.searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext]))
        .thenReturn(Future.successful(ProposalsResultSeededResponse(0, Seq.empty, None)))
      when(
        elasticsearchProposalAPI
          .searchProposals(any[SearchQuery])
      ).thenReturn(Future.successful(ProposalsSearchResult(0, Seq.empty)))
      when(userHistoryCoordinatorService.retrieveVotedProposals(any[RequestUserVotedProposals]))
        .thenReturn(Future.successful(Seq.empty))
      when(userHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestVoteValues]))
        .thenReturn(Future.successful(Map[ProposalId, VoteAndQualifications]()))
      when(persistentUserToAnonymizeService.create(oldEmail))
        .thenReturn(Future.successful({}))

      val futureOrganisation =
        organisationService.update(updatedOrganisation, None, oldEmail, RequestContext.empty)

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisation =>
        organisation shouldBe a[UserId]
      }
      verify(eventBusService, times(1))
        .publish(argMatching[AnyRef]({ case _: OrganisationEmailChangedEvent => }))
      verify(eventBusService, times(1))
        .publish(argMatching[AnyRef]({ case _: OrganisationUpdatedEvent => }))
    }

    Scenario("successfully update an organisation without changing anything") {
      when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(false))
      when(persistentUserService.modifyOrganisation(any[User]))
        .thenReturn(Future.successful(Right(returnedOrganisation)))
      when(
        elasticsearchProposalAPI
          .searchProposals(any[SearchQuery])
      ).thenReturn(Future.successful(ProposalsSearchResult(0, Seq.empty)))

      val futureOrganisation =
        organisationService.update(returnedOrganisation, None, returnedOrganisation.email, RequestContext.empty)

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisation =>
        organisation shouldBe a[UserId]
      }
    }

    Scenario("try to update with mail already exists") {
      when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))

      val futureOrganisation =
        organisationService.update(returnedOrganisation, None, returnedOrganisation.email, RequestContext.empty)

      RecoverMethods.recoverToSucceededIf[EmailAlreadyRegisteredException](futureOrganisation)
    }

    Scenario("Fail update") {
      when(persistentUserService.modifyOrganisation(any[User]))
        .thenReturn(Future.successful(Left(UpdateFailed())))

      val futureOrganisation =
        organisationService.update(returnedOrganisation, None, returnedOrganisation.email, RequestContext.empty)

      RecoverMethods.recoverToSucceededIf[UpdateFailed](futureOrganisation)
    }
  }

  Feature("Get organisations") {
    Scenario("get organisations") {
      when(persistentUserService.findAllOrganisations())
        .thenReturn(Future.successful(Seq(returnedOrganisation, returnedOrganisation2)))

      whenReady(organisationService.getOrganisations, Timeout(2.seconds)) { organisationList =>
        organisationList shouldBe a[Seq[_]]
        organisationList.size shouldBe 2
        organisationList.head.email shouldBe "any@mail.com"
      }
    }
  }

  Feature("search organisations") {
    when(elasticsearchOrganisationAPI.searchOrganisations(eqTo(OrganisationSearchQuery())))
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
    when(
      elasticsearchOrganisationAPI.searchOrganisations(
        eqTo(
          OrganisationSearchQuery(filters =
            Some(OrganisationSearchFilters(organisationName = Some(OrganisationNameSearchFilter("John Doe Corp."))))
          )
        )
      )
    ).thenReturn(
      Future.successful(
        OrganisationSearchResult(
          total = 1L,
          results = Seq(IndexedOrganisation.createFromOrganisation(returnedOrganisation))
        )
      )
    )
    when(
      elasticsearchOrganisationAPI.searchOrganisations(
        eqTo(
          OrganisationSearchQuery(filters = Some(
            OrganisationSearchFilters(organisationIds = Some(OrganisationIdsSearchFilter(Seq(UserId("AAA-BBB-CCC")))))
          )
          )
        )
      )
    ).thenReturn(
      Future.successful(
        OrganisationSearchResult(
          total = 1L,
          results = Seq(IndexedOrganisation.createFromOrganisation(returnedOrganisation))
        )
      )
    )
    when(
      elasticsearchOrganisationAPI.searchOrganisations(
        eqTo(
          OrganisationSearchQuery(filters = Some(
            OrganisationSearchFilters(
              country = Some(CountrySearchFilter(Country("FR"))),
              language = Some(LanguageSearchFilter(Language("fr")))
            )
          )
          )
        )
      )
    ).thenReturn(
      Future.successful(
        OrganisationSearchResult(
          total = 1L,
          results = Seq(IndexedOrganisation.createFromOrganisation(returnedOrganisation))
        )
      )
    )

    Scenario("search all") {
      val futureAllOrganisation = organisationService.search(None, None, None, None, None)

      whenReady(futureAllOrganisation, Timeout(2.seconds)) { organisationsList =>
        organisationsList.total shouldBe 2
      }
    }

    Scenario("search by organisationName") {
      val futureJohnDoeCorp = organisationService.search(Some("John Doe Corp."), None, None, None, None)

      whenReady(futureJohnDoeCorp, Timeout(2.seconds)) { organisationsList =>
        organisationsList.total shouldBe 1
        organisationsList.results.head.organisationName shouldBe Some("John Doe Corp.")
      }
    }

    Scenario("search by organisationIds") {
      val futureJohnDoeCorp = organisationService.search(None, None, Some(Seq(UserId("AAA-BBB-CCC"))), None, None)

      whenReady(futureJohnDoeCorp, Timeout(2.seconds)) { organisationsList =>
        organisationsList.total shouldBe 1
        organisationsList.results.head.organisationId shouldBe UserId("AAA-BBB-CCC")
      }
    }

    Scenario("search by country-language") {
      val futureJohnDoeCorp = organisationService.search(None, None, None, Some(Country("FR")), Some(Language("fr")))

      whenReady(futureJohnDoeCorp, Timeout(2.seconds)) { organisationsList =>
        organisationsList.total shouldBe 1
        organisationsList.results.head.organisationId shouldBe UserId("AAA-BBB-CCC")
      }
    }
  }

  Feature("get proposals voted") {
    Scenario("successfully get proposals voted") {

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
          votesSequenceCount = 0,
          votesSegmentCount = 0,
          toEnrich = false,
          scores = IndexedScores.empty,
          segmentScores = IndexedScores.empty,
          context = None,
          trending = None,
          labels = Seq.empty,
          author = IndexedAuthor(
            firstName = Some(id.value),
            displayName = Some(id.value),
            organisationName = None,
            organisationSlug = None,
            postalCode = None,
            age = None,
            avatarUrl = None,
            anonymousParticipation = false,
            userType = UserType.UserTypeUser
          ),
          organisations = Seq.empty,
          tags = Seq.empty,
          selectedStakeTag = None,
          ideaId = None,
          operationId = None,
          question = None,
          sequencePool = SequencePool.New,
          sequenceSegmentPool = SequencePool.New,
          initialProposal = false,
          refusalReason = None,
          operationKind = None,
          segment = None
        )
      }

      when(userHistoryCoordinatorService.retrieveVotedProposals(any[RequestUserVotedProposals]))
        .thenReturn(Future.successful(Seq(ProposalId("proposal1"), ProposalId("proposal2"))))
      when(userHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestVoteValues]))
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

      when(proposalService.searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext]))
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
        organisationService.getVotedProposals(
          organisationId = UserId("AAA-BBB-CCC"),
          maybeUserId = None,
          filterVotes = None,
          filterQualifications = None,
          sort = None,
          limit = None,
          skip = None,
          RequestContext.empty
        )

      whenReady(futureProposalsVoted, Timeout(2.seconds)) { proposalsList =>
        proposalsList.total shouldBe 2
        proposalsList.results.head.proposal.id shouldBe ProposalId("proposal1")
        proposalsList.results.last.proposal.id shouldBe ProposalId("proposal2")
      }
    }

    Scenario("successful return when no proposal are voted") {
      when(userHistoryCoordinatorService.retrieveVotedProposals(any[RequestUserVotedProposals]))
        .thenReturn(Future.successful(Seq.empty))
      when(userHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestVoteValues]))
        .thenReturn(Future.successful(Map[ProposalId, VoteAndQualifications]()))

      val futureProposalsVoted =
        organisationService.getVotedProposals(
          organisationId = UserId("AAA-BBB-CCC"),
          maybeUserId = None,
          filterVotes = None,
          filterQualifications = None,
          sort = None,
          limit = None,
          skip = None,
          RequestContext.empty
        )

      whenReady(futureProposalsVoted, Timeout(2.seconds)) { proposalsList =>
        proposalsList.total shouldBe 0
      }
    }
  }

}
