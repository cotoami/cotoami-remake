package cotoami.repository

import com.softwaremill.quicklens._

import cotoami.models.{ActiveClient, Id, Node}

case class ActiveClients(
    map: Map[Id[Node], ActiveClient] = Map.empty
) {
  def count: Int = map.size

  def get(id: Id[Node]): Option[ActiveClient] = map.get(id)

  def put(client: ActiveClient): ActiveClients =
    this.modify(_.map).using(_ + (client.nodeId -> client))

  def putAll(clients: Iterable[ActiveClient]): ActiveClients =
    clients.foldLeft(this)((acc, client) => acc.put(client))

  def remove(id: Id[Node]): ActiveClients =
    this.modify(_.map).using(_.removed(id))
}
