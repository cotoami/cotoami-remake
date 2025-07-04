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

  val Id = "ID"
  val Name = "Name"

  val OK = "OK"
  val Cancel = "Cancel"
  val Post = "Post"
  val Insert = "Insert"
  val Save = "Save"
  val Edit = "Edit"
  val Delete = "Delete"

  val DeleteCotonoma = "Delete Cotonoma"
  val WriteSubcoto = "Write Sub-coto"
  val LoadItos = "Load Itos"
  val OpenMap = "Open Map"
  val CloseMap = "Close Map"
  val SwapPane = "Swap Pane"
  val LightMode = "Light Mode"
  val DarkMode = "Dark Mode"
  val MarkAllAsRead = "Mark All as Read"

  def Coto_inRemoteNode(nodeName: String) = s"In ${nodeName} (remote)"

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
  val ChildPrivileges_readOnly = "Read-only"

  val ConfirmDeleteCoto = "Are you sure you want to delete the coto?"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      "As an owner, you are about to delete a coto posted by:",
      someoneElse
    )
  val ConfirmDeleteCotonoma = "Are you sure you want to delete the cotonoma?"

  val NavNodes_allNodes = "All Nodes"
  val NavNodes_addNode = "Add Node"

  val NavCotonomas_current = "Current"
  val NavCotonomas_recent = "Recent"

  val SectionNodeTools_enableSync = "Enable Sync"
  val SectionNodeTools_disableSync = "Disable Sync"

  val EditorCoto_placeholder_coto = "Write your coto in Markdown"
  val EditorCoto_placeholder_summary = "Summary (optional)"
  val EditorCoto_placeholder_newCotonomaName = "New cotonoma name"
  val EditorCoto_placeholder_cotonomaName = "Cotonoma name"
  val EditorCoto_placeholder_cotonomaContent =
    "Write a cotonoma description in Markdown"
  val EditorCoto_inputImage = "Drop an image file here, or click to select one"
  val EditorCoto_date = "Date"
  val EditorCoto_location = "Location"
  val EditorCoto_help_selectLocation = "Click on a location on the map"
  def EditorCoto_cotonomaAlreadyExists(cotonomaName: String) =
    s"The cotonoma \"${cotonomaName}\" already exists in this node."

  val ModalConfirm_title = "Confirmation"

  val ModalWelcome_title = "Welcome to Cotoami"
  val ModalWelcome_recent = "Recent"
  val ModalWelcome_new = "New Database"
  val ModalWelcome_new_name = "Name"
  val ModalWelcome_new_baseFolder = "Base Folder"
  val ModalWelcome_new_selectBaseFolder = "Select a Base Folder"
  val ModalWelcome_new_folderName = "Folder Name to Create"
  val ModalWelcome_new_create = "Create"
  val ModalWelcome_open = "Open"
  val ModalWelcome_open_folder = "Database Folder"
  val ModalWelcome_open_selectFolder = "Select a Database Folder"
  val ModalWelcome_open_open = "Open"
  def ModalWelcome_update_message(newVersion: String) =
    span()(
      "A new version ",
      span(className := "version")(newVersion),
      " of Cotoami Desktop is available."
    )
  val ModalWelcome_update_updateNow = "Update Now"

  val ModalAppUpdate_title = "Updating Application"
  def ModalAppUpdate_message(
      newVersion: String,
      currentVersion: String
  ) = span()(
    "Downloading and installing version ",
    span(className := "version")(newVersion),
    " (current: ",
    span(className := "version")(currentVersion),
    ")"
  )
  val ModalAppUpdate_restart = "Restart App"

  val ModalInputOwnerPassword_title = "Owner Password Required"
  val ModalInputOwnerPassword_message =
    "You need to input the owner password to open this database."

  val ModalInputClientPassword_title = "Client Password Required"
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

  val ModalSelection_title = "Selected Cotos"
  val ModalSelection_clear = "Clear Selection"

  val ModalNewIto_title = "New Ito"
  val ModalNewIto_reverse = "Reverse"
  val ModalNewIto_clearSelection = "Clear selection on connect"
  val ModalNewIto_connect = "Connect"

  val ModalNodeProfile_title = "Node Profile"
  val ModalNodeProfile_selfNode = "You"
  val ModalNodeProfile_switched = "switched"

  val FieldImageMaxSize = "Image Resize Threshold (pixels)"
  val FieldImageMaxSize_placeholder = "No resizing"

  val FieldOwnerPassword = "Owner Password"

  val SelfNodeServer_title = "Node Server"
  val SelfNodeServer_url = "URL"
  val SelfNodeServer_clientNodes = "Client Nodes"
  val SelfNodeServer_anonymousRead = "Accept Anonymous Read"
  val SelfNodeServer_confirmEnableAnonymousRead =
    """
    Are you sure you want to allow anonymous read-only access
    (anyone who knows this node's URL can view your content)?
    """

  val AsServer_title = "As Server"
  val AsServer_url = "URL"
  val AsServer_connection = "Connection"

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

  val ModalSwitchNode_title = "Switch Node"
  val ModalSwitchNode_message =
    """
    You are about to switch the node to operate on as below.
    """
}
