package org.make.api

import pl.allegro.tech.embeddedelasticsearch.{EmbeddedElastic, PopularProperties}

object EmbeddedApplication {

  lazy val embeddedElastic: EmbeddedElastic = EmbeddedElastic.builder()
    .withElasticVersion("5.0.0")
    .withSetting(PopularProperties.TRANSPORT_TCP_PORT, 9300)
    .withSetting(PopularProperties.HTTP_PORT, 9200)
    .withSetting(PopularProperties.CLUSTER_NAME, "make-search")
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
