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

  def select(id: Option[Id[Node]]): Nodes =
    id.map(id =>
      if (this.contains(id))
        this.copy(selectedId = Some(id))
      else
        this
    ).getOrElse(this.copy(selectedId = None))

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
    server.role.map {
      case Parent(parent) => nodes.prependParentId(parent.nodeId)
      case Child(child)   => nodes
    }.getOrElse(nodes)
  }

  def addServers(servers: Iterable[Server]): Nodes =
    servers.foldLeft(this)(_ addServer _)

  def setServerState(id: Id[Node], notConnected: Option[NotConnected]): Nodes =
    this.modify(_.serverMap.index(id).notConnected).setTo(notConnected)

  def containsServer(id: Id[Node]): Boolean = this.serverMap.contains(id)

  // Returns a child role (ChildNode) if:
  // * The operating node is a child of the given parent.
  // * The connection between the child and the parent is active.
  def operatingAsChild(parentId: Id[Node]): Option[ChildNode] =
    if (this.parentIds.contains(parentId))
      this.getServer(parentId).map(server =>
        if (server.notConnected.isEmpty)
          server.clientAsChild
        else
          None
      ).getOrElse(None)
    else
      None

  def postable(id: Id[Node]): Boolean =
    if (Some(id) == this.localId)
      true
    else
      this.operatingAsChild(id).isDefined

  def parentStatus(id: Id[Node]): Option[ParentStatus] =
    this.getServer(id).map(_.notConnected.map {
      case NotConnected.Disabled            => ParentStatus.Disabled
      case NotConnected.Connecting(details) => ParentStatus.Connecting(details)
      case NotConnected.InitFailed(details) => ParentStatus.InitFailed(details)
      case NotConnected.Disconnected(details) =>
        ParentStatus.Disconnected(details)
    }.getOrElse(ParentStatus.Connected))
}

sealed trait ParentStatus
object ParentStatus {
  case object Connected extends ParentStatus
  case object Disabled extends ParentStatus
  case class Connecting(message: Option[String]) extends ParentStatus
  case class InitFailed(message: String) extends ParentStatus
  case class Disconnected(message: Option[String]) extends ParentStatus
}

object Nodes {
  def apply(dataset: InitialDataset) =
    new Nodes(
      map = dataset.nodes,
      localId = Some(dataset.localNodeId),
      operatingId = Some(dataset.localNodeId),
      parentIds = dataset.parentNodeIds.toSeq
    ).addServers(dataset.servers)
}
