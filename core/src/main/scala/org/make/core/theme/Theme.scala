package org.make.core.theme

import org.make.core.{MakeSerializable, StringValue}

case class Theme(themeId: ThemeId, label: String) extends MakeSerializable

case class ThemeId(value: String) extends StringValue
