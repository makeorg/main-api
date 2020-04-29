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
import java.time.ZonedDateTime

import cats.data.NonEmptyList
import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.operation.DefaultPersistentOperationOfQuestionServiceComponent.PersistentOperationOfQuestion
import org.make.api.operation.DefaultPersistentOperationServiceComponent.PersistentOperation
import org.make.api.question.DefaultPersistentQuestionServiceComponent.PersistentQuestion
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.DateHelper
import org.make.core.operation._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentOperationOfQuestionService {
  def search(start: Int,
             end: Option[Int],
             sort: Option[String],
             order: Option[String],
             questionIds: Option[Seq[QuestionId]],
             operationIds: Option[Seq[OperationId]],
             operationKind: Option[Seq[OperationKind]],
             openAt: Option[ZonedDateTime]): Future[Seq[OperationOfQuestion]]
  def persist(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
  def modify(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
  def getById(id: QuestionId): Future[Option[OperationOfQuestion]]
  def find(operationId: Option[OperationId] = None): Future[Seq[OperationOfQuestion]]
  def delete(questionId: QuestionId): Future[Unit]
  def count(questionIds: Option[Seq[QuestionId]],
            operationIds: Option[Seq[OperationId]],
            openAt: Option[ZonedDateTime]): Future[Int]
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
      with StrictLogging {

    private val operationOfQuestionAlias = PersistentOperationOfQuestion.alias
    private val operationAlias = PersistentOperation.alias

    override def search(start: Int,
                        end: Option[Int],
                        sort: Option[String],
                        order: Option[String],
                        questionIds: Option[Seq[QuestionId]],
                        operationIds: Option[Seq[OperationId]],
                        operationKind: Option[Seq[OperationKind]],
                        openAt: Option[ZonedDateTime]): Future[scala.Seq[OperationOfQuestion]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentOperationOfQuestion] {
          val query: scalikejdbc.PagingSQLBuilder[PersistentOperationOfQuestion] = select
            .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
            .innerJoin(PersistentOperation.as(operationAlias))
            .on(operationOfQuestionAlias.operationId, operationAlias.uuid)
            .where(
              sqls.toAndConditionOpt(
                operationIds.map(
                  operations => sqls.in(PersistentOperationOfQuestion.column.operationId, operations.map(_.value))
                ),
                questionIds.map(
                  questionIds => sqls.in(PersistentOperationOfQuestion.column.questionId, questionIds.map(_.value))
                ),
                operationKind
                  .map(operationKind => sqls.in(operationAlias.operationKind, operationKind.map(_.shortName))),
                openAt.map(
                  openAt =>
                    sqls
                      .isNull(PersistentOperationOfQuestion.column.startDate)
                      .or(sqls.le(PersistentOperationOfQuestion.column.startDate, openAt))
                      .and(
                        sqls
                          .isNull(PersistentOperationOfQuestion.column.endDate)
                          .or(sqls.ge(PersistentOperationOfQuestion.column.endDate, openAt))
                    )
                )
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
              PersistentOperationOfQuestion.column.questionId -> operationOfQuestion.questionId.value,
              PersistentOperationOfQuestion.column.operationId -> operationOfQuestion.operationId.value,
              PersistentOperationOfQuestion.column.startDate -> operationOfQuestion.startDate,
              PersistentOperationOfQuestion.column.endDate -> operationOfQuestion.endDate,
              PersistentOperationOfQuestion.column.operationTitle -> operationOfQuestion.operationTitle,
              PersistentOperationOfQuestion.column.landingSequenceId -> operationOfQuestion.landingSequenceId.value,
              PersistentOperationOfQuestion.column.createdAt -> now,
              PersistentOperationOfQuestion.column.updatedAt -> now,
              PersistentOperationOfQuestion.column.canPropose -> operationOfQuestion.canPropose,
              PersistentOperationOfQuestion.column.introCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.introCard.enabled,
              PersistentOperationOfQuestion.column.introCardTitle -> operationOfQuestion.sequenceCardsConfiguration.introCard.title,
              PersistentOperationOfQuestion.column.introCardDescription -> operationOfQuestion.sequenceCardsConfiguration.introCard.description,
              PersistentOperationOfQuestion.column.pushProposalCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.pushProposalCard.enabled,
              PersistentOperationOfQuestion.column.signupCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.signUpCard.enabled,
              PersistentOperationOfQuestion.column.signupCardTitle -> operationOfQuestion.sequenceCardsConfiguration.signUpCard.title,
              PersistentOperationOfQuestion.column.signupCardNextCta -> operationOfQuestion.sequenceCardsConfiguration.signUpCard.nextCtaText,
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
              PersistentOperationOfQuestion.column.gradientStart -> operationOfQuestion.theme.gradientStart,
              PersistentOperationOfQuestion.column.gradientEnd -> operationOfQuestion.theme.gradientEnd,
              PersistentOperationOfQuestion.column.color -> operationOfQuestion.theme.color,
              PersistentOperationOfQuestion.column.fontColor -> operationOfQuestion.theme.fontColor,
              PersistentOperationOfQuestion.column.secondaryColor -> operationOfQuestion.theme.secondaryColor,
              PersistentOperationOfQuestion.column.secondaryFontColor -> operationOfQuestion.theme.secondaryFontColor,
              PersistentOperationOfQuestion.column.description -> operationOfQuestion.description,
              PersistentOperationOfQuestion.column.consultationImage -> operationOfQuestion.consultationImage,
              PersistentOperationOfQuestion.column.descriptionImage -> operationOfQuestion.descriptionImage,
              PersistentOperationOfQuestion.column.displayResults -> operationOfQuestion.displayResults
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
              PersistentOperationOfQuestion.column.signupCardEnabled -> operationOfQuestion.sequenceCardsConfiguration.signUpCard.enabled,
              PersistentOperationOfQuestion.column.signupCardTitle -> operationOfQuestion.sequenceCardsConfiguration.signUpCard.title,
              PersistentOperationOfQuestion.column.signupCardNextCta -> operationOfQuestion.sequenceCardsConfiguration.signUpCard.nextCtaText,
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
              PersistentOperationOfQuestion.column.gradientStart -> operationOfQuestion.theme.gradientStart,
              PersistentOperationOfQuestion.column.gradientEnd -> operationOfQuestion.theme.gradientEnd,
              PersistentOperationOfQuestion.column.color -> operationOfQuestion.theme.color,
              PersistentOperationOfQuestion.column.fontColor -> operationOfQuestion.theme.fontColor,
              PersistentOperationOfQuestion.column.secondaryColor -> operationOfQuestion.theme.secondaryColor,
              PersistentOperationOfQuestion.column.secondaryFontColor -> operationOfQuestion.theme.secondaryFontColor,
              PersistentOperationOfQuestion.column.description -> operationOfQuestion.description,
              PersistentOperationOfQuestion.column.consultationImage -> operationOfQuestion.consultationImage,
              PersistentOperationOfQuestion.column.descriptionImage -> operationOfQuestion.descriptionImage,
              PersistentOperationOfQuestion.column.displayResults -> operationOfQuestion.displayResults
            )
            .where(sqls.eq(PersistentOperationOfQuestion.column.questionId, operationOfQuestion.questionId.value))
        }.execute().apply()
      }).map(_ => operationOfQuestion)
    }

    override def getById(id: QuestionId): Future[Option[OperationOfQuestion]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentOperationOfQuestion] {
          select
            .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
            .where(sqls.eq(PersistentOperationOfQuestion.column.questionId, id.value))
        }.map(PersistentOperationOfQuestion(PersistentOperationOfQuestion.alias.resultName)(_)).single.apply()
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
            .where(sqls.eq(PersistentOperationOfQuestion.column.questionId, questionId.value))
        }.execute().apply()
      }).map(_ => ())
    }

    override def count(questionIds: Option[Seq[QuestionId]],
                       operationIds: Option[Seq[OperationId]],
                       openAt: Option[ZonedDateTime]): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL[PersistentOperationOfQuestion] {
          select(sqls.count)
            .from(PersistentOperationOfQuestion.as(PersistentOperationOfQuestion.alias))
            .where(
              sqls.toAndConditionOpt(
                operationIds
                  .map(opIds => sqls.in(PersistentOperationOfQuestion.column.operationId, opIds.map(_.value))),
                questionIds
                  .map(qIds => sqls.in(PersistentOperationOfQuestion.column.questionId, qIds.map(_.value))),
                openAt.map(
                  openAt =>
                    sqls
                      .isNull(PersistentOperationOfQuestion.column.startDate)
                      .or(sqls.le(PersistentOperationOfQuestion.column.startDate, openAt))
                      .and(
                        sqls
                          .isNull(PersistentOperationOfQuestion.column.endDate)
                          .or(sqls.ge(PersistentOperationOfQuestion.column.endDate, openAt))
                    )
                )
              )
            )
        }.map(_.int(1)).single.apply().getOrElse(0)
      })
    }
  }

}

object DefaultPersistentOperationOfQuestionServiceComponent {

  final case class PersistentOperationOfQuestion(
    questionId: String,
    operationId: String,
    startDate: Option[ZonedDateTime],
    endDate: Option[ZonedDateTime],
    operationTitle: String,
    landingSequenceId: String,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
    canPropose: Boolean,
    introCardEnabled: Boolean,
    introCardTitle: Option[String],
    introCardDescription: Option[String],
    pushProposalCardEnabled: Boolean,
    signupCardEnabled: Boolean,
    signupCardTitle: Option[String],
    signupCardNextCta: Option[String],
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
    gradientStart: String,
    gradientEnd: String,
    color: String,
    fontColor: String,
    secondaryColor: Option[String],
    secondaryFontColor: Option[String],
    description: String,
    consultationImage: Option[String],
    descriptionImage: Option[String],
    displayResults: Boolean
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
        signUpCard = SignUpCard(
          enabled = this.signupCardEnabled,
          title = this.signupCardTitle,
          nextCtaText = this.signupCardNextCta
        ),
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
      theme = QuestionTheme(
        gradientStart = this.gradientStart,
        gradientEnd = this.gradientEnd,
        color = this.color,
        fontColor = this.fontColor,
        secondaryColor = this.secondaryColor,
        secondaryFontColor = this.secondaryFontColor
      ),
      description = this.description,
      consultationImage = this.consultationImage,
      descriptionImage = this.descriptionImage,
      displayResults = this.displayResults
    )
  }

  implicit object PersistentOperationOfQuestion
      extends PersistentCompanion[PersistentOperationOfQuestion, OperationOfQuestion]
      with ShortenedNames
      with StrictLogging {

    final case class FlatQuestionWithDetails(
      questionId: String,
      country: String,
      language: String,
      question: String,
      shortTitle: Option[String],
      slug: String,
      operationId: String,
      startDate: Option[ZonedDateTime],
      endDate: Option[ZonedDateTime],
      operationTitle: String,
      landingSequenceId: String,
      canPropose: Boolean,
      introCardEnabled: Boolean,
      introCardTitle: Option[String],
      introCardDescription: Option[String],
      pushProposalCardEnabled: Boolean,
      signupCardEnabled: Boolean,
      signupCardTitle: Option[String],
      signupCardNextCta: Option[String],
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
      gradientStart: String,
      gradientEnd: String,
      color: String,
      fontColor: String,
      secondaryColor: Option[String],
      secondaryFontColor: Option[String],
      description: String,
      consultationImage: Option[String],
      descriptionImage: Option[String],
      displayResults: Boolean
    ) {
      def toQuestionAndDetails: QuestionWithDetails = {
        QuestionWithDetails(
          question = Question(
            questionId = QuestionId(questionId),
            country = Country(country),
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
              signUpCard =
                SignUpCard(enabled = signupCardEnabled, title = signupCardTitle, nextCtaText = signupCardNextCta),
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
            theme = QuestionTheme(
              gradientStart = this.gradientStart,
              gradientEnd = this.gradientEnd,
              color = this.color,
              fontColor = this.fontColor,
              secondaryColor = this.secondaryColor,
              secondaryFontColor = this.secondaryFontColor
            ),
            description = this.description,
            consultationImage = this.consultationImage,
            descriptionImage = this.descriptionImage,
            displayResults = this.displayResults
          )
        )

      }
    }

    def withQuestion(
      questionAlias: ResultName[PersistentQuestion],
      operationOfQuestionAlias: ResultName[PersistentOperationOfQuestion]
    )(resultSet: WrappedResultSet): Option[FlatQuestionWithDetails] = {

      for {
        country           <- resultSet.stringOpt(questionAlias.country)
        language          <- resultSet.stringOpt(questionAlias.language)
        questionId        <- resultSet.stringOpt(operationOfQuestionAlias.questionId)
        questionSlug      <- resultSet.stringOpt(questionAlias.slug)
        operationId       <- resultSet.stringOpt(operationOfQuestionAlias.operationId)
        question          <- resultSet.stringOpt(questionAlias.question)
        landingSequenceId <- resultSet.stringOpt(operationOfQuestionAlias.landingSequenceId)
        operationTitle    <- resultSet.stringOpt(operationOfQuestionAlias.operationTitle)
      } yield FlatQuestionWithDetails(
        questionId = questionId,
        country = country,
        language = language,
        question = question,
        shortTitle = resultSet.stringOpt(questionAlias.shortTitle),
        slug = questionSlug,
        operationId = operationId,
        startDate = resultSet.zonedDateTimeOpt(operationOfQuestionAlias.startDate),
        endDate = resultSet.zonedDateTimeOpt(operationOfQuestionAlias.endDate),
        operationTitle = operationTitle,
        landingSequenceId = landingSequenceId,
        canPropose = resultSet.boolean(operationOfQuestionAlias.canPropose),
        introCardEnabled = resultSet.boolean(operationOfQuestionAlias.introCardEnabled),
        introCardTitle = resultSet.stringOpt(operationOfQuestionAlias.introCardTitle),
        introCardDescription = resultSet.stringOpt(operationOfQuestionAlias.introCardDescription),
        pushProposalCardEnabled = resultSet.boolean(operationOfQuestionAlias.pushProposalCardEnabled),
        signupCardEnabled = resultSet.boolean(operationOfQuestionAlias.signupCardEnabled),
        signupCardTitle = resultSet.stringOpt(operationOfQuestionAlias.signupCardTitle),
        signupCardNextCta = resultSet.stringOpt(operationOfQuestionAlias.signupCardNextCta),
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
        gradientStart = resultSet.string(operationOfQuestionAlias.gradientStart),
        gradientEnd = resultSet.string(operationOfQuestionAlias.gradientEnd),
        color = resultSet.string(operationOfQuestionAlias.color),
        fontColor = resultSet.string(operationOfQuestionAlias.fontColor),
        secondaryColor = resultSet.stringOpt(operationOfQuestionAlias.secondaryColor),
        secondaryFontColor = resultSet.stringOpt(operationOfQuestionAlias.secondaryFontColor),
        description = resultSet.string(operationOfQuestionAlias.description),
        consultationImage = resultSet.stringOpt(operationOfQuestionAlias.consultationImage),
        descriptionImage = resultSet.stringOpt(operationOfQuestionAlias.descriptionImage),
        displayResults = resultSet.boolean(operationOfQuestionAlias.displayResults)
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
        "signup_card_enabled",
        "signup_card_title",
        "signup_card_next_cta",
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
        "gradient_start",
        "gradient_end",
        "color",
        "font_color",
        "secondary_color",
        "secondary_font_color",
        "description",
        "consultation_image",
        "description_image",
        "display_results"
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
        startDate = resultSet.zonedDateTimeOpt(resultName.startDate),
        endDate = resultSet.zonedDateTimeOpt(resultName.endDate),
        operationTitle = resultSet.string(resultName.operationTitle),
        landingSequenceId = resultSet.string(resultName.landingSequenceId),
        createdAt = resultSet.zonedDateTime(resultName.createdAt),
        updatedAt = resultSet.zonedDateTime(resultName.updatedAt),
        canPropose = resultSet.boolean(resultName.canPropose),
        introCardEnabled = resultSet.boolean(resultName.introCardEnabled),
        introCardTitle = resultSet.stringOpt(resultName.introCardTitle),
        introCardDescription = resultSet.stringOpt(resultName.introCardDescription),
        pushProposalCardEnabled = resultSet.boolean(resultName.pushProposalCardEnabled),
        signupCardEnabled = resultSet.boolean(resultName.signupCardEnabled),
        signupCardTitle = resultSet.stringOpt(resultName.signupCardTitle),
        signupCardNextCta = resultSet.stringOpt(resultName.signupCardNextCta),
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
        gradientStart = resultSet.string(resultName.gradientStart),
        gradientEnd = resultSet.string(resultName.gradientEnd),
        color = resultSet.string(resultName.color),
        fontColor = resultSet.string(resultName.fontColor),
        secondaryColor = resultSet.stringOpt(resultName.secondaryColor),
        secondaryFontColor = resultSet.stringOpt(resultName.secondaryFontColor),
        description = resultSet.string(resultName.description),
        consultationImage = resultSet.stringOpt(resultName.consultationImage),
        descriptionImage = resultSet.stringOpt(resultName.descriptionImage),
        displayResults = resultSet.boolean(resultName.displayResults)
      )
    }
  }
}
