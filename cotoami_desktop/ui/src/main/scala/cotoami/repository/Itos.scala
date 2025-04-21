package cotoami.repository

import scala.collection.immutable.TreeMap
import scala.collection.immutable.HashSet
import com.softwaremill.quicklens._

import cotoami.models.{Coto, Id, Ito, Node}

case class Itos(
    map: Map[Id[Ito], Ito] = Map.empty,
    bySource: ItosBySource = ItosBySource(),
    byTarget: ItosByTarget = ItosByTarget()
) {
  def get(id: Id[Ito]): Option[Ito] = map.get(id)

  def put(ito: Ito): Itos =
    this
      .modify(_.map).using(_ + (ito.id -> ito))
      .modify(_.bySource).using(_.put(ito))
      .modify(_.byTarget).using(_.put(ito))

  def putAll(itos: Iterable[Ito]): Itos = itos.foldLeft(this)(_ put _)

  def replaceOutgoingItos(cotoId: Id[Coto], itos: SiblingItos): Itos =
    this
      .putAll(itos.all)
      .modify(_.bySource).using(_.replace(cotoId, itos))
      .modify(_.byTarget).using(_.putAll(itos.all))

  def delete(id: Id[Ito]): Itos =
    get(id).map { ito =>
      this
        .modify(_.map).using(_ - ito.id)
        .modify(_.bySource).using(_.delete(ito))
        .modify(_.byTarget).using(_.delete(ito.id))
    }.getOrElse(this)

  def connected(from: Id[Coto], to: Id[Coto]): Boolean =
    byTarget.get(to).map(
      _.exists(get(_).map(_.sourceCotoId == from).getOrElse(false))
    ).getOrElse(false)

  def from(id: Id[Coto]): Option[SiblingItos] = bySource.from(id)

  def anyFrom(id: Id[Coto]): Boolean = bySource.anyFrom(id)

  def hasDuplicateOrder(ito: Ito): Boolean =
    bySource.hasDuplicateOrder(ito)

  def to(id: Id[Coto]): Seq[Ito] =
    byTarget.get(id).map(_.map(get).flatten.toSeq)
      .getOrElse(Seq.empty)

  def onCotoDelete(id: Id[Coto]): Itos = {
    val itosFrom = from(id).map(_.all).getOrElse(Seq.empty)
    val itosTo = to(id)
    val toDelete = (itosFrom ++ itosTo).map(_.id)
    toDelete.foldLeft(this)(_ delete _)
  }
}

// Itos grouped by source coto IDs.
case class ItosBySource(
    map: Map[Id[Coto], SiblingItos] = Map.empty
) extends AnyVal {
  def from(id: Id[Coto]): Option[SiblingItos] = map.get(id)

  def anyFrom(id: Id[Coto]): Boolean =
    from(id).map(!_.isEmpty).getOrElse(false)

  def hasDuplicateOrder(ito: Ito): Boolean =
    from(ito.sourceCotoId).map(_.hasDuplicateOrder(ito)).getOrElse(false)

  def put(ito: Ito): ItosBySource =
    copy(map =
      map.updated(
        ito.sourceCotoId,
        map.get(ito.sourceCotoId)
          .map(_.put(ito))
          .getOrElse(SiblingItos().put(ito))
      )
    )

  def replace(cotoId: Id[Coto], itos: SiblingItos): ItosBySource =
    copy(map = map + (cotoId -> itos))

  def delete(ito: Ito): ItosBySource =
    this
      .modify(_.map.index(ito.sourceCotoId)).using(_.delete(ito))
      .modify(_.map).using(_.filterNot(_._2.isEmpty))
}

// Sibling itos grouped by belonging nodes.
// Each grouped itos are sorted in TreeMap by Ito.order.
case class SiblingItos(byNode: Map[Id[Node], TreeMap[Int, Ito]] = Map.empty) {
  def isEmpty: Boolean = byNode.isEmpty

  def all: Iterable[Ito] = byNode.values.map(_.values).flatten

  def group(id: Id[Node]): Iterable[Ito] =
    byNode.get(id).map(_.values).getOrElse(Seq.empty)

  def hasDuplicateOrder(ito: Ito): Boolean =
    byNode.get(ito.nodeId).map(_.contains(ito.order)).getOrElse(false)

  def put(ito: Ito): SiblingItos =
    this.modify(_.byNode.atOrElse(ito.nodeId, TreeMap(ito.order -> ito))).using(
      _.filterNot(_._2.id == ito.id) // remove old version
        .updated(ito.order, ito)
    )

  def delete(ito: Ito): SiblingItos =
    this
      .modify(_.byNode.index(ito.nodeId)).using(
        _.filterNot(_._2.id == ito.id)
      )
      .modify(_.byNode).using(_.filterNot(_._2.isEmpty))
}

// Ito IDs indexed by target coto ID
case class ItosByTarget(map: Map[Id[Coto], HashSet[Id[Ito]]] = Map.empty)
    extends AnyVal {
  def get(id: Id[Coto]): Option[HashSet[Id[Ito]]] = map.get(id)

  def put(ito: Ito): ItosByTarget =
    this.modify(_.map.atOrElse(ito.targetCotoId, HashSet(ito.id))).using(
      _ + ito.id
    )

  def putAll(itos: Iterable[Ito]): ItosByTarget =
    itos.foldLeft(this)(_ put _)

  def delete(id: Id[Ito]): ItosByTarget =
    copy(map = map.map { case (cotoId, itoIds) => (cotoId, itoIds - id) }
      .filterNot(_._2.isEmpty))
}
