package cotoami.models

import org.scalajs.dom
import java.time.Instant

import fui.Browser
import cotoami.utils.Validation

case class Node(
    id: Id[Node],
    icon: dom.Blob,
    name: String,
    rootCotonomaId: Option[Id[Cotonoma]],
    version: Int,
    createdAtUtcIso: String
) extends Entity[Node] {

  // If two node objects have the same ID and version,
  // they can be regarded as the same node.
  override def equals(that: Any): Boolean =
    that match {
      case that: Node => (id, version) == (that.id, that.version)
      case _          => false
    }

  def hasIcon: Boolean = icon.size != 0

  lazy val iconUrl: String = dom.URL.createObjectURL(icon)

  def setIcon(icon: String): Node = {
    revokeIconUrl()
    copy(
      icon = Node.decodeBase64Icon(icon),
      version = version + 1
    )
  }

  def rename(name: String): Node = {
    revokeIconUrl()
    copy(
      name = name,
      version = version + 1
    )
  }

  def revokeIconUrl(): Unit = dom.URL.revokeObjectURL(iconUrl)

  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
}

object Node {
  final val IconName = "database"
  final val SwitchIconName = "switch_account"

  final val IconMimeType = "image/png"

  def decodeBase64Icon(icon: String): dom.Blob =
    Browser.decodeBase64(icon, IconMimeType)

  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)

  def localNodeCannotBeRemoteError = Validation.Error(
    "local-node-cannot-be-remote",
    "This is the local node."
  )
}
