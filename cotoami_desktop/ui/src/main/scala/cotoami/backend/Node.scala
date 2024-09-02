package cotoami.backend

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.document.createElement
import java.time.Instant

import fui.{Browser, Cmd}
import cotoami.utils.Validation

case class Node(json: NodeJson) {
  def id: Id[Node] = Id(this.json.uuid)

  lazy val iconUrl: String = {
    // `URL.revokeObjectURL()` wouldn't be needed because once a node object
    // has been loaded it will be retained until the window is closed.
    val blob = Browser.decodeBase64(this.icon, Node.IconMimeType)
    dom.URL.createObjectURL(blob)
  }
  private def icon: String = this.json.icon

  def name: String = this.json.name
  def rootCotonomaId: Option[Id[Cotonoma]] =
    Nullable.toOption(this.json.root_cotonoma_id).map(Id(_))
  def version: Int = this.json.version
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)

  def newCotoMarkerHtml: dom.Element = {
    val root = createElement("div").asInstanceOf[dom.HTMLDivElement]
    root.className = "geomap-marker coto-marker"
    val icon = createElement("img").asInstanceOf[dom.HTMLImageElement]
    icon.src = this.iconUrl
    root.append(icon)
    root
  }

  def newCotonomaMarkerHtml: dom.Element = {
    val root = createElement("div").asInstanceOf[dom.HTMLDivElement]
    root.className = "geomap-marker cotonoma-marker"
    val icon = createElement("img").asInstanceOf[dom.HTMLImageElement]
    icon.src = this.iconUrl
    root.append(icon)
    root
  }

  def debug: String =
    s"id: ${this.id}, name: ${this.name}, version: ${this.version}"
}

object Node {
  val IconMimeType = "image/png"

  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)

  def setLocalNodeIcon(icon: String): Cmd[Either[ErrorJson, Node]] =
    NodeJson.setLocalNodeIcon(icon).map(_.map(Node(_)))
}

@js.native
trait NodeJson extends js.Object {
  val uuid: String = js.native
  val icon: String = js.native // Base64 encoded image binary
  val name: String = js.native
  val root_cotonoma_id: Nullable[String] = js.native
  val version: Int = js.native
  val created_at: String = js.native
}

object NodeJson {
  def setLocalNodeIcon(icon: String): Cmd[Either[ErrorJson, NodeJson]] =
    Commands.send(Commands.SetLocalNodeIcon(icon))
}

sealed trait DatabaseRole
case class Parent(info: ParentNode) extends DatabaseRole
case class Child(info: ChildNode) extends DatabaseRole

object DatabaseRole {
  def apply(json: DatabaseRoleJson): DatabaseRole = {
    for (parent <- json.Parent.toOption) {
      return Parent(ParentNode(parent))
    }
    for (child <- json.Child.toOption) {
      return Child(ChildNode(child))
    }
    return null // this should be unreachable
  }
}

@js.native
trait DatabaseRoleJson extends js.Object {
  val Parent: js.UndefOr[ParentNodeJson] = js.native
  val Child: js.UndefOr[ChildNodeJson] = js.native
}

case class NodeDetails(json: NodeDetailsJson) {
  def node: Node = Node(this.json.node)
  def root: Option[(Cotonoma, Coto)] =
    Nullable.toOption(this.json.root).map(pair =>
      (Cotonoma(pair._1), Coto(pair._2))
    )
}

object NodeDetails {
  def fetch(id: Id[Node]): Cmd[Either[ErrorJson, NodeDetails]] =
    NodeDetailsJson.fetch(id).map(_.map(NodeDetails(_)))
}

@js.native
trait NodeDetailsJson extends js.Object {
  val node: NodeJson = js.native
  val root: Nullable[js.Tuple2[CotonomaJson, CotoJson]] = js.native
}

object NodeDetailsJson {
  def fetch(id: Id[Node]): Cmd[Either[ErrorJson, NodeDetailsJson]] =
    Commands.send(Commands.NodeDetails(id))
}
