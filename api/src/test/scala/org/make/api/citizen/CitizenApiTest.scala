package org.make.api.citizen

import java.time.LocalDate

import io.finch.Input
import org.make.api.IdGeneratorComponent
import org.make.core.citizen.{Citizen, CitizenId}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future

class CitizenApiTest extends FlatSpec with Matchers with MockitoSugar
  with CitizenServiceComponent
  with IdGeneratorComponent
  with CitizenApi {

  override val citizenService: CitizenService = mock[CitizenService]
  override val idGenerator: IdGenerator = mock[IdGenerator]



  "get citizen" should "return a json citizen if citizen exists" in {
    when(citizenService.getCitizen(ArgumentMatchers.eq(CitizenId("1234"))))
      .thenReturn(
        Future.successful(Option(
          Citizen(
            citizenId = CitizenId("1234"),
            email = "test@test.com",
            dateOfBirth = LocalDate.parse("1970-01-01"),
            firstName = "testFirstName",
            lastName = "testLastName"
          )
        ))
      )

    val output = getCitizen(Input.get("/citizen/1234")).awaitOutputUnsafe().get
    output.status.code should be(200)
  }


  it should "return a 404 if citizen doesn't exist" in {
    when(citizenService.getCitizen(ArgumentMatchers.any(classOf[CitizenId])))
      .thenReturn(Future.successful(None))
    val output = getCitizen(Input.get("/citizen/1234")).awaitOutputUnsafe().get
    output.status.code should be(404)
  }


}
