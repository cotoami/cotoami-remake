package cotoami.models

import java.util.Locale
import java.text.NumberFormat
import cotoami.i18n.Text

case class I18n(locale: Locale = Locale.US) {
  val lang = locale.getLanguage()

  lazy val text: Text = Text.inLang(lang)
  lazy val numberFormat: NumberFormat = NumberFormat.getIntegerInstance

  def format(number: Double): String = numberFormat.format(number)
}

object I18n {
  def fromLanguageTag(tag: String): I18n =
    I18n(Locale.forLanguageTag(tag))
}
