package cotoami.models

import cotoami.i18n.Help

case class I18n(lang: String = "en") {
  lazy val help: Help = Help.inLang(this.lang)
}
