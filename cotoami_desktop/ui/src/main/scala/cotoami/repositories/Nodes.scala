package cotoami.repositories

import com.softwaremill.quicklens._

import cotoami.backend._

case class Nodes(
    map: Map[Id[Node], Node] = Map.empty,
    localId: Option[Id[Node]] = None,
    operatingId: Option[Id[Node]] = None,
    selectedId: Option[Id[Node]] = None,

    // roles
    parentIds: Seq[Id[Node]] = Seq.empty,
    servers: Seq[Server] = Seq.empty
) {
  def get(id: Id[Node]): Option[Node] = this.map.get(id)

  def contains(id: Id[Node]): Boolean = this.map.contains(id)

  def add(node: Node): Nodes =
    this.modify(_.map).using(_ + (node.id -> node))

  def local: Option[Node] = this.localId.flatMap(this.get)

  def operating: Option[Node] = this.operatingId.flatMap(this.get)

  def parents: Seq[Node] = this.parentIds.map(this.get).flatten

  def select(id: Id[Node]): Nodes =
    if (this.contains(id))
      this.copy(selectedId = Some(id))
    else
      this

  def deselect(): Nodes = this.copy(selectedId = None)

  def isSelecting(id: Id[Node]): Boolean =
    this.selectedId.map(_ == id).getOrElse(false)

  def selected: Option[Node] = this.selectedId.flatMap(this.get)

  def current: Option[Node] = this.selected.orElse(this.operating)
}

object Nodes {
  def apply(info: DatabaseInfo) =
    new Nodes(
      map = info.nodes,
      localId = Some(info.localNodeId),
      operatingId = Some(info.localNodeId),
      parentIds = info.parentNodeIds.toSeq,
      servers = info.servers.toSeq
    )
}
