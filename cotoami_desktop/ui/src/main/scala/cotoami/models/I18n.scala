package cotoami.models

import cotoami.i18n.Text

case class I18n(lang: String = "en") {
  lazy val text: Text = Text.inLang(lang)
}
