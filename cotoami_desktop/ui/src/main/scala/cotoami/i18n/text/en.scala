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

  val ModalNewPassword_title = "New Owner Password"
  val ModalNewPassword_message =
    """
    Store this password in a safe place. 
    You will need it to migrate this database to another computer and open it
    (you can obtain a new password at any time via the node profile).
    """

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
    "Are you sure you want to promote this coto to a cotonoma? It is an irreversible change."

  val ModalEditIto_disconnect = "Disconnect"
  val ModalEditIto_confirmDisconnect =
    "Are you sure you want to delete this ito?"

  val ModalClients_title = "Client Nodes"
  val ModalClients_add = "Add Client"
}
