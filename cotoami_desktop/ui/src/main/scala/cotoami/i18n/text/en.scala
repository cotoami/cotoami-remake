package cotoami.i18n.text

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.i18n.Text

object en extends Text {
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
}
