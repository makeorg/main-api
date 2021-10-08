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

package org.make.api.technical.crm

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpRequest, HttpResponse, MediaTypes, StatusCodes}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.make.api.extensions.{MailJetConfiguration, MailJetConfigurationComponent}
import org.make.api.technical.ShortenedNames
import org.make.api.technical.crm.CrmClientException.RequestException.ManageContactListException
import org.make.api.{ActorSystemComponent, MakeUnitTest}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.util.{Success, Try}

class CrmClientComponentTest
    extends MakeUnitTest
    with DefaultCrmClientComponent
    with ActorSystemComponent
    with MailJetConfigurationComponent
    with ShortenedNames {

  override val actorSystem: ActorSystem = CrmClientComponentTest.actorSystem
  implicit val ec: EC = ECGlobal
  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]

  Feature("retry on 500 errors") {

    def client(maxFailures: Int): CrmClient = new DefaultCrmClient {
      var failures = 0
      override lazy val httpFlow: Flow[(HttpRequest, Ctx), (Try[HttpResponse], Ctx), Http.HostConnectionPool] =
        Flow
          .fromFunction[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse])] {
            case (_, promise) =>
              val response = {
                if (failures >= maxFailures) {
                  HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity.Strict(
                      ContentType(MediaTypes.`application/json`),
                      ByteString("""{"Count": 0, "Total": 0, "Data": []}""")
                    )
                  )
                } else {
                  failures += 1
                  HttpResponse(StatusCodes.InternalServerError)
                }
              }
              (Success(response), promise)
          }
          .asInstanceOf[Flow[(HttpRequest, Ctx), (Try[HttpResponse], Ctx), Http.HostConnectionPool]]
    }

    Scenario("it works with few failures") {
      whenReady(client(2).manageContactList(ManageManyContacts(Nil, Nil)), Timeout(10.seconds)) { result =>
        result.total should be(0)
      }
    }
    Scenario("it gives up on too many failures") {
      whenReady(client(10).manageContactList(ManageManyContacts(Nil, Nil)).failed, Timeout(10.seconds)) { exception =>
        exception shouldBe a[ManageContactListException]
      }
    }
  }

}

object CrmClientComponentTest {

  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api {
      |  mail-jet {
      |    dispatcher {
      |      type = Dispatcher
      |      executor = "thread-pool-executor"
      |      thread-pool-executor {
      |        fixed-pool-size = 32
      |      }
      |      throughput = 1
      |    }
      |  }
      |}
    """.stripMargin

  val actorSystem: ActorSystem = ActorSystem("CrmClientComponentTest", ConfigFactory.parseString(configuration))

}
