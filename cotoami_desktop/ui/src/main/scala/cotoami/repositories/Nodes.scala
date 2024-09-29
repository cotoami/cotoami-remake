package cotoami.repositories

import com.softwaremill.quicklens._

import cotoami.models.{
  ChildNode,
  DatabaseRole,
  Id,
  Node,
  NotConnected,
  ParentStatus,
  Server
}
import cotoami.backend.InitialDataset

case class Nodes(
    map: Map[Id[Node], Node] = Map.empty,
    localId: Option[Id[Node]] = None,
    operatingId: Option[Id[Node]] = None,
    focusedId: Option[Id[Node]] = None,

    // roles
    parentIds: Seq[Id[Node]] = Seq.empty,
    serverMap: Map[Id[Node], Server] = Map.empty
) {
  def get(id: Id[Node]): Option[Node] = this.map.get(id)

  def contains(id: Id[Node]): Boolean = this.map.contains(id)

  def put(node: Node): Nodes =
    this.modify(_.map).using { map =>
      map.get(node.id) match {
        case Some(existingNode) if existingNode == node => {
          // To avoid a redundant icon url change,
          // it won't be replaced with the same node.
          map
        }
        case Some(existingNode) => {
          existingNode.revokeIconUrl() // Side-effect!
          map + (node.id -> node)
        }
        case None => map + (node.id -> node)
      }
    }

  def local: Option[Node] = this.localId.flatMap(this.get)

  def isLocal(id: Id[Node]): Boolean = Some(id) == this.localId

  def operating: Option[Node] = this.operatingId.flatMap(this.get)

  def operatingRemote: Boolean = (this.localId, this.operatingId) match {
    case (Some(local), Some(operating)) => local != operating
    case _                              => false
  }

  def isOperating(id: Id[Node]): Boolean = this.operatingId == Some(id)

  def parents: Seq[Node] = this.parentIds.map(this.get).flatten

  def focus(id: Option[Id[Node]]): Nodes =
    id.map(id =>
      if (this.contains(id))
        this.copy(focusedId = Some(id))
      else
        this
    ).getOrElse(this.copy(focusedId = None))

  def isFocusing(id: Id[Node]): Boolean = this.focusedId == Some(id)

  def focused: Option[Node] = this.focusedId.flatMap(this.get)

  def current: Option[Node] = this.focused.orElse(this.operating)

  def prependParentId(id: Id[Node]): Nodes =
    if (this.parentIds.contains(id)) this
    else
      this.modify(_.parentIds).using(id +: _)

  def setIcon(id: Id[Node], icon: String): Nodes =
    this.modify(_.map.index(id)).using(_.setIcon(icon))

  def getServer(id: Id[Node]): Option[Server] = this.serverMap.get(id)

  def addServer(server: Server): Nodes = {
    val nodes =
      this.modify(_.serverMap).using(_ + (server.server.nodeId -> server))
    server.role.map {
      case DatabaseRole.Parent(parent) => nodes.prependParentId(parent.nodeId)
      case DatabaseRole.Child(child)   => nodes
    }.getOrElse(nodes)
  }

  def addServers(servers: Iterable[Server]): Nodes =
    servers.foldLeft(this)(_ addServer _)

  def setServerState(
      id: Id[Node],
      notConnected: Option[NotConnected],
      clientAsChild: Option[ChildNode]
  ): Nodes =
    this.modify(_.serverMap.index(id)).using(
      _.copy(notConnected = notConnected, clientAsChild = clientAsChild)
    )

  def containsServer(id: Id[Node]): Boolean = this.serverMap.contains(id)

  def parentStatus(parentId: Id[Node]): Option[ParentStatus] =
    if (this.parentIds.contains(parentId))
      this.getServer(parentId).map(server =>
        server.notConnected.map {
          case NotConnected.Disabled => ParentStatus.Disabled
          case NotConnected.Connecting(details) =>
            ParentStatus.Connecting(details)
          case NotConnected.InitFailed(details) =>
            ParentStatus.InitFailed(details)
          case NotConnected.Disconnected(details) =>
            ParentStatus.Disconnected(details)
        }.getOrElse(
          ParentStatus.Connected(server.clientAsChild)
        )
      )
    else
      None

  def asChildOf(parentId: Id[Node]): Option[ChildNode] =
    parentStatus(parentId) match {
      case Some(ParentStatus.Connected(child)) => child
      case _                                   => None
    }

  def postableTo(id: Id[Node]): Boolean =
    if (Some(id) == this.localId || Some(id) == this.operatingId)
      true
    else
      this.parentStatus(id).map {
        case ParentStatus.Connected(Some(child)) => true
        case _                                   => false
      }.getOrElse(false)
}

object Nodes {
  def apply(dataset: InitialDataset, localId: Id[Node]): Nodes =
    new Nodes(
      map = dataset.nodes,
      localId = Some(localId),
      operatingId = Some(dataset.localNodeId),
      parentIds = dataset.parentNodeIds.toSeq
    ).addServers(dataset.servers)
}
