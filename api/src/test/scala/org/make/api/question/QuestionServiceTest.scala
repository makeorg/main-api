package org.make.api.question

import akka.actor.ActorSystem
import org.make.api.personality.{QuestionPersonalityService, QuestionPersonalityServiceComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.user.{UserService, UserServiceComponent}
import org.make.api.{ActorSystemComponent, MakeUnitTest, TestUtils}
import org.make.core.personality.{Candidate, Personality, PersonalityId}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.mockito.Mockito.when
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class QuestionServiceTest
    extends MakeUnitTest
    with DefaultQuestionService
    with PersistentQuestionServiceComponent
    with ActorSystemComponent
    with IdGeneratorComponent
    with QuestionPersonalityServiceComponent
    with UserServiceComponent {

  override val actorSystem: ActorSystem = ActorSystem()
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val questionPersonalityService: QuestionPersonalityService = mock[QuestionPersonalityService]
  override val userService: UserService = mock[UserService]
  override val persistentQuestionService: PersistentQuestionService = mock[PersistentQuestionService]

  val personalities: Seq[Personality] = Seq(
    Personality(
      personalityId = PersonalityId("personality-1"),
      userId = UserId("user-1"),
      questionId = QuestionId("question-id"),
      personalityRole = Candidate
    ),
    Personality(
      personalityId = PersonalityId("personality-2"),
      userId = UserId("user-2"),
      questionId = QuestionId("question-id"),
      personalityRole = Candidate
    )
  )

  val user1 = TestUtils.user(id = UserId("user-1"))
  val user2 = TestUtils.user(id = UserId("user-2"))

  feature("Get question personalities") {
    scenario("Get question personalities") {
      when(
        questionPersonalityService.find(
          start = 0,
          end = None,
          sort = None,
          order = None,
          userId = None,
          questionId = Some(QuestionId("question-id")),
          personalityRole = None
        )
      ).thenReturn(Future.successful(personalities))
      when(userService.getPersonality(UserId("user-1"))).thenReturn(Future.successful(Some(user1)))
      when(userService.getPersonality(UserId("user-2"))).thenReturn(Future.successful(Some(user2)))

      whenReady(
        questionService.getQuestionPersonalities(
          start = 0,
          end = None,
          questionId = QuestionId("question-id"),
          personalityRole = None
        ),
        Timeout(3.seconds)
      ) { questionPersonalities =>
        questionPersonalities.map(_.userId) should contain(UserId("user-1"))
        questionPersonalities.map(_.userId) should contain(UserId("user-2"))
      }
    }
  }

}
