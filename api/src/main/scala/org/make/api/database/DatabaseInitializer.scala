package org.make.api.database

import scalikejdbc.NamedDB

/**
  * Created by francois on 5/12/17.
  */
object DatabaseInitializer {

  NamedDB('WRITE).toDB().connectionAttributes

}
