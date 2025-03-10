package cotoami.i18n

import slinky.core.facade.ReactElement

trait Text {
  val Coto: String
  val Cotonoma: String
  val Ito: String
  val Pin: String
  val Node: String
  val NodeRoot: String
  val Owner: String

  val Post: String
  val Save: String
  val Edit: String
  val Connect: String
  val WriteSubcoto: String
  val EditItos: String

  def ModalIncorporate_intro: ReactElement
  def ModalIncorporate_connect(operatingNodeId: String): ReactElement

  val ModalPromote_confirm: String

  val ModalEditIto_disconnect: String
  val ModalEditIto_confirmDisconnect: String
}

object Text {
  def inLang(lang: String): Text = text.en
}
