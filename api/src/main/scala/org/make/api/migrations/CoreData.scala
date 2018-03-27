package org.make.api.migrations

import org.make.api.migrations.InsertFixtureData.FixtureDataLine
import org.make.core.RequestContext
import org.make.core.reference.{LabelId, TagId, ThemeId}

object CoreData extends InsertFixtureData {

  override val dataResource: String = "fixtures/proposals.csv"
  var localRequestContext: RequestContext = RequestContext.empty
  override def requestContext: RequestContext = localRequestContext

  override def extractDataLine(line: String): Option[FixtureDataLine] = {
    line.drop(1).dropRight(1).split("""";"""") match {
      case Array(email, content, theme, tags, labels, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = Some(ThemeId(theme)),
            operation = None,
            tags = tags.split('|').map(TagId.apply).toSeq,
            labels = labels.split('|').map(LabelId.apply).toSeq,
            country = country,
            language = language
          )
        )
      case Array(email, content, theme, tags, country, language) =>
        Some(
          FixtureDataLine(
            email = email,
            content = content,
            theme = Some(ThemeId(theme)),
            operation = None,
            tags = tags.split('|').map(TagId.apply).toSeq,
            labels = Seq.empty,
            country = country,
            language = language
          )
        )
      case _ => None
    }
  }

  override val runInProduction: Boolean = false
}
