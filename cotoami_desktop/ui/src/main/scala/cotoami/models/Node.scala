package cotoami.models

import org.scalajs.dom
import java.time.Instant

import fui.{Browser, Cmd}
import cotoami.utils.Validation
import cotoami.backend.Cotonoma

case class Node(
    id: Id[Node],
    name: String,
    rootCotonomaId: Option[Id[Cotonoma]],
    version: Int,
    createdAt: Instant
)(icon: String)
    extends Entity[Node] {

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

  def debug: String =
    s"id: ${this.id}, name: ${this.name}, version: ${this.version}"
}

object Node {
  val IconMimeType = "image/png"

  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)

  import cotoami.backend.{parseJsonDateTime, ErrorJson, NodeJson, Nullable}

  def apply(json: NodeJson): Node =
    Node(
      Id(json.uuid),
      json.name,
      Nullable.toOption(json.root_cotonoma_id).map(Id(_)),
      json.version,
      parseJsonDateTime(json.created_at)
    )(json.icon)

  def setLocalNodeIcon(icon: String): Cmd[Either[ErrorJson, Node]] =
    NodeJson.setLocalNodeIcon(icon).map(_.map(Node(_)))
}
