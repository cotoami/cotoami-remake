package cotoami.repository

import com.softwaremill.quicklens._

import cotoami.models.{Coto, Id, Node, ParentNode}

case class Parents(
    map: Map[Id[Node], ParentNode] = Map.empty,
    order: Seq[Id[Node]] = Seq.empty
) {
  def nodeIds: Seq[Id[Node]] = order

  def contains(id: Id[Node]): Boolean = map.contains(id)

  def get(id: Id[Node]): Option[ParentNode] = map.get(id)

  lazy val anyUnreadPosts: Boolean = map.values.exists(_.anyUnreadPosts)

  def anyUnreadPostsIn(id: Id[Node]): Boolean =
    map.get(id).exists(_.anyUnreadPosts)

  def unread(coto: Coto): Boolean =
    get(coto.nodeId).map(_.unread(coto)).getOrElse(false)

  def prepend(parent: ParentNode): Parents =
    this
      .modify(_.map).using(_ + (parent.nodeId -> parent))
      .modify(_.order).using(order =>
        parent.nodeId +: order.filterNot(_ == parent.nodeId)
      )

  def append(parent: ParentNode): Parents =
    this
      .modify(_.map).using(_ + (parent.nodeId -> parent))
      .modify(_.order).using(order =>
        order.filterNot(_ == parent.nodeId) :+ parent.nodeId
      )

  def appendAll(parents: Iterable[ParentNode]): Parents =
    parents.foldLeft(this)(_ append _)

  def updateOthersLastPostedAt(id: Id[Node], utcIso: Option[String]): Parents =
    this.modify(_.map.index(id).othersLastPostedAtUtcIso).setTo(utcIso)
}
