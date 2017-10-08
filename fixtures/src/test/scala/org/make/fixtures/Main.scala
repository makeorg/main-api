package org.make.fixtures

import io.gatling.core.Predef.{heavisideUsers, nothingFor, Simulation}

import scala.concurrent.duration._
class Main extends Simulation {
  setUp(
    User.scnRegister
      .inject(heavisideUsers(User.maxClients).over(30.minutes))
      .protocols(User.httpConf),
    Proposal.scnRegister
      .inject(nothingFor(30.seconds), heavisideUsers(Proposal.maxClients).over(30.minutes))
      .protocols(Proposal.httpConf)
  )
}
