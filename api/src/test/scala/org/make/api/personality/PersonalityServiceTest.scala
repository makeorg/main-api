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

package org.make.api.personality

import org.make.api.MakeUnitTest
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.personality.{Candidate, Personality, PersonalityId}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersonalityServiceTest
    extends MakeUnitTest
    with DefaultPersonalityServiceComponent
    with PersistentPersonalityServiceComponent
    with IdGeneratorComponent {

  override val persistentPersonalityService: PersistentPersonalityService = mock[PersistentPersonalityService]
  override val idGenerator: IdGenerator = mock[IdGenerator]

  val personality: Personality = Personality(
    personalityId = PersonalityId("personality"),
    userId = UserId("user-id"),
    questionId = QuestionId("question"),
    personalityRole = Candidate
  )

  feature("create personality") {
    scenario("creation") {
      Mockito.when(idGenerator.nextPersonalityId()).thenReturn(PersonalityId("personality"))
      Mockito.when(persistentPersonalityService.persist(personality)).thenReturn(Future.successful(personality))

      whenReady(
        personalityService.createPersonality(
          request = CreatePersonalityRequest(
            userId = UserId("user-id"),
            questionId = QuestionId("question"),
            personalityRole = Candidate
          )
        ),
        Timeout(2.seconds)
      ) { personality =>
        personality.personalityId should be(PersonalityId("personality"))
      }
    }
  }

  feature("update personality") {
    scenario("update when no personality is found") {
      Mockito.when(persistentPersonalityService.getById(PersonalityId("not-found"))).thenReturn(Future.successful(None))

      whenReady(
        personalityService.updatePersonality(
          personalityId = PersonalityId("not-found"),
          UpdatePersonalityRequest(userId = UserId("user-id"), personalityRole = Candidate)
        ),
        Timeout(2.seconds)
      ) { personality =>
        personality should be(None)
      }
    }

    scenario("update when personality is found") {
      val updatedPersonality: Personality = personality.copy(userId = UserId("update-user"))

      Mockito
        .when(persistentPersonalityService.getById(PersonalityId("personality")))
        .thenReturn(Future.successful(Some(personality)))
      Mockito
        .when(persistentPersonalityService.modify(updatedPersonality))
        .thenReturn(Future.successful(updatedPersonality))

      whenReady(
        personalityService.updatePersonality(
          personalityId = PersonalityId("personality"),
          UpdatePersonalityRequest(userId = UserId("update-user"), personalityRole = Candidate)
        ),
        Timeout(2.seconds)
      ) { personality =>
        personality.map(_.userId.value) should be(Some("update-user"))
      }
    }
  }

}
