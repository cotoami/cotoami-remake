package cotoami.models

import org.scalajs.dom
import java.time.Instant

import fui.Browser
import cotoami.utils.Validation

case class Node(
    id: Id[Node],
    name: String,
    rootCotonomaId: Option[Id[Cotonoma]],
    version: Int,
    createdAtUtcIso: String
)(
    // Make the raw icon data private to force a client to use `iconUrl`
    // and remove it from `equals` since the `id` and `version` should be
    // enough to decide equality of nodes.
    icon: String
) extends Entity[Node] {
  override def equals(that: Any): Boolean =
    that match {
      case that: Node => (this.id, this.version) == (that.id, that.version)
      case _          => false
    }

  def setIcon(icon: String): Node = {
    revokeIconUrl()
    this.copy(version = this.version + 1)(icon = icon)
  }

  lazy val iconUrl: String = {
    val blob = Browser.decodeBase64(this.icon, Node.IconMimeType)
    dom.URL.createObjectURL(blob)
  }

  def revokeIconUrl(): Unit = dom.URL.revokeObjectURL(this.iconUrl)

  lazy val createdAt: Instant = parseUtcIso(this.createdAtUtcIso)

  def debug: String =
    s"id: ${this.id}, name: ${this.name}, version: ${this.version}"
}

object Node {
  val IconMimeType = "image/png"

  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)
}
