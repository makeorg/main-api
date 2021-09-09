/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.demographics

import org.make.api.MakeUnitTest
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.security.{
  DefaultAESEncryptionComponent,
  SecurityConfiguration,
  SecurityConfigurationComponent
}
import org.make.core.{DateHelper, DefaultDateHelperComponent}
import org.make.core.demographics.{ActiveDemographicsCard, ActiveDemographicsCardId, DemographicsCardId}
import org.make.core.question.QuestionId
import org.make.core.technical.IdGenerator
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.time.ZonedDateTime
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DemographicsCardServiceTest
    extends MakeUnitTest
    with DefaultAESEncryptionComponent
    with DefaultDemographicsCardServiceComponent
    with DefaultDateHelperComponent
    with PersistentDemographicsCardServiceComponent
    with ActiveDemographicsCardServiceComponent
    with IdGeneratorComponent
    with SecurityConfigurationComponent {
  override val persistentDemographicsCardService: PersistentDemographicsCardService =
    mock[PersistentDemographicsCardService]
  override val activeDemographicsCardService: ActiveDemographicsCardService = mock[ActiveDemographicsCardService]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val securityConfiguration: SecurityConfiguration = mock[SecurityConfiguration]

  when(securityConfiguration.aesSecretKey).thenReturn("G9pPOCayHYlBnNAq1mCVqA==")

  when(idGenerator.nextDemographicsCardId()).thenReturn(DemographicsCardId("card-id"))

  Feature("get a random card") {
    val questionId: QuestionId = QuestionId("question-id")
    val fakeQuestionId: QuestionId = QuestionId("fake")

    when(
      activeDemographicsCardService
        .list(eqTo(None), eqTo(None), eqTo(None), eqTo(None), eqTo(Some(questionId)), eqTo(None))
    ).thenReturn(
      Future.successful(
        Seq(
          ActiveDemographicsCard(ActiveDemographicsCardId("active-1"), DemographicsCardId("age"), questionId),
          ActiveDemographicsCard(ActiveDemographicsCardId("active-2"), DemographicsCardId("gender"), questionId),
          ActiveDemographicsCard(ActiveDemographicsCardId("active-3"), DemographicsCardId("location"), questionId),
          ActiveDemographicsCard(ActiveDemographicsCardId("active-4"), DemographicsCardId("pet"), questionId)
        )
      )
    )
    when(
      activeDemographicsCardService
        .list(eqTo(None), eqTo(None), eqTo(None), eqTo(None), eqTo(Some(fakeQuestionId)), eqTo(None))
    ).thenReturn(Future.successful(Seq.empty))
    when(persistentDemographicsCardService.get(any)).thenAnswer { id: DemographicsCardId =>
      Future.successful(Some(demographicsCard(id)))
    }

    Scenario("valid question") {
      whenReady(demographicsCardService.getOneRandomCardByQuestion(questionId), Timeout(10.seconds)) { card =>
        card shouldBe defined
        card.map(_.id) should contain oneElementOf Seq(
          DemographicsCardId("age"),
          DemographicsCardId("gender"),
          DemographicsCardId("location"),
          DemographicsCardId("pet")
        )
      }
    }

    Scenario("fake question") {
      whenReady(demographicsCardService.getOneRandomCardByQuestion(fakeQuestionId), Timeout(10.seconds)) { card =>
        card should not be defined
      }
    }
  }

  Feature("validate token") {
    Scenario("valid token") {
      val questionId = QuestionId("question-id")
      val id: DemographicsCardId = DemographicsCardId("dc-id")
      val token = demographicsCardService.generateToken(id, questionId)
      Thread.sleep(1000)
      demographicsCardService.isTokenValid(token, id, questionId) shouldBe true
    }

    Scenario("invalid tokens") {
      val nowDate: ZonedDateTime = DateHelper.now()
      val questionId = QuestionId("question-id")
      val id: DemographicsCardId = DemographicsCardId("dc-id")

      val invalidToken = "invalid-token"
      aesEncryption.decodeAndDecrypt(invalidToken).isSuccess shouldBe false
      demographicsCardService.isTokenValid(invalidToken, id, questionId) shouldBe false

      val invalidDate: ZonedDateTime = nowDate.minusDays(1)
      val invalidDateDemoToken = DemographicToken(invalidDate, id, questionId)
      val invalidDateToken = aesEncryption.encryptAndEncode(invalidDateDemoToken.toTokenizedString)
      aesEncryption.decodeAndDecrypt(invalidDateToken).isSuccess shouldBe true
      demographicsCardService.isTokenValid(invalidDateToken, id, questionId) shouldBe false

      val invalidQuestionIdDemoToken = DemographicToken(nowDate, id, QuestionId("other-id"))
      val invalidQuestionIdToken = aesEncryption.encryptAndEncode(invalidQuestionIdDemoToken.toTokenizedString)
      aesEncryption.decodeAndDecrypt(invalidQuestionIdToken).isSuccess shouldBe true
      demographicsCardService.isTokenValid(invalidQuestionIdToken, id, questionId) shouldBe false

      val invalidDemoCardIdDemoToken = DemographicToken(nowDate, DemographicsCardId("other-id"), questionId)
      val invalidDemoCardIdToken = aesEncryption.encryptAndEncode(invalidDemoCardIdDemoToken.toTokenizedString)
      aesEncryption.decodeAndDecrypt(invalidDemoCardIdToken).isSuccess shouldBe true
      demographicsCardService.isTokenValid(invalidDemoCardIdToken, id, questionId) shouldBe false
    }
  }
}
