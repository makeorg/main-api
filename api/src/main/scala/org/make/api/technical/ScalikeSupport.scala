/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical

import enumeratum.values.{StringEnum, StringEnumEntry}
import enumeratum.{Enum, EnumEntry}
import eu.timepit.refined.api.{RefType, Refined, Validate}
import eu.timepit.refined.refineV
import org.make.core.StringValue
import org.make.core.crmTemplate.{CrmLanguageTemplateId, CrmQuestionTemplateId, CrmTemplateKind, TemplateId}
import org.make.core.demographics.{ActiveDemographicsCardId, DemographicsCard, DemographicsCardId}
import org.make.core.feature.{ActiveFeatureId, FeatureId}
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.{ExplorationSequenceConfigurationId, ExplorationSortAlgorithm, SequenceId}
import org.make.core.tag.{TagId, TagTypeId}
import org.make.core.user.{UserId, UserType}
import org.make.core.widget.{SourceId, Widget, WidgetId}
import scalikejdbc.{Binders, ParameterBinderFactory, TypeBinder}

object ScalikeSupport {

  def enumBinders[A <: EnumEntry](implicit enum: Enum[A]): Binders[A] = Binders.string.xmap(enum.withName, _.entryName)

  implicit val crmTemplateKindBinders: Binders[CrmTemplateKind] = enumBinders
  implicit val demographicsCardLayoutBinders: Binders[DemographicsCard.Layout] = enumBinders
  implicit val widgetVersionBinders: Binders[Widget.Version] = enumBinders

  implicit def stringEnumBinders[A <: StringEnumEntry](implicit enum: StringEnum[A]): Binders[A] =
    Binders.string.xmap(enum.withValue, _.value)

  implicit def stringEnumEntryParameterBinderFactory[A <: StringEnumEntry, B <: A]: ParameterBinderFactory[B] =
    ParameterBinderFactory.stringParameterBinderFactory.contramap(_.value)

  def stringValueBinders[A <: StringValue](f: String => A): Binders[A] = Binders.string.xmap(f, _.value)

  implicit val activeDemographicsCardIdBinders: Binders[ActiveDemographicsCardId] = stringValueBinders(
    ActiveDemographicsCardId.apply
  )
  implicit val activeFeatureIdBinders: Binders[ActiveFeatureId] = stringValueBinders(ActiveFeatureId.apply)
  implicit val crmLanguageTemplateIdBinders: Binders[CrmLanguageTemplateId] = stringValueBinders(
    CrmLanguageTemplateId.apply
  )
  implicit val crmQuestionTemplateIdBinders: Binders[CrmQuestionTemplateId] = stringValueBinders(
    CrmQuestionTemplateId.apply
  )
  implicit val countryBinders: Binders[Country] = stringValueBinders(Country.apply)
  implicit val demographicsCardIdBinders: Binders[DemographicsCardId] = stringValueBinders(DemographicsCardId.apply)
  implicit val featureIdBinders: Binders[FeatureId] = stringValueBinders(FeatureId.apply)
  implicit val languageBinders: Binders[Language] = stringValueBinders(Language.apply)
  implicit val operationIdBinders: Binders[OperationId] = stringValueBinders(OperationId.apply)
  implicit val questionIdBinders: Binders[QuestionId] = stringValueBinders(QuestionId.apply)
  implicit val sequenceIdBinders: Binders[SequenceId] = stringValueBinders(SequenceId.apply)
  implicit val sourceIdBinders: Binders[SourceId] = stringValueBinders(SourceId.apply)
  implicit val tagIdBinders: Binders[TagId] = stringValueBinders(TagId.apply)
  implicit val tagTypeIdBinders: Binders[TagTypeId] = stringValueBinders(TagTypeId.apply)
  implicit val templateIdBinders: Binders[TemplateId] = stringValueBinders(TemplateId.apply)
  implicit val userIdBinders: Binders[UserId] = stringValueBinders(UserId.apply)
  implicit val widgetIdBinders: Binders[WidgetId] = stringValueBinders(WidgetId.apply)
  implicit val ExplorationSequenceConfigurationIdBinders: Binders[ExplorationSequenceConfigurationId] =
    stringValueBinders(ExplorationSequenceConfigurationId.apply)

  implicit val userTypeBinders: Binders[UserType] = stringEnumBinders[UserType]
  implicit val explorationSortAlgorithmBinders: Binders[ExplorationSortAlgorithm] =
    stringEnumBinders[ExplorationSortAlgorithm]

  /*
   * The following code is a copy-paste from https://github.com/katainaka0503/scalikejdbc-refined
   **/
  implicit def refinedParameterBinderFactory[T, P, F[_, _]](
    implicit
    based: ParameterBinderFactory[T],
    refType: RefType[F]
  ): ParameterBinderFactory[F[T, P]] =
    based.contramap(refType.unwrap)

  implicit def refinedTypeBinder[T, P](
    implicit validate: Validate[T, P],
    based: TypeBinder[T]
  ): TypeBinder[Refined[T, P]] =
    based.map(refineV[P].unsafeFrom(_))
}
