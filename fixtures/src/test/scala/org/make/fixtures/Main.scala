package org.make.fixtures

import io.gatling.core.Predef.{atOnceUsers, nothingFor, Simulation}

import scala.concurrent.duration._

class Main extends Simulation {
  setUp(
    User.scnRegister.inject(atOnceUsers(User.maxClients)).protocols(User.httpConf),
    Proposal.scnRegister
      .inject(nothingFor(6.seconds), atOnceUsers(Proposal.maxClients))
      .protocols(Proposal.httpConf)
  )
}
