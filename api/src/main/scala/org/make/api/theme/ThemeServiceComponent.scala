package org.make.api.theme

import org.make.api.technical.ShortenedNames
import org.make.core.reference._

import scala.concurrent.Future

trait ThemeServiceComponent {
  def themeService: ThemeService
}

trait ThemeService extends ShortenedNames {
  def findAll(): Future[Seq[Theme]]
}

trait DefaultThemeServiceComponent extends ThemeServiceComponent with ShortenedNames {
  this: PersistentThemeServiceComponent =>

  val themeService = new ThemeService {

    override def findAll(): Future[Seq[Theme]] = {
      persistentThemeService.findAll()
    }
  }
}
