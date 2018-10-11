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

import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import org.make.api.MakeUnitTest
import org.make.api.proposal.{ProposalService, ProposalServiceComponent, ProposalsResultSeededResponse}
import org.make.api.technical.{EventBusService, EventBusServiceComponent, IdGenerator, IdGeneratorComponent}
import org.make.api.user.DefaultPersistentUserServiceComponent.UpdateFailed
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user._
import org.make.api.userhistory.UserEvent.{OrganisationRegisteredEvent, OrganisationUpdatedEvent}
import org.make.api.userhistory.UserHistoryActor.{RequestUserVotedProposals, RequestVoteValues}
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.profile.Profile
import org.make.core.proposal.VoteKey.{Agree, Disagree}
import org.make.core.proposal.{ProposalId, SearchQuery}
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
    with IdGeneratorComponent
    with PersistentUserServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ProposalServiceComponent
    with EventBusServiceComponent
    with OrganisationSearchEngineComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
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
    profile = Some(Profile(None, Some("avatarUrl"), None, None, None, None, None, None, None, None, None, None, None))
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
    profile = None
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
    scenario("successfully register an organisation") {
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
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))
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

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(
          name = Some("Jeanne Done Corp."),
          email = None,
          avatar = Some("anotherAvatarUrl"),
          description = None
        ),
        RequestContext.empty
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisationId =>
        organisationId.isDefined shouldBe true
      }
      verify(eventBusService, times(1))
        .publish(ArgumentMatchers.any[OrganisationUpdatedEvent])
    }

    scenario("successfully update an organisation without changing anything") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))
      Mockito.when(persistentUserService.modify(any[User])).thenReturn(Future.successful(Right(returnedOrganisation)))

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(name = None, email = None, avatar = None, description = None),
        RequestContext.empty
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisationId =>
        organisationId.isDefined shouldBe true
      }
    }

    scenario("try to update with mail already exists") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))
      Mockito.when(persistentUserService.emailExists(any[String])).thenReturn(Future.successful(true))

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(name = None, email = Some("any@mail.com"), avatar = None, description = None),
        RequestContext.empty
      )

      RecoverMethods.recoverToSucceededIf[EmailAlreadyRegisteredException](futureOrganisation)
    }

    scenario("Fail update") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(Some(returnedOrganisation)))
      Mockito.when(persistentUserService.modify(any[User])).thenReturn(Future.successful(Left(UpdateFailed())))

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(name = None, email = None, avatar = None, description = None),
        RequestContext.empty
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisationId =>
        organisationId.isDefined shouldBe false
      }
    }

    scenario("Organisation not found") {
      Mockito.when(persistentUserService.get(any[UserId])).thenReturn(Future.successful(None))

      val futureOrganisation = organisationService.update(
        UserId("AAA-BBB-CCC"),
        OrganisationUpdateData(name = None, email = None, avatar = None, description = None),
        RequestContext.empty
      )

      whenReady(futureOrganisation, Timeout(2.seconds)) { organisationId =>
        organisationId.isDefined shouldBe false
      }
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

    scenario("search all") {
      val futureAllOrganisation = organisationService.search(None, None)

      whenReady(futureAllOrganisation, Timeout(2.seconds)) { organisationsList =>
        organisationsList.total shouldBe 2
      }
    }

    scenario("search by organisationName") {
      val futureJohnDoeCorp = organisationService.search(Some("John Doe Corp."), None)

      whenReady(futureJohnDoeCorp, Timeout(2.seconds)) { organisationsList =>
        organisationsList.total shouldBe 1
        organisationsList.results.head.organisationName shouldBe Some("John Doe Corp.")
      }
    }
  }

  feature("get proposals voted") {
    scenario("successfully get proposals voted") {
      Mockito
        .when(userHistoryCoordinatorService.retrieveVotedProposals(any[RequestUserVotedProposals]))
        .thenReturn(Future.successful(Seq(ProposalId("proposal1"), ProposalId("proposal2"))))
      Mockito
        .when(userHistoryCoordinatorService.retrieveVoteAndQualifications(any[RequestVoteValues]))
        .thenReturn(
          Future.successful(
            Map(
              ProposalId("proposal1") -> VoteAndQualifications(Agree, Seq.empty, DateHelper.now()),
              ProposalId("proposal2") -> VoteAndQualifications(Disagree, Seq.empty, DateHelper.now())
            )
          )
        )

      Mockito
        .when(proposalService.searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext]))
        .thenReturn(Future.successful(ProposalsResultSeededResponse(total = 2, Seq.empty, None)))

      val futureProposalsVoted =
        organisationService.getVotedProposals(UserId("AAA-BBB-CCC"), None, None, None, RequestContext.empty)

      whenReady(futureProposalsVoted, Timeout(2.seconds)) { proposalsList =>
        proposalsList.total shouldBe 2
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
