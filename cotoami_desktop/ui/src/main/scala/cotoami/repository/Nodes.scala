package cotoami.repository

import scala.util.chaining._
import com.softwaremill.quicklens._

import cotoami.models.{
  ChildNode,
  Coto,
  Cotonoma,
  DatabaseRole,
  Id,
  Link,
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

    // remote nodes
    servers: Servers = Servers(),
    activeClients: ActiveClients = ActiveClients(),
    parentIds: Seq[Id[Node]] = Seq.empty
) {
  def get(id: Id[Node]): Option[Node] = map.get(id)

  def contains(id: Id[Node]): Boolean = map.contains(id)

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

  def local: Option[Node] = localId.flatMap(get)

  def isLocal(id: Id[Node]): Boolean = Some(id) == localId

  def operating: Option[Node] = operatingId.flatMap(get)

  def operatingRemote: Boolean = (localId, operatingId) match {
    case (Some(local), Some(operating)) => local != operating
    case _                              => false
  }

  def isOperating(id: Id[Node]): Boolean = operatingId == Some(id)

  def parents: Seq[Node] = parentIds.map(get).flatten

  def focus(id: Option[Id[Node]]): Nodes =
    id.map(id =>
      if (contains(id))
        copy(focusedId = Some(id))
      else
        this
    ).getOrElse(copy(focusedId = None))

  def isFocusing(id: Id[Node]): Boolean = focusedId == Some(id)

  def focused: Option[Node] = focusedId.flatMap(get)

  def current: Option[Node] = focused.orElse(operating)

  def prependParentId(id: Id[Node]): Nodes =
    if (parentIds.contains(id)) this
    else
      this.modify(_.parentIds).using(id +: _)

  def setIcon(id: Id[Node], icon: String): Nodes =
    this.modify(_.map.index(id)).using(_.setIcon(icon))

  def rename(id: Id[Node], name: String): Nodes =
    this.modify(_.map.index(id)).using(_.rename(name))

  def addServer(server: Server): Nodes =
    this.modify(_.servers).using(_.put(server)).pipe { nodes =>
      server.role.map {
        case DatabaseRole.Parent(parent) => nodes.prependParentId(parent.nodeId)
        case DatabaseRole.Child(child)   => nodes
      }.getOrElse(nodes)
    }

  def addServers(servers: Iterable[Server]): Nodes =
    servers.foldLeft(this)(_ addServer _)

  def parentStatus(parentId: Id[Node]): Option[ParentStatus] = {
    if (!parentIds.contains(parentId)) return None

    servers.get(parentId).map(server =>
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
  }

  def parentConnection(parentId: Id[Node]): Option[ChildNode] =
    parentStatus(parentId) match {
      case Some(ParentStatus.Connected(child)) => child
      case _                                   => None
    }

  def reachable(nodeId: Id[Node]): Boolean =
    isOperating(nodeId) || parentConnection(nodeId).isDefined

  def isOwnerOf(nodeId: Id[Node]): Boolean =
    parentConnection(nodeId).map(_.asOwner).getOrElse(false)

  def currentNodeRootCotonomaId: Option[Id[Cotonoma]] =
    current.flatMap(_.rootCotonomaId)

  def isCurrentNodeRoot(cotonomaId: Id[Cotonoma]): Boolean =
    Some(cotonomaId) == currentNodeRootCotonomaId

  def isNodeRoot(cotonoma: Cotonoma): Boolean =
    get(cotonoma.nodeId)
      .map(_.rootCotonomaId == Some(cotonoma.id))
      .getOrElse(false)

  def canPostTo(nodeId: Id[Node]): Boolean = reachable(nodeId)

  // A coto can be edited/deleted only by its creator.
  def canEdit(coto: Coto): Boolean =
    isOperating(coto.postedById) && reachable(coto.nodeId)

  // A link can be edited/deleted by:
  // the creator or an owner of the node in which it was created.
  def canEdit(link: Link): Boolean =
    (isOperating(link.createdById) || isOwnerOf(link.nodeId)) &&
      reachable(link.nodeId)

  def canCreateLinksIn(nodeId: Id[Node]): Boolean =
    isOperating(nodeId) ||
      parentConnection(nodeId).map(_.canEditLinks).getOrElse(false)

  def canPromote(coto: Coto): Boolean = canEdit(coto) && !coto.isCotonoma
}

object Nodes {
  def apply(dataset: InitialDataset, localId: Id[Node]): Nodes =
    new Nodes(
      map = dataset.nodes,
      localId = Some(localId),
      operatingId = Some(dataset.localNodeId),
      parentIds = dataset.parentNodeIds.toSeq
    )
      .addServers(dataset.servers)
      .modify(_.activeClients).using(_.putAll(dataset.activeClients))
}
