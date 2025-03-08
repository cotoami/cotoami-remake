package cotoami.i18n

import slinky.core.facade.ReactElement

trait Text {
  def ModalIncorporate_intro: ReactElement
  def ModalIncorporate_connect(operatingNodeId: String): ReactElement

  val ModalPromote_confirm: String
}

object Text {
  def inLang(lang: String): Text = text.en
}
