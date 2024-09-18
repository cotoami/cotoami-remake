package cotoami.models

import org.scalajs.dom
import java.time.Instant

import fui.{Browser, Cmd}
import cotoami.utils.Validation

case class Node(
    id: Id[Node],
    name: String,
    rootCotonomaId: Option[Id[Cotonoma]],
    version: Int,
    createdAtUtcIso: String
)(
    // Make the raw icon data private to force a client to use `iconUrl`
    // and remove it from `equals` since the `version` should be enough
    // to decide equality of nodes.
    icon: String
) extends Entity[Node] {
  override def equals(that: Any): Boolean =
    that match {
      case that: Node => version.equals(that.version)
      case _          => false
    }

  def setIcon(icon: String): Node = {
    dom.URL.revokeObjectURL(this.iconUrl) // revoke the old image URL
    this.copy(version = this.version + 1)(icon = icon)
  }

  lazy val iconUrl: String = {
    // `URL.revokeObjectURL()` wouldn't be needed because once a node object
    // has been loaded it will be retained until the window is closed
    // (unless the icon is changed by `setIcon` which revokes the URL).
    val blob = Browser.decodeBase64(this.icon, Node.IconMimeType)
    dom.URL.createObjectURL(blob)
  }

  lazy val createdAt: Instant = parseUtcIso(this.createdAtUtcIso)

  def debug: String =
    s"id: ${this.id}, name: ${this.name}, version: ${this.version}"
}

object Node {
  val IconMimeType = "image/png"

  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)

  import cotoami.backend.{ErrorJson, NodeJson, Nullable}

  def apply(json: NodeJson): Node =
    Node(
      Id(json.uuid),
      json.name,
      Nullable.toOption(json.root_cotonoma_id).map(Id(_)),
      json.version,
      json.created_at
    )(json.icon)

  def setLocalNodeIcon(icon: String): Cmd[Either[ErrorJson, Node]] =
    NodeJson.setLocalNodeIcon(icon).map(_.map(Node(_)))
}
