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
      case that: Node => (this.id, this.version) == (that.id, that.version)
      case _          => false
    }

  def setIcon(icon: String): Node = {
    revokeIconUrl()
    this.copy(
      icon = Node.decodeBase64Icon(icon),
      version = this.version + 1
    )
  }

  lazy val iconUrl: String = dom.URL.createObjectURL(this.icon)

  def revokeIconUrl(): Unit = dom.URL.revokeObjectURL(this.iconUrl)

  lazy val createdAt: Instant = parseUtcIso(this.createdAtUtcIso)

  def debug: String =
    s"id: ${this.id}, name: ${this.name}, version: ${this.version}"
}

object Node {
  val IconMimeType = "image/png"

  def decodeBase64Icon(icon: String): dom.Blob =
    Browser.decodeBase64(icon, IconMimeType)

  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)
}
