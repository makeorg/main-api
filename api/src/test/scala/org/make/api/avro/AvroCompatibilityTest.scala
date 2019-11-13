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

package org.make.api.avro

import com.sksamuel.avro4s.SchemaFor
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.Schema
import org.make.api.MakeUnitTest
import org.make.api.idea.IdeaEvent.IdeaEventWrapper
import org.make.api.proposal.PublishedProposalEvent.ProposalEventWrapper
import org.make.api.semantic.{PredictDuplicateEventWrapper, PredictionsEventWrapper}
import org.make.api.technical.AvroSerializers
import org.make.api.technical.crm.{MailJetEventWrapper, SendEmail}
import org.make.api.technical.tracking.TrackingEventWrapper
import org.make.api.userhistory.UserEvent.UserEventWrapper

class AvroCompatibilityTest extends MakeUnitTest with AvroSerializers with StrictLogging {

  feature("avro schemas") {
    scenario("check avro compatibility for SendEmail") {
      val currentSchema: Schema = SchemaFor[SendEmail]()
      checkEntityType(currentSchema, "SendEmail")
    }
    scenario("check avro compatibility for UserEventWrapper") {
      val currentSchema: Schema = SchemaFor[UserEventWrapper]()
      checkEntityType(currentSchema, "UserEventWrapper")
    }
    scenario("check avro compatibility for ProposalEventWrapper") {
      val currentSchema: Schema = SchemaFor[ProposalEventWrapper]()
      checkEntityType(currentSchema, "ProposalEventWrapper")
    }
    scenario("check avro compatibility for MailJetEventWrapper") {
      val currentSchema: Schema = SchemaFor[MailJetEventWrapper]()
      checkEntityType(currentSchema, "MailJetEventWrapper")
    }
    scenario("check avro compatibility for TrackingEventWrapper") {
      val currentSchema: Schema = SchemaFor[TrackingEventWrapper]()
      checkEntityType(currentSchema, "TrackingEventWrapper")
    }
    scenario("check avro compatibility for PredictionsEventWrapper") {
      val currentSchema: Schema = SchemaFor[PredictionsEventWrapper]()
      checkEntityType(currentSchema, "PredictionsEventWrapper")
    }
    scenario("check avro compatibility for IdeaEventWrapper") {
      val currentSchema: Schema = SchemaFor[IdeaEventWrapper]()
      checkEntityType(currentSchema, "IdeaEventWrapper")
    }
    scenario("check avro compatibility for PredictDuplicateEventWrapper") {
      val currentSchema: Schema = SchemaFor[PredictDuplicateEventWrapper]()
      checkEntityType(currentSchema, "PredictDuplicateEventWrapper")
    }
  }

  private def checkEntityType(currentSchema: Schema, name: String): Unit = {
    logger.info(s"Current schema for $name is $currentSchema")
    val schemas = AvroCompatibilityChecker.loadSchemas(name)

    if (schemas.isEmpty) {
      fail(s"No schema found for entity $name!")
    }

    // Checking that all schemas are compatible one with another
    val schemasWithNextVersions: Seq[(Schema, Schema)] = schemas.zip(schemas.drop(1))
    schemasWithNextVersions.foreach {
      case (oldSchema, newerSchema) =>
        AvroCompatibilityChecker.isCompatible(newerSchema, oldSchema) should be(true)
    }

    // check that the current schema is in the list of schemas
    currentSchema should be(schemas.last)
  }
}
