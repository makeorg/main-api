package org.make.api

import com.typesafe.scalalogging.StrictLogging
import pl.allegro.tech.embeddedelasticsearch.{EmbeddedElastic, IndexSettings, PopularProperties}

import scala.io.Source

object EmbeddedApplication extends StrictLogging {

  val schema: String = Source.fromResource("proposition-mapping.json").mkString

  logger.debug("Applying schema for proposition: {}", schema)

  lazy val embeddedElastic: EmbeddedElastic = EmbeddedElastic.builder()
    .withElasticVersion("5.4.0")
    .withSetting(PopularProperties.TRANSPORT_TCP_PORT, 9300)
    .withSetting(PopularProperties.HTTP_PORT, 9200)
    .withSetting(PopularProperties.CLUSTER_NAME, "make-search")
    .withIndex("propositions",
      IndexSettings.builder()
        .withType("proposition", schema)
        .build()
    )
    //    .withIndex("cars",
    //      IndexSettings.builder()
    //        .withType("car", getSystemResourceAsStream("car-mapping.json"))
    //        .build()
    //    )
    //    .withIndex("books",
    //      IndexSettings.builder()
    //        .withType(PAPER_BOOK_INDEX_TYPE, getSystemResourceAsStream("paper-book-mapping.json"))
    //        .withType("audio_book", getSystemResourceAsStream("audio-book-mapping.json"))
    //        .withSettings(getSystemResourceAsStream("elastic-settings.json"))
    //        .build()
    //    )
    .build()

}
