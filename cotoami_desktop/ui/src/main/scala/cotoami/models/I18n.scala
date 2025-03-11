package cotoami.models

import java.text.NumberFormat
import cotoami.i18n.Text

case class I18n(lang: String = "en") {
  lazy val text: Text = Text.inLang(lang)
  lazy val numberFormat: NumberFormat = NumberFormat.getIntegerInstance

  def format(number: Double): String = numberFormat.format(number)
}
