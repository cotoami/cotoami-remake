package cotoami.i18n.text

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.i18n.Text

object en extends Text {
  val Coto = "Coto"
  val Cotonoma = "Cotonoma"
  val Ito = "Ito"
  val Pin = "Pin"
  val Node = "Node"
  val NodeRoot = "Node Root"
  val Owner = "Owner"
  val Server = "Server"
  val Client = "Client"
  val ReadOnly = "Read-only"

  val Id = "ID"
  val Name = "Name"

  val Cancel = "Cancel"
  val Post = "Post"
  val Save = "Save"
  val Edit = "Edit"
  val Delete = "Delete"

  val DeleteCotonoma = "Delete Cotonoma"
  val Connect = "Connect"
  val WriteSubcoto = "Write Sub-coto"
  val EditItos = "Edit Itos"

  val Node_notYetConnected = "Not yet connected"

  val Ito_description_placeholder = "Ito description (optional)"
  val Ito_editPin = "Edit Pin"
  val Ito_editIto = "Edit Ito"

  val Owner_resetPassword = "Reset Owner Password"
  val Owner_confirmResetPassword =
    """
    Are you sure you want to generate a new owner password? 
    Doing so will invalidate the current password.
    """

  val Connection_disabled = "not synced"
  val Connection_connecting = "connecting"
  val Connection_initFailed = "initialization failed"
  val Connection_authenticationFailed = "authentication failed"
  val Connection_sessionExpired = "session expired"
  val Connection_disconnected = "disconnected"
  val Connection_connected = "connected"

  val ChildPrivileges = "Privileges"
  val ChildPrivileges_asOwner = "Owner (full privileges)"
  val ChildPrivileges_canPostCotos = "Post cotos"
  val ChildPrivileges_canEditItos = "Edit itos"
  val ChildPrivileges_canPostCotonomas = "Post cotonomas"

  val ConfirmDeleteCoto = "Are you sure you want to delete the coto?"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      "As an owner, you are about to delete a coto posted by:",
      someoneElse
    )
  val ConfirmDeleteCotonoma = "Are you sure you want to delete the cotonoma?"

  val NavNodes_allNodes = "All Nodes"
  val NavNodes_addNode = "Add Node"

  val SectionNodeTools_enableSync = "Enable Sync"
  val SectionNodeTools_disableSync = "Disable Sync"

  val ModalInputOwnerPassword_title = "Input Owner Password"
  val ModalInputOwnerPassword_message =
    "You need to input the owner password to open this database."

  val ModalInputClientPassword_title = "Input Client Password"
  val ModalInputClientPassword_message =
    """
    Failed to log in to the server node with the configured password.
    To reconnect to this node, please enter a new password.
    """

  val ModalNewOwnerPassword_title = "New Owner Password"
  val ModalNewOwnerPassword_message =
    """
    Store this password in a safe place. 
    You will need it to open this database on another computer. 
    You can generate a new password from the node profile at any time.
    """

  val ModalNewClientPassword_title = "New Client Password"
  val ModalNewClientPassword_message =
    """
    Send this password to the node owner using a secure method.
    """

  val ModalNodeProfile_title = "Node Profile"
  val ModalNodeProfile_selfNode = "You"
  val ModalNodeProfile_switched = "switched"
  val ModalNodeProfile_ownerPassword = "Owner Password"

  val AsServer_title = "As Server"
  val AsServer_url = "URL"
  val AsServer_connection = "Connection"
  val AsServer_clientNodes = "Client Nodes"
  val AsServer_anonymousRead = "Accept Anonymous Read"
  val AsServer_confirmEnableAnonymousRead =
    """
    Are you sure you want to allow anonymous read-only access
    (anyone who knows this node's URL can view your content)?
    """

  val AsClient_title = "As Client"
  val AsClient_password = "Password"
  val AsClient_resetPassword = "Reset Client Password"
  val AsClient_confirmResetPassword =
    """
    Are you sure you want to generate a new client password? 
    Doing so will invalidate the current password.
    """
  val AsClient_lastLogin = "Last Login"
  val AsClient_remoteAddress = "Remote Address"

  val AsChild_title = "As Child"

  val ModalIncorporate_title = "Incorporate Remote Node"

  val ModalPromote_confirm =
    """
    Are you sure you want to promote this coto to a cotonoma?
    This action cannot be undone.
    """

  val ModalEditIto_disconnect = "Disconnect"
  val ModalEditIto_confirmDisconnect =
    "Are you sure you want to delete this ito?"

  val ModalClients_title = "Client Nodes"
  val ModalClients_add = "Add Client"

  val ModalNewClient_title = "New Client"
  val ModalNewClient_registered =
    """
    The child node below has been registered.
    Send the generated password to the node's owner in a secure way.
    """
}
