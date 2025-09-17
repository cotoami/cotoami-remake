package cotoami.i18n.text

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.i18n.Text

object en extends Text {
  val Coto = "Coto"
  val Cotonoma = "Cotonoma"
  val Ito = "Ito"
  val Pin = "Pin"
  val Node = "Node"
  val Owner = "Owner"
  val Server = "Server"
  val Client = "Client"

  val Id = "ID"
  val Name = "Name"
  val Password = "Password"

  val OK = "OK"
  val Cancel = "Cancel"
  val Post = "Post"
  val Insert = "Insert"
  val Save = "Save"
  val Edit = "Edit"
  val Preview = "Preview"
  val Delete = "Delete"
  val Repost = "Repost"
  val Promote = "Promote"
  val Traverse = "Traverse"
  val Select = "Select"
  val Deselect = "Deselect"
  val Register = "Register"
  val Back = "Back"

  val DeleteCotonoma = "Delete Cotonoma"
  val WriteSubcoto = "Write Sub-coto"
  val OpenMap = "Open Map"
  val CloseMap = "Close Map"
  val SwapPane = "Swap Pane"
  val LightMode = "Light Mode"
  val DarkMode = "Dark Mode"
  val MarkAllAsRead = "Mark All as Read"
  val PostTo = "Post to"

  def Coto_inRemoteNode(nodeName: String) = s"In ${nodeName} (remote)"

  val Node_id = "Node ID"
  val Node_root = "Node Root"
  val Node_notYetConnected = "Not yet connected"
  val Node_settings = "Node Settings"

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

  val PaneStock_map_dockLeft = "Dock Left"
  val PaneStock_map_dockTop = "Dock Top"

  val SectionPins_layout_document = "Document"
  val SectionPins_layout_columns = "Columns"
  val SectionPins_layout_masonry = "Masonry"

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

  val ModalSubcoto_title = "New Sub-coto"

  val ModalNodeProfile_title = "Node Profile"
  val ModalNodeProfile_selfNode = "You"
  val ModalNodeProfile_switched = "switched"
  val ModalNodeProfile_description = "Description"

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
  val SelfNodeServer_anonymousConnections = "Active connections"

  val AsServer_title = "As Server"
  val AsServer_url = "URL"
  val AsServer_connection = "Connection"

  val AsClient_title = "As Client"
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
  val ModalIncorporate_nodeUrl = "Node URL"
  val ModalIncorporate_incorporate = "Incorporate"

  val ModalPromote_title = "Promote to Cotonoma"
  val ModalPromote_confirm =
    """
    Are you sure you want to promote this coto to a cotonoma?
    This action cannot be undone.
    """

  val ModalEditIto_disconnect = "Disconnect"
  val ModalEditIto_confirmDisconnect =
    "Are you sure you want to delete this ito?"

  val ModalRepost_title = "Repost"
  val ModalRepost_repostTo = "Repost to"
  val ModalRepost_typeCotonomaName = "Type cotonoma name"
  val ModalRepost_newCotonoma = "New cotonoma"
  val ModalRepost_root = "root"
  val ModalRepost_alreadyPostedIn = "Already posted in"

  val ModalClients_title = "Client Nodes"
  val ModalClients_add = "Add Client"
  val ModalClients_connecting = "connecting"
  val ModalClients_nodes = "nodes"
  val ModalClients_noClients = "No client nodes registered yet."
  val ModalClients_column_name = "Name"
  val ModalClients_column_lastLogin = "Last Login"
  val ModalClients_column_status = "Status"
  val ModalClients_column_enabled = "Enabled"

  val ModalNewClient_title = "New Client"
  val ModalNewClient_registered =
    """
    The child node below has been registered.
    Send the generated password to the node's owner in a secure way.
    """

  val ModalSwitchNode_title = "Switch Node"
  val ModalSwitchNode_switch = "Switch"
  val ModalSwitchNode_message =
    """
    You are about to switch the node to operate on as below.
    """

  val ModalNodeIcon_title = "Change Node Icon"
  val ModalNodeIcon_inputImage = Fragment(
    "Drag and drop an image file here,",
    br(),
    "or click to select one"
  )
}
