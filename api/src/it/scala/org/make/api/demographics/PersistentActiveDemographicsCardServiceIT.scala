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

package org.make.api.demographics

import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.api.{DatabaseTest, TestUtilsIT}
import org.make.core.demographics.{ActiveDemographicsCard, ActiveDemographicsCardId, DemographicsCardId}
import org.make.core.question.QuestionId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentActiveDemographicsCardServiceIT
    extends DatabaseTest
    with DefaultPersistentActiveDemographicsCardServiceComponent
    with DefaultPersistentDemographicsCardServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultIdGeneratorComponent {

  override def beforeAll(): Unit = {
    super.beforeAll()
    val futurePersistQuestions: Future[Unit] = for {
      _ <- persistentQuestionService.persist(question(QuestionId("question-1"), slug = "question-1"))
      _ <- persistentQuestionService.persist(question(QuestionId("question-2"), slug = "question-2"))
    } yield {}
    val futurePersistDemographicsCards = for {
      _ <- persistentDemographicsCardService.persist(TestUtilsIT.demographicsCard(id = DemographicsCardId("age")))
      _ <- persistentDemographicsCardService.persist(TestUtilsIT.demographicsCard(id = DemographicsCardId("location")))
      _ <- persistentDemographicsCardService.persist(TestUtilsIT.demographicsCard(id = DemographicsCardId("gender")))
      _ <- persistentDemographicsCardService.persist(TestUtilsIT.demographicsCard(id = DemographicsCardId("pet")))
    } yield {}

    whenReady(futurePersistQuestions, Timeout(10.seconds)) { _ =>
      ()
    }
    whenReady(futurePersistDemographicsCards, Timeout(10.seconds)) { _ =>
      ()
    }
  }

  def newActiveDemographicsCard(
    demographicsCardId: DemographicsCardId,
    questionId: QuestionId
  ): ActiveDemographicsCard =
    ActiveDemographicsCard(
      id = idGenerator.nextActiveDemographicsCardId(),
      demographicsCardId = demographicsCardId,
      questionId = questionId
    )

  val age: ActiveDemographicsCard = newActiveDemographicsCard(DemographicsCardId("age"), QuestionId("question-1"))
  val location: ActiveDemographicsCard =
    newActiveDemographicsCard(DemographicsCardId("location"), QuestionId("question-1"))
  val genderq1: ActiveDemographicsCard =
    newActiveDemographicsCard(DemographicsCardId("gender"), QuestionId("question-1"))
  val genderq2: ActiveDemographicsCard =
    newActiveDemographicsCard(DemographicsCardId("gender"), QuestionId("question-2"))
  val pet: ActiveDemographicsCard = newActiveDemographicsCard(DemographicsCardId("pet"), QuestionId("question-2"))
  val toDelete: ActiveDemographicsCard =
    newActiveDemographicsCard(DemographicsCardId("age"), QuestionId("question-2"))

  Feature("persist and get") {
    Scenario("persist and get") {
      val futureDemographicsCard: Future[Option[ActiveDemographicsCard]] = for {
        _             <- persistentActiveDemographicsCardService.persist(age)
        activeCardAge <- persistentActiveDemographicsCardService.get(age.id)
      } yield activeCardAge

      whenReady(futureDemographicsCard, Timeout(3.seconds)) { result =>
        result.map(_.id) shouldBe Some(age.id)
      }
    }

    Scenario("Get active demographicsCard by activeDemographicsCardId that does not exists") {
      val futureDemographicsCardId: Future[Option[ActiveDemographicsCard]] =
        persistentActiveDemographicsCardService.get(ActiveDemographicsCardId("fake"))

      whenReady(futureDemographicsCardId, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }

  Feature("list") {
    Scenario("list all and by questionId") {
      val futurePersistedDemographicsCardList: Future[Seq[ActiveDemographicsCard]] = for {
        l  <- persistentActiveDemographicsCardService.persist(location)
        g1 <- persistentActiveDemographicsCardService.persist(genderq1)
        g2 <- persistentActiveDemographicsCardService.persist(genderq2)
        p  <- persistentActiveDemographicsCardService.persist(pet)
      } yield Seq(l, g1, g2, p)

      val all: Future[(Seq[ActiveDemographicsCard], Seq[ActiveDemographicsCard])] = for {
        persisted <- futurePersistedDemographicsCardList
        found <- persistentActiveDemographicsCardService.list(
          start = None,
          end = None,
          sort = None,
          order = None,
          questionId = None
        )
      } yield persisted -> found

      whenReady(all, Timeout(3.seconds)) {
        case (persisted, found) =>
          persisted.forall(found.contains) should be(true)
      }

      val q2Only: Future[Seq[ActiveDemographicsCard]] = for {
        found <- persistentActiveDemographicsCardService.list(
          start = None,
          end = None,
          sort = None,
          order = None,
          questionId = Some(QuestionId("question-2"))
        )
      } yield found

      whenReady(q2Only, Timeout(3.seconds)) { found =>
        found.size shouldBe 2
        found.map(_.id) should contain theSameElementsAs Seq(genderq2.id, pet.id)
      }
    }
  }

  Feature("delete") {
    Scenario("Delete active demographicsCard") {
      val futureDemographicsCardPersisted: Future[Option[ActiveDemographicsCard]] = for {
        _       <- persistentActiveDemographicsCardService.persist(toDelete)
        deleted <- persistentActiveDemographicsCardService.get(toDelete.id)
      } yield deleted

      whenReady(futureDemographicsCardPersisted, Timeout(3.seconds)) { result =>
        result.map(_.id) shouldBe Some(toDelete.id)
      }

      val futureDemographicsCard: Future[Option[ActiveDemographicsCard]] = for {
        _                       <- persistentActiveDemographicsCardService.delete(toDelete.id)
        demographicsCardDeleted <- persistentActiveDemographicsCardService.get(toDelete.id)
      } yield demographicsCardDeleted

      whenReady(futureDemographicsCard, Timeout(3.seconds)) { result =>
        result shouldBe None
      }
    }
  }
}
