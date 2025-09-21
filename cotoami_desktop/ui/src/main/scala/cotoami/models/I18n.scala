package cotoami.models

import java.util.Locale
import java.text.NumberFormat
import cotoami.i18n.Text

case class I18n(locale: Locale = Locale.US) {
  val lang = locale.getLanguage()
  val script = locale.getScript()
  val langWithScript = if (script.isBlank()) lang else s"${lang}-${script}"

  lazy val text: Text = Text.inLang(lang, script)
  lazy val numberFormat: NumberFormat = NumberFormat.getIntegerInstance

  def format(number: Double): String = numberFormat.format(number)
}

object I18n {
  def fromBcp47(tag: String): I18n =
    I18n(Locale.forLanguageTag(tag))
}
