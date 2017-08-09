package org.make.core.tag

import org.make.core.{MakeSerializable, StringValue}

case class Tag(tagId: TagId, label: String) extends MakeSerializable

case class TagId(value: String) extends StringValue
