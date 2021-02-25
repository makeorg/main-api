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

package org.make.api.migrations.db

import java.sql.Connection
import java.util.concurrent.Executors
import grizzled.slf4j.Logging
import org.make.api.crmTemplates.{
  DefaultCrmTemplatesServiceComponent,
  DefaultPersistentCrmLanguageTemplateServiceComponent,
  DefaultPersistentCrmQuestionTemplateServiceComponent
}
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.migrations.db.V98__Split_CRM_templates_by_language_and_question.languageTemplates
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.technical.{DefaultIdGeneratorComponent, ShortenedNames}
import org.make.api.technical.ExecutorServiceHelper._
import org.make.api.technical.Futures._
import org.make.core.crmTemplate.{CrmTemplateKind, TemplateId}
import org.make.core.crmTemplate.CrmTemplateKind._
import org.make.core.reference.Language

import scala.concurrent.Future

class V98__Split_CRM_templates_by_language_and_question
    extends Migration
    with DefaultCrmTemplatesServiceComponent
    with DefaultPersistentCrmLanguageTemplateServiceComponent
    with DefaultPersistentCrmQuestionTemplateServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultIdGeneratorComponent
    with MakeDBExecutionContextComponent
    with ShortenedNames
    with Logging {

  private implicit val ec: EC = Executors.newFixedThreadPool(8).toExecutionContext
  override val readExecutionContext: EC = ec
  override val writeExecutionContext: EC = ec

  override def migrate(connection: Connection): Unit = {
    languageTemplates
      .foldLeft(Future.unit) {
        case (f, (language, templates)) =>
          f.flatMap(_ => crmTemplatesService.create(Language(language), templates.andThen(TemplateId.apply)).toUnit)
      }
  }

}

object V98__Split_CRM_templates_by_language_and_question {

  def languageTemplates: Map[String, CrmTemplateKind => String] = {
    if (System.getenv("ENV_NAME") == "prod") {
      Map("fr" -> {
        case Registration         => "222475"
        case ResendRegistration   => "222475"
        case Welcome              => "235705"
        case ProposalAccepted     => "222512"
        case ProposalRefused      => "222555"
        case ForgottenPassword    => "191459"
        case B2BRegistration      => "1395993"
        case B2BProposalAccepted  => "393225"
        case B2BProposalRefused   => "393224"
        case B2BEmailChanged      => "618004"
        case B2BForgottenPassword => "618004"
      }, "en" -> {
        case Registration         => "313889"
        case ResendRegistration   => "313889"
        case Welcome              => "313893"
        case ProposalAccepted     => "313897"
        case ProposalRefused      => "313899"
        case ForgottenPassword    => "313903"
        case B2BRegistration      => "1395993"
        case B2BProposalAccepted  => "393225"
        case B2BProposalRefused   => "393224"
        case B2BEmailChanged      => "618004"
        case B2BForgottenPassword => "618004"
      })
    } else {
      Map("fr" -> {
        case Registration         => "225362"
        case ResendRegistration   => "225362"
        case Welcome              => "235799"
        case ProposalRefused      => "225359"
        case ProposalAccepted     => "225358"
        case ForgottenPassword    => "225361"
        case B2BRegistration      => "1393409"
        case B2BProposalRefused   => "393189"
        case B2BProposalAccepted  => "408740"
        case B2BEmailChanged      => "618010"
        case B2BForgottenPassword => "618010"
      }, "en" -> {
        case Registration         => "313838"
        case ResendRegistration   => "313838"
        case Welcome              => "313850"
        case ProposalRefused      => "313868"
        case ProposalAccepted     => "313860"
        case ForgottenPassword    => "313871"
        case B2BRegistration      => "1393409"
        case B2BProposalRefused   => "393189"
        case B2BProposalAccepted  => "408740"
        case B2BEmailChanged      => "618010"
        case B2BForgottenPassword => "618010"
      })
    }
  }

}
