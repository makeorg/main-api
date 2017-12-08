package org.make.api.idea

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.idea.DefaultPersistentIdeaServiceComponent.PersistentIdea
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.DateHelper
import org.make.core.reference.{Idea, IdeaId}
import scalikejdbc._

import scala.concurrent.Future

trait PersistentIdeaServiceComponent {
  def persistentIdeaService: PersistentIdeaService
}

trait PersistentIdeaService {
  def findOne(ideaId: IdeaId): Future[Option[Idea]]
  def findOneByName(name: String): Future[Option[Idea]]
  def findAll(): Future[Seq[Idea]]
  def persist(idea: Idea): Future[Idea]
  def modify(ideaId: IdeaId, name: String): Future[Int]
}

trait DefaultPersistentIdeaServiceComponent extends PersistentIdeaServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentIdeaService = new PersistentIdeaService with ShortenedNames with StrictLogging {

    private val ideaAlias = PersistentIdea.ideaAlias
    private val column = PersistentIdea.column

    override def findOne(ideaId: IdeaId): Future[Option[Idea]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentIdea.as(ideaAlias))
            .where(sqls.eq(ideaAlias.id, ideaId.value))
        }.map(PersistentIdea.apply()).single.apply
      })

      futurePersistentTag.map(_.map(_.toIdea))
    }

    override def findOneByName(name: String): Future[Option[Idea]] = {
      implicit val context: EC = readExecutionContext
      val futurePersistentTag = Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentIdea.as(ideaAlias))
            .where(sqls.eq(ideaAlias.name, name))
        }.map(PersistentIdea.apply()).single.apply
      })

      futurePersistentTag.map(_.map(_.toIdea))
    }

    override def findAll(): Future[Seq[Idea]] = ???

    override def persist(idea: Idea): Future[Idea] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentIdea)
            .namedValues(
              column.id -> idea.ideaId.value,
              column.name -> idea.name,
              column.language -> idea.language,
              column.country -> idea.country,
              column.operation -> idea.operation,
              column.question -> idea.question,
              column.createdAt -> DateHelper.now,
              column.updatedAt -> DateHelper.now
            )
        }.execute().apply()
      }).map(_ => idea)
    }

    override def modify(ideaId: IdeaId, name: String): Future[Int] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentIdea)
            .set(column.name -> name)
            .where(
              sqls
                .eq(column.id, ideaId.value)
            )
        }.update().apply()
      })
    }
  }
}

object DefaultPersistentIdeaServiceComponent {

  case class PersistentIdea(id: String,
                            name: String,
                            language: Option[String],
                            country: Option[String],
                            operation: Option[String],
                            question: Option[String],
                            createdAt: ZonedDateTime,
                            updatedAt: ZonedDateTime) {
    def toIdea: Idea =
      Idea(
        ideaId = IdeaId(id),
        name = name,
        language = language,
        country = country,
        operation = operation,
        question = question
      )
  }

  object PersistentIdea extends SQLSyntaxSupport[PersistentIdea] with ShortenedNames with StrictLogging {

    override val columnNames: Seq[String] =
      Seq("id", "name", "language", "country", "operation", "question", "created_at", "updated_at")

    override val tableName: String = "idea"

    lazy val ideaAlias: QuerySQLSyntaxProvider[SQLSyntaxSupport[PersistentIdea], PersistentIdea] = syntax("idea")

    def apply(
      ideaResultName: ResultName[PersistentIdea] = ideaAlias.resultName
    )(resultSet: WrappedResultSet): PersistentIdea = {
      PersistentIdea.apply(
        id = resultSet.string(ideaResultName.id),
        name = resultSet.string(ideaResultName.name),
        language = resultSet.stringOpt(ideaResultName.language),
        country = resultSet.stringOpt(ideaResultName.country),
        operation = resultSet.stringOpt(ideaResultName.operation),
        question = resultSet.stringOpt(ideaResultName.question),
        createdAt = resultSet.zonedDateTime(ideaResultName.createdAt),
        updatedAt = resultSet.zonedDateTime(ideaResultName.updatedAt)
      )
    }
  }
}
