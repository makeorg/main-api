package org.make.api.database

import akka.actor.{Actor, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config
import org.make.api.ConfigurationSupport
import scalikejdbc.async._


class DatabaseConfiguration(override protected val configuration: Config) extends Extension with ConfigurationSupport {

  private val host: String = configuration.getString("host")
  private val port: Int = configuration.getInt("port")
  private val user: String = configuration.getString("user")
  private val password: String = configuration.getString("password")
  private val database: String = configuration.getString("database")

  AsyncConnectionPool.add('READ,
    s"jdbc:postgresql://$host:$port/$database", user, password,
    AsyncConnectionPoolSettings(
      maxPoolSize = 50,
      maxQueueSize = 10,
      maxIdleMillis = 20000
    )
  )
  AsyncConnectionPool.add('WRITE,
    s"jdbc:postgresql://$host:$port/$database", user, password,
    AsyncConnectionPoolSettings(
      maxPoolSize = 10,
      maxQueueSize = 5,
      maxIdleMillis = 20000
    )
  )

  val readDatasource: AsyncConnectionPool = AsyncConnectionPool('READ)
  val writeDatasource: AsyncConnectionPool = AsyncConnectionPool('WRITE)

  NamedAsyncDB('READ).localTx { session => ???

  }

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

