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
  val ReadOnly = "Read-only"

  val Post = "Post"
  val Save = "Save"
  val Edit = "Edit"
  val Delete = "Delete"
  val DeleteCotonoma = "Delete Cotonoma"
  val Connect = "Connect"
  val WriteSubcoto = "Write Sub-coto"
  val EditItos = "Edit Itos"

  val Node_notYetConnected = "Not yet connected"

  val ConfirmDeleteCoto = "Are you sure you want to delete the coto?"
  def ConfirmDeleteOthersCoto(someoneElse: ReactElement): ReactElement =
    span(className := "delete-others-coto")(
      "As an owner, you are about to delete a coto posted by:",
      someoneElse
    )
  val ConfirmDeleteCotonoma = "Are you sure you want to delete the cotonoma?"

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
  val ModalNodeProfile_generateOwnerPassword = "Generate Owner Password"
  val ModalNodeProfile_confirmGenerateOwnerPassword =
    """
    Are you sure you want to generate a new owner password? 
    Doing so will invalidate the current password.
    """
  val ModalNodeProfile_generateClientPassword = "Generate Client Password"
  val ModalNodeProfile_confirmGenerateClientPassword =
    """
    Are you sure you want to generate a new client password? 
    Doing so will invalidate the current password.
    """
  val ModalNodeProfile_clientLastLogin = "Last Login"
  val ModalNodeProfile_clientRemoteAddress = "Remote Address"
  val ModalNodeProfile_localServer = "As Server"
  val ModalNodeProfile_localServerUrl = "Server URL"
  val ModalNodeProfile_clientNodes = "Client Nodes"
  val ModalNodeProfile_anonymousRead = "Accept Anonymous Read"

  lazy val ModalIncorporate_intro: ReactElement = p()(
    """
    You can incorporate another database node into your database.
    Once incorporated, it will sync with the remote database 
    in real-time as long as you are online.
    """
  )

  def ModalIncorporate_connect(operatingNodeId: String): ReactElement = p()(
    """
    Trying to connect to a remote node with a URL and a password to authenticate your node.
    If you don't have a password for a node you want to incorporate,
    tell an admin of the node your node ID to create a new account.
    Your node ID is:
    """,
    code()(operatingNodeId)
  )

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
