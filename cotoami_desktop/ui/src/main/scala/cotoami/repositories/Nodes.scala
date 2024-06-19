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
    serverMap: Map[Id[Node], Server] = Map.empty
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

  def prependParentId(id: Id[Node]): Nodes =
    if (this.parentIds.contains(id)) this
    else
      this.modify(_.parentIds).using(id +: _)

  def getServer(id: Id[Node]): Option[Server] = this.serverMap.get(id)

  def addServer(server: Server): Nodes = {
    val nodes =
      this.modify(_.serverMap).using(_ + (server.server.nodeId -> server))
    server.databaseRole.map {
      case Parent(parent) => nodes.prependParentId(parent.nodeId)
      case Child(child)   => nodes
    }.getOrElse(nodes)
  }

  def addServers(servers: Iterable[Server]): Nodes =
    servers.foldLeft(this)(_ addServer _)

  def setServerState(id: Id[Node], notConnected: Option[NotConnected]): Nodes =
    this.modify(_.serverMap.index(id).notConnected).setTo(notConnected)

  def isEditable(id: Id[Node]): Boolean = {
    if (Some(id) == localId) return true

    if (this.parentIds.contains(id)) {
      this.getServer(id).map(_.notConnected.isEmpty).getOrElse(false)
    } else {
      false
    }
  }
}

object Nodes {
  def apply(info: DatabaseInfo) =
    new Nodes(
      map = info.nodes,
      localId = Some(info.localNodeId),
      operatingId = Some(info.localNodeId),
      parentIds = info.parentNodeIds.toSeq
    ).addServers(info.servers)
}
