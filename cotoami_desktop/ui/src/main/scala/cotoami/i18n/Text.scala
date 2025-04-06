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
  val ReadOnly: String

  val Post: String
  val Save: String
  val Edit: String
  val Delete: String
  val DeleteCotonoma: String
  val Connect: String
  val WriteSubcoto: String
  val EditItos: String

  val Node_notYetConnected: String

  val ConfirmDeleteCoto: String
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement
  val ConfirmDeleteCotonoma: String

  val ModalInputOwnerPassword_title: String
  val ModalInputOwnerPassword_message: String

  val ModalNewOwnerPassword_title: String
  val ModalNewOwnerPassword_message: String

  val ModalNodeProfile_title: String
  val ModalNodeProfile_generateOwnerPassword: String
  val ModalNodeProfile_confirmGenerateOwnerPassword: String
  val ModalNodeProfile_clientLastLogin: String
  val ModalNodeProfile_clientRemoteAddress: String
  val ModalNodeProfile_localServer: String
  val ModalNodeProfile_localServerUrl: String
  val ModalNodeProfile_clientNodes: String
  val ModalNodeProfile_anonymousRead: String

  def ModalIncorporate_intro: ReactElement
  def ModalIncorporate_connect(operatingNodeId: String): ReactElement

  val ModalPromote_confirm: String

  val ModalEditIto_disconnect: String
  val ModalEditIto_confirmDisconnect: String

  val ModalClients_title: String
  val ModalClients_add: String

  val ModalNewClient_title: String
  val ModalNewClient_registered: String
}

object Text {
  def inLang(lang: String): Text = text.en
}
