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

package org.make.api.technical.demographics

import org.make.api.technical.security.AESEncryptionComponent
import org.make.core.DateHelper
import org.make.core.question.QuestionId
import org.make.core.reference.Country

import java.time.ZonedDateTime

trait DemographicsServiceComponent {
  def demographicsService: DemographicsService
}

trait DemographicsService {
  def generateToken(cardName: String, country: Country, question: QuestionId): String
}

trait DefaultDemographicsServiceComponent extends DemographicsServiceComponent {
  this: AESEncryptionComponent =>

  override lazy val demographicsService: DefaultDemographicsService = new DefaultDemographicsService

  class DefaultDemographicsService extends DemographicsService {
    override def generateToken(cardName: String, country: Country, question: QuestionId): String = {
      val nowDate: ZonedDateTime = DateHelper.now()
      val token = DemographicToken(nowDate, cardName, country, question)
      aesEncryption.encryptAndEncode(token.toTokenizedString())
    }
  }
}
