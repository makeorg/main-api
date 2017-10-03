package org.make.api.sequence

// ToDo: handle translations
final case class CreateSequenceRequest(title: String, themeIds: Seq[String], tagIds: Seq[String])
final case class UpdateSequenceRequest(title: String)
