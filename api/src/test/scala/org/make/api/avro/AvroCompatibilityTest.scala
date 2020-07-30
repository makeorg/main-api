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

import java.nio.file.Files

import com.sksamuel.avro4s.DefaultFieldMapper
import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.Schema
import org.make.api.MakeUnitTest
import org.make.api.idea.IdeaEventWrapper
import org.make.api.proposal.PublishedProposalEvent.ProposalEventWrapper
import org.make.api.semantic.{PredictDuplicateEventWrapper, PredictionsEventWrapper}
import org.make.api.technical.crm.{MailJetEventWrapper, SendEmail}
import org.make.api.technical.tracking.TrackingEventWrapper
import org.make.api.userhistory.UserEventWrapper
import org.make.core.AvroSerializers

class AvroCompatibilityTest extends MakeUnitTest with AvroSerializers with StrictLogging {

  Feature("avro schemas") {

    Scenario("check avro compatibility for SendEmail") {
      val currentSchema: Schema = SendEmail.schemaFor.schema(DefaultFieldMapper)
      checkEntityType(currentSchema, "SendEmail")
    }
    Scenario("check avro compatibility for UserEventWrapper") {
      val currentSchema: Schema = UserEventWrapper.schemaFor.schema(DefaultFieldMapper)
      checkEntityType(currentSchema, "UserEventWrapper")
    }
    Scenario("check avro compatibility for ProposalEventWrapper") {
      val currentSchema: Schema = ProposalEventWrapper.schemaFor.schema(DefaultFieldMapper)
      checkEntityType(currentSchema, "ProposalEventWrapper")
    }
    Scenario("check avro compatibility for MailJetEventWrapper") {
      val currentSchema: Schema = MailJetEventWrapper.schemaFor.schema(DefaultFieldMapper)
      checkEntityType(currentSchema, "MailJetEventWrapper")
    }
    Scenario("check avro compatibility for TrackingEventWrapper") {
      val currentSchema: Schema = TrackingEventWrapper.schemaFor.schema(DefaultFieldMapper)
      checkEntityType(currentSchema, "TrackingEventWrapper")
    }
    Scenario("check avro compatibility for PredictionsEventWrapper") {
      val currentSchema: Schema = PredictionsEventWrapper.schemaFor.schema(DefaultFieldMapper)
      checkEntityType(currentSchema, "PredictionsEventWrapper")
    }
    Scenario("check avro compatibility for IdeaEventWrapper") {
      val currentSchema: Schema = IdeaEventWrapper.schemaFor.schema(DefaultFieldMapper)
      checkEntityType(currentSchema, "IdeaEventWrapper")
    }
    Scenario("check avro compatibility for PredictDuplicateEventWrapper") {
      val currentSchema: Schema = PredictDuplicateEventWrapper.schemaFor.schema(DefaultFieldMapper)
      checkEntityType(currentSchema, "PredictDuplicateEventWrapper")
    }
  }

  private def checkEntityType(currentSchema: Schema, name: String): Unit = {
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

    val tmp = Files.createTempFile(name, ".avro")
    val w = Files.newBufferedWriter(tmp.toAbsolutePath)
    w.write(currentSchema.toString(false))
    w.flush()
    // check that the current schema is in the list of schemas
    schemas.last.toString(true) should be(currentSchema.toString(true))
  }
}
