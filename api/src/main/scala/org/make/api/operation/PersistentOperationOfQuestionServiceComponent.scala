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

package org.make.api.operation
import java.time.{LocalDate, ZonedDateTime}
import cats.Show
import cats.data.NonEmptyList
import eu.timepit.refined.W
import eu.timepit.refined.auto._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.MaxSize
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.operation.DefaultPersistentOperationOfQuestionServiceComponent.PersistentOperationOfQuestion
import org.make.api.operation.DefaultPersistentOperationOfQuestionServiceComponent.PersistentOperationOfQuestion.resultsLinkBinders
import org.make.api.operation.DefaultPersistentOperationServiceComponent.PersistentOperation
import org.make.api.question.DefaultPersistentQuestionServiceComponent.{COUNTRY_SEPARATOR, PersistentQuestion}
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.Futures._
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.api.technical.ScalikeSupport._
import org.make.core.{DateHelper, Order}
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import scalikejdbc._

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentOperationOfQuestionService {
  def search(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    questionIds: Option[Seq[QuestionId]],
    operationIds: Option[Seq[OperationId]],
    operationKind: Option[Seq[OperationKind]],
    openAt: Option[ZonedDateTime],
    endAfter: Option[ZonedDateTime],
    slug: Option[String]
  ): Future[Seq[OperationOfQuestion]]
  def persist(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
  def modify(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
  def getById(id: QuestionId): Future[Option[OperationOfQuestion]]
  def find(operationId: Option[OperationId] = None): Future[Seq[OperationOfQuestion]]
  def delete(questionId: QuestionId): Future[Unit]
  def count(
    questionIds: Option[Seq[QuestionId]],
    operationIds: Option[Seq[OperationId]],
    openAt: Option[ZonedDateTime],
    endAfter: Option[ZonedDateTime],
    slug: Option[String]
  ): Future[Int]
  // TODO: delete this method once the calling batch was run in production
  def questionIdFromSequenceId(sequenceId: SequenceId): Future[Option[QuestionId]]
}

trait PersistentOperationOfQuestionServiceComponent {
  def persistentOperationOfQuestionService: PersistentOperationOfQuestionService
}

trait DefaultPersistentOperationOfQuestionServiceComponent extends PersistentOperationOfQuestionServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentOperationOfQuestionService: DefaultPersistentOperationOfQuestionService =
    new DefaultPersistentOperationOfQuestionService

  class DefaultPersistentOperationOfQuestionService
      extends PersistentOperationOfQuestionService
      with ShortenedNames
      with Logging {

    private val operationOfQuestionAlias = PersistentOperationOfQuestion.alias
    private val operationAlias = PersistentOperation.alias

    override def search(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      questionIds: Option[Seq[QuestionId]],
      operationIds: Option[Seq[OperationId]],
      operationKind: Option[Seq[OperationKind]],
      openAt: Option[ZonedDateTime],
      endAfter: Option[ZonedDateTime],
      slug: Option[String]
    ): Future[scala.Seq[OperationOfQuestion]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentOperationOfQuestion] {
          val query: scalikejdbc.PagingSQLBuilder[PersistentOperationOfQuestion] = select
            .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
            .innerJoin(PersistentOperation.as(operationAlias))
            .on(operationOfQuestionAlias.operationId, operationAlias.uuid)
            .innerJoin(PersistentQuestion.as(PersistentQuestion.alias))
            .on(operationOfQuestionAlias.questionId, PersistentQuestion.alias.questionId)
            .where(
              sqls.toAndConditionOpt(
                operationIds
                  .map(operations => sqls.in(PersistentOperationOfQuestion.alias.operationId, operations.map(_.value))),
                questionIds.map(
                  questionIds => sqls.in(PersistentOperationOfQuestion.alias.questionId, questionIds.map(_.value))
                ),
                operationKind
                  .map(operationKind => sqls.in(operationAlias.operationKind, operationKind)),
                openAt.map(
                  openAt =>
                    sqls
                      .le(PersistentOperationOfQuestion.alias.startDate, openAt)
                      .and(sqls.ge(PersistentOperationOfQuestion.alias.endDate, openAt))
                ),
                endAfter.map(end => sqls.ge(PersistentOperationOfQuestion.alias.endDate, end)),
                slug.map(s       => sqls.like(PersistentQuestion.alias.slug, s"%$s%"))
              )
            )

          sortOrderQuery(start, end, sort, order, query)
        }.map(PersistentOperationOfQuestion(operationOfQuestionAlias.resultName)).list().apply()
      }).map(_.map(_.toOperationOfQuestion))
    }

    override def persist(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          val now = DateHelper.now()
          insert
            .into(PersistentOperationOfQuestion)
            .namedValues(
              PersistentOperationOfQuestion.column.questionId -> operationOfQuestion.questionId,
              PersistentOperationOfQuestion.column.operationId -> operationOfQuestion.operationId,
              PersistentOperationOfQuestion.column.startDate -> operationOfQuestion.startDate,
              PersistentOperationOfQuestion.column.endDate -> operationOfQuestion.endDate,
              PersistentOperationOfQuestion.column.operationTitle -> operationOfQuestion.operationTitle,
              PersistentOperationOfQuestion.column.landingSequenceId -> operationOfQuestion.landingSequenceId,
              PersistentOperationOfQuestion.column.createdAt -> now,
              PersistentOperationOfQuestion.column.updatedAt -> now,
              PersistentOperationOfQuestion.column.canPropose -> operationOfQuestion.canPropose,
              PersistentOperationOfQuestion.column.introCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.introCard.enabled,
              PersistentOperationOfQuestion.column.introCardTitle -> operationOfQuestion.sequenceCardsConfiguration.introCard.title,
              PersistentOperationOfQuestion.column.introCardDescription -> operationOfQuestion.sequenceCardsConfiguration.introCard.description,
              PersistentOperationOfQuestion.column.pushProposalCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.pushProposalCard.enabled,
              PersistentOperationOfQuestion.column.finalCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.finalCard.enabled,
              PersistentOperationOfQuestion.column.finalCardSharingEnabled -> operationOfQuestion.sequenceCardsConfiguration.finalCard.sharingEnabled,
              PersistentOperationOfQuestion.column.finalCardTitle -> operationOfQuestion.sequenceCardsConfiguration.finalCard.title,
              PersistentOperationOfQuestion.column.finalCardShareDescription -> operationOfQuestion.sequenceCardsConfiguration.finalCard.shareDescription,
              PersistentOperationOfQuestion.column.finalCardLearnMoreTitle -> operationOfQuestion.sequenceCardsConfiguration.finalCard.learnMoreTitle,
              PersistentOperationOfQuestion.column.finalCardLearnMoreButton -> operationOfQuestion.sequenceCardsConfiguration.finalCard.learnMoreTextButton,
              PersistentOperationOfQuestion.column.finalCardLinkUrl -> operationOfQuestion.sequenceCardsConfiguration.finalCard.linkUrl,
              PersistentOperationOfQuestion.column.aboutUrl -> operationOfQuestion.aboutUrl,
              PersistentOperationOfQuestion.column.metaTitle -> operationOfQuestion.metas.title,
              PersistentOperationOfQuestion.column.metaDescription -> operationOfQuestion.metas.description,
              PersistentOperationOfQuestion.column.metaPicture -> operationOfQuestion.metas.picture,
              PersistentOperationOfQuestion.column.color -> operationOfQuestion.theme.color,
              PersistentOperationOfQuestion.column.fontColor -> operationOfQuestion.theme.fontColor,
              PersistentOperationOfQuestion.column.description -> operationOfQuestion.description,
              PersistentOperationOfQuestion.column.consultationImage -> operationOfQuestion.consultationImage,
              PersistentOperationOfQuestion.column.consultationImageAlt -> operationOfQuestion.consultationImageAlt,
              PersistentOperationOfQuestion.column.descriptionImage -> operationOfQuestion.descriptionImage,
              PersistentOperationOfQuestion.column.descriptionImageAlt -> operationOfQuestion.descriptionImageAlt,
              PersistentOperationOfQuestion.column.resultsLink -> operationOfQuestion.resultsLink,
              PersistentOperationOfQuestion.column.proposalsCount -> operationOfQuestion.proposalsCount,
              PersistentOperationOfQuestion.column.participantsCount -> operationOfQuestion.participantsCount,
              PersistentOperationOfQuestion.column.actions -> operationOfQuestion.actions,
              PersistentOperationOfQuestion.column.featured -> operationOfQuestion.featured,
              PersistentOperationOfQuestion.column.votesCount -> operationOfQuestion.votesCount,
              PersistentOperationOfQuestion.column.votesTarget -> operationOfQuestion.votesTarget,
              PersistentOperationOfQuestion.column.actionDate -> operationOfQuestion.timeline.action.map(_.date),
              PersistentOperationOfQuestion.column.actionDateText -> operationOfQuestion.timeline.action
                .map(_.dateText),
              PersistentOperationOfQuestion.column.actionDescription -> operationOfQuestion.timeline.action
                .map(_.description),
              PersistentOperationOfQuestion.column.resultDate -> operationOfQuestion.timeline.result.map(_.date),
              PersistentOperationOfQuestion.column.resultDateText -> operationOfQuestion.timeline.result
                .map(_.dateText),
              PersistentOperationOfQuestion.column.resultDescription -> operationOfQuestion.timeline.result
                .map(_.description),
              PersistentOperationOfQuestion.column.workshopDate -> operationOfQuestion.timeline.workshop.map(_.date),
              PersistentOperationOfQuestion.column.workshopDateText -> operationOfQuestion.timeline.workshop
                .map(_.dateText),
              PersistentOperationOfQuestion.column.workshopDescription -> operationOfQuestion.timeline.workshop.map(
                _.description
              )
            )
        }.execute().apply()
      }).map(_ => operationOfQuestion)
    }

    override def modify(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          val now = DateHelper.now()
          update(PersistentOperationOfQuestion)
            .set(
              PersistentOperationOfQuestion.column.startDate -> operationOfQuestion.startDate,
              PersistentOperationOfQuestion.column.endDate -> operationOfQuestion.endDate,
              PersistentOperationOfQuestion.column.operationTitle -> operationOfQuestion.operationTitle,
              PersistentOperationOfQuestion.column.updatedAt -> now,
              PersistentOperationOfQuestion.column.canPropose -> operationOfQuestion.canPropose,
              PersistentOperationOfQuestion.column.introCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.introCard.enabled,
              PersistentOperationOfQuestion.column.introCardTitle -> operationOfQuestion.sequenceCardsConfiguration.introCard.title,
              PersistentOperationOfQuestion.column.introCardDescription -> operationOfQuestion.sequenceCardsConfiguration.introCard.description,
              PersistentOperationOfQuestion.column.pushProposalCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.pushProposalCard.enabled,
              PersistentOperationOfQuestion.column.finalCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.finalCard.enabled,
              PersistentOperationOfQuestion.column.finalCardSharingEnabled -> operationOfQuestion.sequenceCardsConfiguration.finalCard.sharingEnabled,
              PersistentOperationOfQuestion.column.finalCardTitle -> operationOfQuestion.sequenceCardsConfiguration.finalCard.title,
              PersistentOperationOfQuestion.column.finalCardShareDescription -> operationOfQuestion.sequenceCardsConfiguration.finalCard.shareDescription,
              PersistentOperationOfQuestion.column.finalCardLearnMoreTitle -> operationOfQuestion.sequenceCardsConfiguration.finalCard.learnMoreTitle,
              PersistentOperationOfQuestion.column.finalCardLearnMoreButton -> operationOfQuestion.sequenceCardsConfiguration.finalCard.learnMoreTextButton,
              PersistentOperationOfQuestion.column.finalCardLinkUrl -> operationOfQuestion.sequenceCardsConfiguration.finalCard.linkUrl,
              PersistentOperationOfQuestion.column.aboutUrl -> operationOfQuestion.aboutUrl,
              PersistentOperationOfQuestion.column.metaTitle -> operationOfQuestion.metas.title,
              PersistentOperationOfQuestion.column.metaDescription -> operationOfQuestion.metas.description,
              PersistentOperationOfQuestion.column.metaPicture -> operationOfQuestion.metas.picture,
              PersistentOperationOfQuestion.column.color -> operationOfQuestion.theme.color,
              PersistentOperationOfQuestion.column.fontColor -> operationOfQuestion.theme.fontColor,
              PersistentOperationOfQuestion.column.description -> operationOfQuestion.description,
              PersistentOperationOfQuestion.column.consultationImage -> operationOfQuestion.consultationImage,
              PersistentOperationOfQuestion.column.consultationImageAlt -> operationOfQuestion.consultationImageAlt,
              PersistentOperationOfQuestion.column.descriptionImage -> operationOfQuestion.descriptionImage,
              PersistentOperationOfQuestion.column.descriptionImageAlt -> operationOfQuestion.descriptionImageAlt,
              PersistentOperationOfQuestion.column.resultsLink -> operationOfQuestion.resultsLink,
              PersistentOperationOfQuestion.column.proposalsCount -> operationOfQuestion.proposalsCount,
              PersistentOperationOfQuestion.column.participantsCount -> operationOfQuestion.participantsCount,
              PersistentOperationOfQuestion.column.actions -> operationOfQuestion.actions,
              PersistentOperationOfQuestion.column.featured -> operationOfQuestion.featured,
              PersistentOperationOfQuestion.column.votesCount -> operationOfQuestion.votesCount,
              PersistentOperationOfQuestion.column.votesTarget -> operationOfQuestion.votesTarget,
              PersistentOperationOfQuestion.column.actionDate -> operationOfQuestion.timeline.action.map(_.date),
              PersistentOperationOfQuestion.column.actionDateText -> operationOfQuestion.timeline.action.map(
                _.dateText
              ),
              PersistentOperationOfQuestion.column.actionDescription -> operationOfQuestion.timeline.action.map(
                _.description
              ),
              PersistentOperationOfQuestion.column.resultDate -> operationOfQuestion.timeline.result.map(_.date),
              PersistentOperationOfQuestion.column.resultDateText -> operationOfQuestion.timeline.result.map(
                _.dateText
              ),
              PersistentOperationOfQuestion.column.resultDescription -> operationOfQuestion.timeline.result.map(
                _.description
              ),
              PersistentOperationOfQuestion.column.workshopDate -> operationOfQuestion.timeline.workshop.map(_.date),
              PersistentOperationOfQuestion.column.workshopDateText -> operationOfQuestion.timeline.workshop.map(
                _.dateText
              ),
              PersistentOperationOfQuestion.column.workshopDescription -> operationOfQuestion.timeline.workshop.map(
                _.description
              )
            )
            .where(sqls.eq(PersistentOperationOfQuestion.column.questionId, operationOfQuestion.questionId))
        }.execute().apply()
      }).map(_ => operationOfQuestion)
    }

    override def getById(id: QuestionId): Future[Option[OperationOfQuestion]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentOperationOfQuestion] {
          select
            .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
            .where(sqls.eq(PersistentOperationOfQuestion.column.questionId, id))
        }.map(PersistentOperationOfQuestion(PersistentOperationOfQuestion.alias.resultName)(_)).single().apply()
      }).map(_.map(_.toOperationOfQuestion))
    }

    override def find(operationId: Option[OperationId]): Future[Seq[OperationOfQuestion]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentOperationOfQuestion] {
          select
            .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
            .where(
              sqls.toAndConditionOpt(
                operationId
                  .map(operation => sqls.eq(PersistentOperationOfQuestion.column.operationId, operation.value))
              )
            )
        }.map(PersistentOperationOfQuestion(PersistentOperationOfQuestion.alias.resultName)).list().apply()
      }).map(_.map(_.toOperationOfQuestion))
    }

    override def delete(questionId: QuestionId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentOperationOfQuestion)
            .where(sqls.eq(PersistentOperationOfQuestion.column.questionId, questionId))
        }.execute().apply()
      }).toUnit
    }

    override def count(
      questionIds: Option[Seq[QuestionId]],
      operationIds: Option[Seq[OperationId]],
      openAt: Option[ZonedDateTime],
      endAfter: Option[ZonedDateTime],
      slug: Option[String]
    ): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentOperationOfQuestion] {
          select(sqls.count)
            .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
            .innerJoin(PersistentQuestion.as(PersistentQuestion.alias))
            .on(operationOfQuestionAlias.questionId, PersistentQuestion.alias.questionId)
            .where(
              sqls.toAndConditionOpt(
                operationIds
                  .map(opIds => sqls.in(PersistentOperationOfQuestion.alias.operationId, opIds.map(_.value))),
                questionIds
                  .map(qIds => sqls.in(PersistentOperationOfQuestion.alias.questionId, qIds.map(_.value))),
                openAt.map(
                  openAt =>
                    sqls
                      .le(PersistentOperationOfQuestion.alias.startDate, openAt)
                      .and(sqls.ge(PersistentOperationOfQuestion.alias.endDate, openAt))
                ),
                endAfter.map(end => sqls.ge(PersistentOperationOfQuestion.alias.endDate, end)),
                slug.map(s       => sqls.like(PersistentQuestion.alias.slug, s"%$s%"))
              )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }

    override def questionIdFromSequenceId(sequenceId: SequenceId): Future[Option[QuestionId]] = {
      implicit val context: EC = readExecutionContext
      val ooq = PersistentOperationOfQuestion.alias
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(ooq.questionId)
            .from(PersistentOperationOfQuestion.as(ooq))
            .where(sqls.eq(ooq.landingSequenceId, sequenceId.value))
        }.map { resultSet =>
          QuestionId(resultSet.string(1))
        }.single().apply()
      })
    }
  }
}

object DefaultPersistentOperationOfQuestionServiceComponent {

  final case class PersistentOperationOfQuestion(
    questionId: String,
    operationId: String,
    startDate: ZonedDateTime,
    endDate: ZonedDateTime,
    operationTitle: String,
    landingSequenceId: String,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
    canPropose: Boolean,
    introCardEnabled: Boolean,
    introCardTitle: Option[String],
    introCardDescription: Option[String],
    pushProposalCardEnabled: Boolean,
    finalCardEnabled: Boolean,
    finalCardSharingEnabled: Boolean,
    finalCardTitle: Option[String],
    finalCardShareDescription: Option[String],
    finalCardLearnMoreTitle: Option[String],
    finalCardLearnMoreButton: Option[String],
    finalCardLinkUrl: Option[String],
    aboutUrl: Option[String],
    metaTitle: Option[String],
    metaDescription: Option[String],
    metaPicture: Option[String],
    color: String,
    fontColor: String,
    description: String,
    consultationImage: Option[String],
    consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
    descriptionImage: Option[String],
    descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]],
    resultsLink: Option[ResultsLink],
    proposalsCount: Int,
    participantsCount: Int,
    actions: Option[String],
    featured: Boolean,
    votesCount: Int,
    votesTarget: Int,
    actionDate: Option[LocalDate],
    actionDateText: Option[String Refined MaxSize[W.`20`.T]],
    actionDescription: Option[String Refined MaxSize[W.`150`.T]],
    resultDate: Option[LocalDate],
    resultDateText: Option[String Refined MaxSize[W.`20`.T]],
    resultDescription: Option[String Refined MaxSize[W.`150`.T]],
    workshopDate: Option[LocalDate],
    workshopDateText: Option[String Refined MaxSize[W.`20`.T]],
    workshopDescription: Option[String Refined MaxSize[W.`150`.T]]
  ) {
    def toOperationOfQuestion: OperationOfQuestion = OperationOfQuestion(
      questionId = QuestionId(this.questionId),
      operationId = OperationId(this.operationId),
      startDate = this.startDate,
      endDate = this.endDate,
      operationTitle = this.operationTitle,
      landingSequenceId = SequenceId(this.landingSequenceId),
      canPropose = this.canPropose,
      sequenceCardsConfiguration = SequenceCardsConfiguration(
        introCard = IntroCard(
          enabled = this.introCardEnabled,
          title = this.introCardTitle,
          description = this.introCardDescription
        ),
        pushProposalCard = PushProposalCard(enabled = this.pushProposalCardEnabled),
        finalCard = FinalCard(
          enabled = this.finalCardEnabled,
          sharingEnabled = this.finalCardSharingEnabled,
          title = this.finalCardTitle,
          shareDescription = this.finalCardShareDescription,
          learnMoreTitle = this.finalCardLearnMoreTitle,
          learnMoreTextButton = this.finalCardLearnMoreButton,
          linkUrl = this.finalCardLinkUrl
        )
      ),
      aboutUrl = this.aboutUrl,
      metas = Metas(title = this.metaTitle, description = this.metaDescription, picture = this.metaPicture),
      theme = QuestionTheme(color = this.color, fontColor = this.fontColor),
      description = this.description,
      consultationImage = this.consultationImage,
      consultationImageAlt = this.consultationImageAlt,
      descriptionImage = this.descriptionImage,
      descriptionImageAlt = this.descriptionImageAlt,
      resultsLink = this.resultsLink,
      proposalsCount = this.proposalsCount,
      participantsCount = this.participantsCount,
      actions = this.actions,
      featured = this.featured,
      votesTarget = this.votesTarget,
      votesCount = this.votesCount,
      timeline = OperationOfQuestionTimeline(
        action = (this.actionDate, this.actionDateText, this.actionDescription) match {
          case (Some(date), Some(dateText), Some(description)) =>
            Some(TimelineElement(date = date, dateText = dateText, description = description))
          case _ => None
        },
        result = (this.resultDate, this.resultDateText, this.resultDescription) match {
          case (Some(date), Some(dateText), Some(description)) =>
            Some(TimelineElement(date = date, dateText = dateText, description = description))
          case _ => None
        },
        workshop = (this.workshopDate, this.workshopDateText, this.workshopDescription) match {
          case (Some(date), Some(dateText), Some(description)) =>
            Some(TimelineElement(date = date, dateText = dateText, description = description))
          case _ => None
        }
      ),
      createdAt = this.createdAt
    )
  }

  implicit object PersistentOperationOfQuestion
      extends PersistentCompanion[PersistentOperationOfQuestion, OperationOfQuestion]
      with ShortenedNames
      with Logging {

    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    implicit val resultsLinkBinders: Binders[Option[ResultsLink]] =
      Binders.string.xmap(s => Option(s).flatMap(ResultsLink.parse), _.map(Show[ResultsLink].show).orNull)

    final case class FlatQuestionWithDetails(
      questionId: String,
      countries: String,
      language: String,
      question: String,
      shortTitle: Option[String],
      slug: String,
      operationId: String,
      startDate: ZonedDateTime,
      endDate: ZonedDateTime,
      operationTitle: String,
      landingSequenceId: String,
      canPropose: Boolean,
      introCardEnabled: Boolean,
      introCardTitle: Option[String],
      introCardDescription: Option[String],
      pushProposalCardEnabled: Boolean,
      finalCardEnabled: Boolean,
      finalCardSharingEnabled: Boolean,
      finalCardTitle: Option[String],
      finalCardShareDescription: Option[String],
      finalCardLearnMoreTitle: Option[String],
      finalCardLearnMoreButton: Option[String],
      finalCardLinkUrl: Option[String],
      aboutUrl: Option[String],
      metaTitle: Option[String],
      metaDescription: Option[String],
      metaPicture: Option[String],
      color: String,
      fontColor: String,
      description: String,
      consultationImage: Option[String],
      consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
      descriptionImage: Option[String],
      descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]],
      resultsLink: Option[ResultsLink],
      proposalsCount: Int,
      participantsCount: Int,
      actions: Option[String],
      featured: Boolean,
      votesCount: Int,
      votesTarget: Int,
      actionDate: Option[LocalDate],
      actionDateText: Option[String Refined MaxSize[W.`20`.T]],
      actionDescription: Option[String Refined MaxSize[W.`150`.T]],
      resultDate: Option[LocalDate],
      resultDateText: Option[String Refined MaxSize[W.`20`.T]],
      resultDescription: Option[String Refined MaxSize[W.`150`.T]],
      workshopDate: Option[LocalDate],
      workshopDateText: Option[String Refined MaxSize[W.`20`.T]],
      workshopDescription: Option[String Refined MaxSize[W.`150`.T]],
      createdAt: ZonedDateTime
    ) {
      def toQuestionAndDetails: QuestionWithDetails = {
        QuestionWithDetails(
          question = Question(
            questionId = QuestionId(questionId),
            countries = NonEmptyList.fromListUnsafe(countries.split(COUNTRY_SEPARATOR).map(Country.apply).toList),
            language = Language(language),
            slug = slug,
            question = question,
            shortTitle = shortTitle,
            operationId = Some(OperationId(operationId))
          ),
          details = OperationOfQuestion(
            questionId = QuestionId(questionId),
            operationId = OperationId(operationId),
            startDate = startDate,
            endDate = endDate,
            operationTitle = operationTitle,
            landingSequenceId = SequenceId(landingSequenceId),
            canPropose = canPropose,
            sequenceCardsConfiguration = SequenceCardsConfiguration(
              introCard =
                IntroCard(enabled = introCardEnabled, title = introCardTitle, description = introCardDescription),
              pushProposalCard = PushProposalCard(enabled = pushProposalCardEnabled),
              finalCard = FinalCard(
                enabled = finalCardEnabled,
                sharingEnabled = finalCardSharingEnabled,
                title = finalCardTitle,
                shareDescription = finalCardShareDescription,
                learnMoreTitle = finalCardLearnMoreTitle,
                learnMoreTextButton = finalCardLearnMoreButton,
                linkUrl = finalCardLinkUrl
              )
            ),
            aboutUrl = aboutUrl,
            metas = Metas(title = metaTitle, description = metaDescription, picture = metaPicture),
            theme = QuestionTheme(color = this.color, fontColor = this.fontColor),
            description = this.description,
            consultationImage = this.consultationImage,
            consultationImageAlt = this.consultationImageAlt,
            descriptionImage = this.descriptionImage,
            descriptionImageAlt = this.descriptionImageAlt,
            resultsLink = this.resultsLink,
            proposalsCount = this.proposalsCount,
            participantsCount = this.participantsCount,
            actions = this.actions,
            featured = this.featured,
            votesCount = this.votesCount,
            votesTarget = this.votesTarget,
            timeline = OperationOfQuestionTimeline(
              action = (this.actionDate, this.actionDateText, this.actionDescription) match {
                case (Some(date), Some(dateText), Some(description)) =>
                  Some(TimelineElement(date = date, dateText = dateText, description = description))
                case _ => None
              },
              result = (this.resultDate, this.resultDateText, this.resultDescription) match {
                case (Some(date), Some(dateText), Some(description)) =>
                  Some(TimelineElement(date = date, dateText = dateText, description = description))
                case _ => None
              },
              workshop = (this.workshopDate, this.workshopDateText, this.workshopDescription) match {
                case (Some(date), Some(dateText), Some(description)) =>
                  Some(TimelineElement(date = date, dateText = dateText, description = description))
                case _ => None
              }
            ),
            createdAt = this.createdAt
          )
        )

      }
    }

    def withQuestion(
      questionAlias: ResultName[PersistentQuestion],
      operationOfQuestionAlias: ResultName[PersistentOperationOfQuestion]
    )(resultSet: WrappedResultSet): Option[FlatQuestionWithDetails] = {

      for {
        countries         <- resultSet.stringOpt(questionAlias.countries)
        language          <- resultSet.stringOpt(questionAlias.language)
        questionId        <- resultSet.stringOpt(operationOfQuestionAlias.questionId)
        questionSlug      <- resultSet.stringOpt(questionAlias.slug)
        operationId       <- resultSet.stringOpt(operationOfQuestionAlias.operationId)
        question          <- resultSet.stringOpt(questionAlias.question)
        landingSequenceId <- resultSet.stringOpt(operationOfQuestionAlias.landingSequenceId)
        operationTitle    <- resultSet.stringOpt(operationOfQuestionAlias.operationTitle)
      } yield FlatQuestionWithDetails(
        questionId = questionId,
        countries = countries,
        language = language,
        question = question,
        shortTitle = resultSet.stringOpt(questionAlias.shortTitle),
        slug = questionSlug,
        operationId = operationId,
        startDate = resultSet.zonedDateTime(operationOfQuestionAlias.startDate),
        endDate = resultSet.zonedDateTime(operationOfQuestionAlias.endDate),
        operationTitle = operationTitle,
        landingSequenceId = landingSequenceId,
        canPropose = resultSet.boolean(operationOfQuestionAlias.canPropose),
        introCardEnabled = resultSet.boolean(operationOfQuestionAlias.introCardEnabled),
        introCardTitle = resultSet.stringOpt(operationOfQuestionAlias.introCardTitle),
        introCardDescription = resultSet.stringOpt(operationOfQuestionAlias.introCardDescription),
        pushProposalCardEnabled = resultSet.boolean(operationOfQuestionAlias.pushProposalCardEnabled),
        finalCardEnabled = resultSet.boolean(operationOfQuestionAlias.finalCardEnabled),
        finalCardSharingEnabled = resultSet.boolean(operationOfQuestionAlias.finalCardSharingEnabled),
        finalCardTitle = resultSet.stringOpt(operationOfQuestionAlias.finalCardTitle),
        finalCardShareDescription = resultSet.stringOpt(operationOfQuestionAlias.finalCardShareDescription),
        finalCardLearnMoreTitle = resultSet.stringOpt(operationOfQuestionAlias.finalCardLearnMoreTitle),
        finalCardLearnMoreButton = resultSet.stringOpt(operationOfQuestionAlias.finalCardLearnMoreButton),
        finalCardLinkUrl = resultSet.stringOpt(operationOfQuestionAlias.finalCardLinkUrl),
        aboutUrl = resultSet.stringOpt(operationOfQuestionAlias.aboutUrl),
        metaTitle = resultSet.stringOpt(operationOfQuestionAlias.metaTitle),
        metaDescription = resultSet.stringOpt(operationOfQuestionAlias.metaDescription),
        metaPicture = resultSet.stringOpt(operationOfQuestionAlias.metaPicture),
        color = resultSet.string(operationOfQuestionAlias.color),
        fontColor = resultSet.string(operationOfQuestionAlias.fontColor),
        description = resultSet.string(operationOfQuestionAlias.description),
        consultationImage = resultSet.stringOpt(operationOfQuestionAlias.consultationImage),
        consultationImageAlt = resultSet.get(operationOfQuestionAlias.consultationImageAlt),
        descriptionImage = resultSet.stringOpt(operationOfQuestionAlias.descriptionImage),
        descriptionImageAlt = resultSet.get(operationOfQuestionAlias.descriptionImageAlt),
        resultsLink = resultSet.get(operationOfQuestionAlias.resultsLink),
        proposalsCount = resultSet.int(operationOfQuestionAlias.proposalsCount),
        participantsCount = resultSet.int(operationOfQuestionAlias.participantsCount),
        actions = resultSet.stringOpt(operationOfQuestionAlias.actions),
        featured = resultSet.boolean(operationOfQuestionAlias.featured),
        votesCount = resultSet.int(operationOfQuestionAlias.votesCount),
        votesTarget = resultSet.int(operationOfQuestionAlias.votesTarget),
        actionDate = resultSet.localDateOpt(operationOfQuestionAlias.actionDate),
        actionDateText = resultSet.get(operationOfQuestionAlias.actionDateText),
        actionDescription = resultSet.get(operationOfQuestionAlias.actionDescription),
        resultDate = resultSet.localDateOpt(operationOfQuestionAlias.resultDate),
        resultDateText = resultSet.get(operationOfQuestionAlias.resultDateText),
        resultDescription = resultSet.get(operationOfQuestionAlias.resultDescription),
        workshopDate = resultSet.localDateOpt(operationOfQuestionAlias.workshopDate),
        workshopDateText = resultSet.get(operationOfQuestionAlias.workshopDateText),
        workshopDescription = resultSet.get(operationOfQuestionAlias.workshopDescription),
        createdAt = resultSet.zonedDateTime(operationOfQuestionAlias.createdAt)
      )
    }

    override val columnNames: Seq[String] =
      Seq(
        "question_id",
        "operation_id",
        "start_date",
        "end_date",
        "operation_title",
        "landing_sequence_id",
        "created_at",
        "updated_at",
        "can_propose",
        "intro_card_enabled",
        "intro_card_title",
        "intro_card_description",
        "push_proposal_card_enabled",
        "final_card_enabled",
        "final_card_sharing_enabled",
        "final_card_title",
        "final_card_share_description",
        "final_card_learn_more_title",
        "final_card_learn_more_button",
        "final_card_link_url",
        "about_url",
        "meta_title",
        "meta_description",
        "meta_picture",
        "color",
        "font_color",
        "description",
        "consultation_image",
        "consultation_image_alt",
        "description_image",
        "description_image_alt",
        "results_link",
        "proposals_count",
        "participants_count",
        "actions",
        "featured",
        "votes_count",
        "votes_target",
        "action_date",
        "action_date_text",
        "action_description",
        "result_date",
        "result_date_text",
        "result_description",
        "workshop_date",
        "workshop_date_text",
        "workshop_description"
      )

    override val tableName: String = "operation_of_question"

    override lazy val alias: SyntaxProvider[PersistentOperationOfQuestion] = syntax("operationOfquestion")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.operationTitle)

    def apply(
      resultName: ResultName[PersistentOperationOfQuestion] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentOperationOfQuestion = {
      PersistentOperationOfQuestion(
        questionId = resultSet.string(resultName.questionId),
        operationId = resultSet.string(resultName.operationId),
        startDate = resultSet.zonedDateTime(resultName.startDate),
        endDate = resultSet.zonedDateTime(resultName.endDate),
        operationTitle = resultSet.string(resultName.operationTitle),
        landingSequenceId = resultSet.string(resultName.landingSequenceId),
        createdAt = resultSet.zonedDateTime(resultName.createdAt),
        updatedAt = resultSet.zonedDateTime(resultName.updatedAt),
        canPropose = resultSet.boolean(resultName.canPropose),
        introCardEnabled = resultSet.boolean(resultName.introCardEnabled),
        introCardTitle = resultSet.stringOpt(resultName.introCardTitle),
        introCardDescription = resultSet.stringOpt(resultName.introCardDescription),
        pushProposalCardEnabled = resultSet.boolean(resultName.pushProposalCardEnabled),
        finalCardEnabled = resultSet.boolean(resultName.finalCardEnabled),
        finalCardSharingEnabled = resultSet.boolean(resultName.finalCardSharingEnabled),
        finalCardTitle = resultSet.stringOpt(resultName.finalCardTitle),
        finalCardShareDescription = resultSet.stringOpt(resultName.finalCardShareDescription),
        finalCardLearnMoreTitle = resultSet.stringOpt(resultName.finalCardLearnMoreTitle),
        finalCardLearnMoreButton = resultSet.stringOpt(resultName.finalCardLearnMoreButton),
        finalCardLinkUrl = resultSet.stringOpt(resultName.finalCardLinkUrl),
        aboutUrl = resultSet.stringOpt(resultName.aboutUrl),
        metaTitle = resultSet.stringOpt(resultName.metaTitle),
        metaDescription = resultSet.stringOpt(resultName.metaDescription),
        metaPicture = resultSet.stringOpt(resultName.metaPicture),
        color = resultSet.string(resultName.color),
        fontColor = resultSet.string(resultName.fontColor),
        description = resultSet.string(resultName.description),
        consultationImage = resultSet.stringOpt(resultName.consultationImage),
        consultationImageAlt = resultSet.get(resultName.consultationImageAlt),
        descriptionImage = resultSet.stringOpt(resultName.descriptionImage),
        descriptionImageAlt = resultSet.get(resultName.descriptionImageAlt),
        resultsLink = resultSet.get(resultName.resultsLink),
        proposalsCount = resultSet.int(resultName.proposalsCount),
        participantsCount = resultSet.int(resultName.participantsCount),
        actions = resultSet.stringOpt(resultName.actions),
        featured = resultSet.boolean(resultName.featured),
        votesCount = resultSet.int(resultName.votesCount),
        votesTarget = resultSet.int(resultName.votesTarget),
        actionDate = resultSet.localDateOpt(resultName.actionDate),
        actionDateText = resultSet.get(resultName.actionDateText),
        actionDescription = resultSet.get(resultName.actionDescription),
        resultDate = resultSet.localDateOpt(resultName.resultDate),
        resultDateText = resultSet.get(resultName.resultDateText),
        resultDescription = resultSet.get(resultName.resultDescription),
        workshopDate = resultSet.localDateOpt(resultName.workshopDate),
        workshopDateText = resultSet.get(resultName.workshopDateText),
        workshopDescription = resultSet.get(resultName.workshopDescription)
      )
    }
  }
}
