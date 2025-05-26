package cotoami.repository

import scala.util.chaining._
import com.softwaremill.quicklens._

import cotoami.models.{
  ChildNode,
  Client,
  ClientNode,
  Coto,
  Cotonoma,
  DatabaseRole,
  Id,
  Ito,
  LocalNode,
  Node,
  ParentNode,
  ParentStatus,
  Server
}
import cotoami.backend.InitialDataset

case class Nodes(
    map: Map[Id[Node], Node] = Map.empty,

    // Local: the node that the app has originally opened.
    localId: Option[Id[Node]] = None,

    // Self: the node currently being treated as the local node.
    // This node can be changed via the "Switch Node" feature.
    selfSettings: Option[LocalNode] = None,

    // Focus: the node being browsed.
    focusedId: Option[Id[Node]] = None,

    // Remote nodes
    servers: Servers = Servers(),
    parents: Seq[ParentNode] = Seq.empty,
    activeClients: ActiveClients = ActiveClients()
) {
  def onNodeChange: Nodes = unfocus

  def get(id: Id[Node]): Option[Node] = map.get(id)

  def contains(id: Id[Node]): Boolean = map.contains(id)

  def put(node: Node): Nodes =
    this.modify(_.map).using { map =>
      map.get(node.id) match {
        case Some(existingNode) => {
          // Replace the same node only if the version will be upgraded
          if (node.version > existingNode.version) {
            existingNode.revokeIconUrl() // Side-effect!
            map + (node.id -> node)
          } else {
            map
          }
        }
        case None => map + (node.id -> node)
      }
    }

  def local: Option[Node] = localId.flatMap(get)

  def isLocal(id: Id[Node]): Boolean = Some(id) == localId

  val selfId: Option[Id[Node]] = selfSettings.map(_.nodeId)

  def self: Option[Node] = selfId.flatMap(get)

  def isSelf(id: Id[Node]): Boolean = Some(id) == selfId

  def isSelfRemote: Boolean =
    (localId, selfId) match {
      case (Some(local), Some(self)) => local != self
      case _                         => false
    }

  def focus(id: Option[Id[Node]]): Nodes =
    id.map(id =>
      if (contains(id))
        copy(focusedId = Some(id))
      else
        this
    ).getOrElse(copy(focusedId = None))

  def unfocus: Nodes = copy(focusedId = None)

  def isFocusing(id: Id[Node]): Boolean = focusedId == Some(id)

  def focused: Option[Node] = focusedId.flatMap(get)

  def current: Option[Node] = focused.orElse(self)

  def setIcon(id: Id[Node], icon: String): Nodes =
    this.modify(_.map.index(id)).using(_.setIcon(icon))

  def rename(id: Id[Node], name: String): Nodes =
    this.modify(_.map.index(id)).using(_.rename(name))

  def addServer(server: Server): Nodes =
    this.modify(_.servers).using(_.put(server)).pipe { nodes =>
      server.role.map {
        case DatabaseRole.Parent(parent) => nodes.prependParent(parent)
        case DatabaseRole.Child(child)   => nodes
      }.getOrElse(nodes)
    }

  def addServers(servers: Iterable[Server]): Nodes =
    servers.foldLeft(this)(_ addServer _)

  def clientInfo(clientNode: ClientNode): Option[Client] =
    get(clientNode.nodeId).map(
      Client(_, clientNode, activeClients.get(clientNode.nodeId))
    )

  def parentNodes: Seq[Node] = parents.map(_.nodeId).map(get).flatten

  def prependParent(parent: ParentNode): Nodes =
    this.modify(_.parents).using(parents =>
      parent +: parents.filterNot(_.nodeId == parent.nodeId)
    )

  def isParent(id: Id[Node]): Boolean = parents.exists(_.nodeId == id)

  def anyUnreadPosts: Boolean = parents.exists(_.anyUnreadPosts)

  def parentStatus(parentId: Id[Node]): Option[ParentStatus] = {
    if (!isParent(parentId)) return None

    servers.get(parentId).map(server =>
      server.notConnected
        .map(ParentStatus.ServerDisconnected(_))
        .getOrElse(ParentStatus.Connected(server.childPrivileges))
    )
  }

  def parentConnected(parentId: Id[Node]): Boolean =
    servers.get(parentId).map(_.notConnected.isEmpty).getOrElse(false)

  def childPrivilegesTo(parentId: Id[Node]): Option[ChildNode] =
    parentStatus(parentId) match {
      case Some(ParentStatus.Connected(child)) => child
      case _                                   => None
    }

  def isOwnerOf(nodeId: Id[Node]): Boolean =
    childPrivilegesTo(nodeId).map(_.asOwner).getOrElse(false)

  def currentNodeRootCotonomaId: Option[Id[Cotonoma]] =
    current.flatMap(_.rootCotonomaId)

  def isCurrentNodeRoot(cotonomaId: Id[Cotonoma]): Boolean =
    Some(cotonomaId) == currentNodeRootCotonomaId

  def isNodeRoot(cotonoma: Cotonoma): Boolean =
    get(cotonoma.nodeId)
      .map(_.rootCotonomaId == Some(cotonoma.id))
      .getOrElse(false)

  def isWritable(nodeId: Id[Node]): Boolean =
    isSelf(nodeId) || childPrivilegesTo(nodeId).isDefined

  // A coto can be edited only by its creator, but if a coto is a cotonoma,
  // owners can edit it, too.
  def canEdit(coto: Coto): Boolean =
    isWritable(coto.nodeId) &&
      (isSelf(coto.postedById) ||
        (coto.isCotonoma && isOwnerOf(coto.nodeId)))

  // A coto can be deleted by its creator or the node owner.
  def canDelete(coto: Coto): Boolean =
    isWritable(coto.nodeId) &&
      (isSelf(coto.postedById) || isSelf(coto.nodeId))

  def canEdit(ito: Ito): Boolean = canEditItosIn(ito.nodeId)

  def canEditItosIn(nodeId: Id[Node]): Boolean =
    isSelf(nodeId) ||
      childPrivilegesTo(nodeId)
        .map(_.canEditItos)
        .getOrElse(false)

  def canPostCotonoma(nodeId: Id[Node]): Boolean =
    isSelf(nodeId) ||
      childPrivilegesTo(nodeId)
        .map(_.canPostCotonomas)
        .getOrElse(false)

  def canPromote(coto: Coto): Boolean =
    !coto.isCotonoma && canEdit(coto) && canPostCotonoma(coto.nodeId)
}

object Nodes {
  def apply(dataset: InitialDataset, localId: Id[Node]): Nodes =
    new Nodes(
      map = dataset.nodes,
      localId = Some(localId),
      selfSettings = Some(dataset.localSettings),
      parents = dataset.parents.toSeq
    )
      .addServers(dataset.servers)
      .modify(_.activeClients).using(_.putAll(dataset.activeClients))
}
