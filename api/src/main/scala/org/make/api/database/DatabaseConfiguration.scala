package org.make.api.database

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import org.apache.commons.dbcp2.BasicDataSource
import org.make.api.ConfigurationSupport
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}


class DatabaseConfiguration(override protected val configuration: Config) extends Extension with ConfigurationSupport {

  private val user: String = configuration.getString("user")
  private val password: String = configuration.getString("password")

  private val jdbcUrl: String = configuration.getString("jdbc-url")

  val readDatasource = new BasicDataSource()
  readDatasource.setDriverClassName("org.postgresql.Driver")
  readDatasource.setUrl(jdbcUrl)
  readDatasource.setUsername(user)
  readDatasource.setPassword(password)
  readDatasource.setInitialSize(10)
  readDatasource.setMaxTotal(50)
  readDatasource.setMaxIdle(10)

  val writeDatasource = new BasicDataSource()
  writeDatasource.setDriverClassName("org.postgresql.Driver")
  writeDatasource.setUrl(jdbcUrl)
  writeDatasource.setUsername(user)
  writeDatasource.setPassword(password)
  writeDatasource.setInitialSize(10)
  writeDatasource.setMaxTotal(50)
  writeDatasource.setMaxIdle(10)



  ConnectionPool.add('READ,
    new DataSourceConnectionPool(
      dataSource = readDatasource
    )
  )

  ConnectionPool.add('WRITE,
    new DataSourceConnectionPool(
      dataSource = writeDatasource
    )
  )

}

object DatabaseConfiguration extends ExtensionId[DatabaseConfiguration] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): DatabaseConfiguration =
    new DatabaseConfiguration(system.settings.config.getConfig("make-api.database"))

  override def lookup(): ExtensionId[DatabaseConfiguration] = DatabaseConfiguration
  override def get(system: ActorSystem): DatabaseConfiguration = super.get(system)
}

trait DatabaseConfigurationExtension { this: Actor =>
  val databaseConfiguration: DatabaseConfiguration = DatabaseConfiguration(context.system)
}

