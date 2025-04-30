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
  val Server: String
  val Client: String

  val Id: String
  val Name: String

  val Cancel: String
  val Post: String
  val Save: String
  val Edit: String
  val Delete: String

  val DeleteCotonoma: String
  val WriteSubcoto: String
  val LoadItos: String

  def Coto_inRemoteNode(nodeName: String): String

  val Node_notYetConnected: String

  val Ito_description_placeholder: String
  val Ito_editPin: String
  val Ito_editIto: String

  val Owner_resetPassword: String
  val Owner_confirmResetPassword: String

  val Connection_disabled: String
  val Connection_connecting: String
  val Connection_initFailed: String
  val Connection_authenticationFailed: String
  val Connection_sessionExpired: String
  val Connection_disconnected: String
  val Connection_connected: String

  val ChildPrivileges: String
  val ChildPrivileges_asOwner: String
  val ChildPrivileges_canPostCotos: String
  val ChildPrivileges_canEditItos: String
  val ChildPrivileges_canPostCotonomas: String
  val ChildPrivileges_readOnly: String

  val ConfirmDeleteCoto: String
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement
  val ConfirmDeleteCotonoma: String

  val NavNodes_allNodes: String
  val NavNodes_addNode: String

  val NavCotonomas_current: String
  val NavCotonomas_recent: String

  val SectionNodeTools_enableSync: String
  val SectionNodeTools_disableSync: String

  val EditorCoto_placeholder_coto: String
  val EditorCoto_placeholder_summary: String
  val EditorCoto_placeholder_newCotonomaName: String
  val EditorCoto_placeholder_cotonomaName: String
  val EditorCoto_placeholder_cotonomaContent: String
  val EditorCoto_inputImage: String
  val EditorCoto_date: String
  val EditorCoto_location: String
  def EditorCoto_cotonomaAlreadyExists(cotonomaName: String): String

  val ModalInputOwnerPassword_title: String
  val ModalInputOwnerPassword_message: String

  val ModalInputClientPassword_title: String
  val ModalInputClientPassword_message: String

  val ModalNewOwnerPassword_title: String
  val ModalNewOwnerPassword_message: String

  val ModalNewClientPassword_title: String
  val ModalNewClientPassword_message: String

  val ModalSelection_title: String
  val ModalSelection_clear: String

  val ModalNewIto_title: String
  val ModalNewIto_reverse: String
  val ModalNewIto_clearSelection: String
  val ModalNewIto_connect: String

  val ModalNodeProfile_title: String
  val ModalNodeProfile_selfNode: String
  val ModalNodeProfile_switched: String

  val FieldImageMaxSize: String
  val FieldImageMaxSize_placeholder: String

  val FieldOwnerPassword: String

  val AsServer_title: String
  val AsServer_url: String
  val AsServer_connection: String
  val AsServer_clientNodes: String
  val AsServer_anonymousRead: String
  val AsServer_confirmEnableAnonymousRead: String

  val AsClient_title: String
  val AsClient_password: String
  val AsClient_resetPassword: String
  val AsClient_confirmResetPassword: String
  val AsClient_lastLogin: String
  val AsClient_remoteAddress: String

  val AsChild_title: String

  val ModalIncorporate_title: String

  val ModalPromote_confirm: String

  val ModalEditIto_disconnect: String
  val ModalEditIto_confirmDisconnect: String

  val ModalClients_title: String
  val ModalClients_add: String

  val ModalNewClient_title: String
  val ModalNewClient_registered: String

  val ModalSwitchNode_title: String
  val ModalSwitchNode_message: String
}

object Text {
  def inLang(lang: String): Text = text.en
}
