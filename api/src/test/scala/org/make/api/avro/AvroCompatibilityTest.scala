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

import com.sksamuel.avro4s.DefaultFieldMapper
import grizzled.slf4j.Logging
import org.apache.avro.Schema
import org.make.api.MakeUnitTest
import org.make.api.idea.IdeaEventWrapper
import org.make.api.proposal.PublishedProposalEvent.ProposalEventWrapper
import org.make.api.semantic.{PredictDuplicateEventWrapper, PredictionsEventWrapper}
import org.make.api.technical.crm.{MailJetEventWrapper, SendEmail}
import org.make.api.technical.tracking.{DemographicEventWrapper, TrackingEventWrapper}
import org.make.api.userhistory.UserEventWrapper
import org.make.core.AvroSerializers

class AvroCompatibilityTest extends MakeUnitTest with AvroSerializers with Logging {

  Feature("avro schemas") {

    Seq(
      ("SendEmail", SendEmail.schemaFor),
      ("UserEventWrapper", UserEventWrapper.schemaFor),
      ("ProposalEventWrapper", ProposalEventWrapper.schemaFor),
      ("MailJetEventWrapper", MailJetEventWrapper.schemaFor),
      ("TrackingEventWrapper", TrackingEventWrapper.schemaFor),
      ("PredictionsEventWrapper", PredictionsEventWrapper.schemaFor),
      ("IdeaEventWrapper", IdeaEventWrapper.schemaFor),
      ("PredictDuplicateEventWrapper", PredictDuplicateEventWrapper.schemaFor),
      ("DemographicEventWrapper", DemographicEventWrapper.schemaFor)
    ).foreach {
      case (name, schema) =>
        Scenario(s"check avro compatibility for $name") {
          checkAvroCompatibilityForEntity(name, schema.schema(DefaultFieldMapper))
        }
    }
  }

  private def checkAvroCompatibilityForEntity(name: String, currentSchema: Schema): Unit = {
    val schemas = AvroCompatibilityChecker.loadSchemas(name)
    ensureCurrentSchemaIsLatest(name, currentSchema, schemas)
    ensureSchemasAreCompatibleWithPreviousVersion(schemas)
  }

  private def ensureCurrentSchemaIsLatest(name: String, currentSchema: Schema, schemas: Seq[Schema]): Unit = {
    schemas.lastOption match {
      case None =>
        fail(s"No schema found for entity $name!")
      case Some(latest) =>
        if (latest != currentSchema) {
          fail(s"Schema not up-to-date for entity $name.\nSchema is:\n${currentSchema.toString(false)}")
        }
    }
  }

  private def ensureSchemasAreCompatibleWithPreviousVersion(schemas: Seq[Schema]): Unit = {
    val schemasWithNextVersions: Seq[(Schema, Schema)] = schemas.zip(schemas.drop(1))
    schemasWithNextVersions.foreach {
      case (oldSchema, newerSchema) =>
        AvroCompatibilityChecker.isCompatible(newerSchema, oldSchema) should be(true)
    }
  }

}
