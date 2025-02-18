package cotoami.repository

import com.softwaremill.quicklens._

import cotoami.models.{ChildNode, Id, Node, NotConnected, Server}

case class Servers(
    map: Map[Id[Node], Server] = Map.empty
) {
  def get(id: Id[Node]): Option[Server] = map.get(id)

  def put(server: Server): Servers =
    this.modify(_.map).using(_ + (server.server.nodeId -> server))

  def putAll(servers: Iterable[Server]): Servers =
    servers.foldLeft(this)(_ put _)

  def setState(
      id: Id[Node],
      notConnected: Option[NotConnected],
      childPrivileges: Option[ChildNode]
  ): Servers =
    this.modify(_.map.index(id)).using(
      _.copy(
        notConnected = notConnected,
        childPrivileges = childPrivileges
      )
    )

  def contains(id: Id[Node]): Boolean = map.contains(id)
}
