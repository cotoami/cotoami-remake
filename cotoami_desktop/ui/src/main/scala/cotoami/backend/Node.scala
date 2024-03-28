package cotoami.backend

import scala.scalajs.js
import java.time.LocalDateTime
import cotoami.{Id, Validation}

case class Nodes(
    map: Map[Id[Node], Node] = Map.empty,
    localId: Option[Id[Node]] = None,
    operatingId: Option[Id[Node]] = None,
    parentIds: Seq[Id[Node]] = Seq.empty,
    selectedId: Option[Id[Node]] = None
) {
  def get(id: Id[Node]): Option[Node] = this.map.get(id)

  def contains(id: Id[Node]): Boolean = this.map.contains(id)

  def local: Option[Node] = this.localId.flatMap(this.get(_))

  def operating: Option[Node] = this.operatingId.flatMap(this.get(_))

  def parents: Seq[Node] = this.parentIds.map(this.get(_)).flatten

  def select(id: Id[Node]): Nodes =
    if (this.contains(id))
      this.copy(selectedId = Some(id))
    else
      this

  def deselect(): Nodes = this.copy(selectedId = None)

  def isSelecting(id: Id[Node]): Boolean =
    this.selectedId.map(_ == id).getOrElse(false)

  def selected: Option[Node] = this.selectedId.flatMap(this.get(_))

  def current: Option[Node] = this.selected.orElse(this.operating)
}

object Nodes {
  def apply(info: DatabaseInfo) =
    new Nodes(
      map = info.nodes,
      localId = Some(info.localNodeId),
      operatingId = Some(info.localNodeId),
      parentIds = info.parentNodeIds
    )
}

case class Node(json: NodeJson) {
  def id: Id[Node] = Id(this.json.uuid)
  def icon: String = this.json.icon
  def name: String = this.json.name
  def rootCotonomaId: Id[Cotonoma] = Id(this.json.root_cotonoma_id)
  def version: Int = this.json.version
  lazy val createdAt: LocalDateTime = parseJsonDateTime(this.json.created_at)

  def debug: String =
    s"id: ${this.id}, name: ${this.name}, version: ${this.version}"
}

object Node {
  def validateName(name: String): Seq[Validation.Error] =
    Cotonoma.validateName(name)
}

@js.native
trait NodeJson extends js.Object {
  val uuid: String = js.native
  val icon: String = js.native // Base64 encoded image binary
  val name: String = js.native
  val root_cotonoma_id: String = js.native
  val version: Int = js.native
  val created_at: String = js.native
}
