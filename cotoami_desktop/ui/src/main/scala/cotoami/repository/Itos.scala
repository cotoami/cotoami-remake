package cotoami.repository

import scala.collection.immutable.TreeMap
import scala.collection.immutable.HashSet
import com.softwaremill.quicklens._

import cotoami.models.{Coto, Id, Ito, Node}

case class Itos(
    map: Map[Id[Ito], Ito] = Map.empty,
    bySource: Itos.BySource = Itos.BySource(),
    byTarget: Itos.ByTarget = Itos.ByTarget()
) {
  def get(id: Id[Ito]): Option[Ito] = map.get(id)

  def put(ito: Ito): Itos =
    this
      .modify(_.map).using(_ + (ito.id -> ito))
      .modify(_.bySource).using(_.put(ito))
      .modify(_.byTarget).using(_.put(ito))

  def putAll(itos: Iterable[Ito]): Itos = itos.foldLeft(this)(_ put _)

  def replaceSiblingGroup(
      cotoId: Id[Coto],
      nodeId: Id[Node],
      itos: Iterable[Ito]
  ): Itos =
    this
      .putAll(itos)
      .modify(_.bySource).using(_.replaceSiblingGroup(cotoId, nodeId, itos))
      .modify(_.byTarget).using(_.putAll(itos))

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

  def from(cotoId: Id[Coto]): Map[Id[Node], Seq[Ito]] =
    bySource.from(cotoId).map(_.byNode.map { case (nodeId, ids) =>
      (nodeId, ids.values.map(get).flatten.toSeq)
    }.toMap).getOrElse(Map.empty)

  def anyFrom(cotoId: Id[Coto]): Boolean = bySource.anyFrom(cotoId)

  def onlyOneFrom(id: Id[Coto]): Option[Ito] =
    bySource.onlyOneFrom(id).flatMap(get)

  def hasDuplicateOrder(ito: Ito): Boolean =
    bySource.hasDuplicateOrder(ito)

  def to(cotoId: Id[Coto]): Seq[Ito] =
    byTarget.get(cotoId).map(_.map(get).flatten.toSeq)
      .getOrElse(Seq.empty)

  def onCotoDelete(cotoId: Id[Coto]): Itos = {
    val itosFrom = from(cotoId).values.flatten
    val itosTo = to(cotoId)
    val toDelete = (itosFrom ++ itosTo).map(_.id)
    toDelete.foldLeft(this)(_ delete _)
  }
}

object Itos {

  // Ito IDs grouped by source coto IDs.
  case class BySource(
      map: Map[Id[Coto], SiblingIds] = Map.empty
  ) extends AnyVal {
    def from(id: Id[Coto]): Option[SiblingIds] = map.get(id)

    def anyFrom(id: Id[Coto]): Boolean =
      from(id).map(!_.isEmpty).getOrElse(false)

    def onlyOneFrom(id: Id[Coto]): Option[Id[Ito]] =
      from(id).flatMap(_.onlyOne)

    def hasDuplicateOrder(ito: Ito): Boolean =
      from(ito.sourceCotoId).map(_.hasDuplicateOrder(ito)).getOrElse(false)

    def put(ito: Ito): BySource =
      copy(map =
        map.updated(
          ito.sourceCotoId,
          map.get(ito.sourceCotoId)
            .map(_.put(ito))
            .getOrElse(SiblingIds().put(ito))
        )
      )

    def replaceSiblingGroup(
        cotoId: Id[Coto],
        nodeId: Id[Node],
        itos: Iterable[Ito]
    ): BySource =
      this.modify(_.map.index(cotoId)).using(_.replaceGroup(nodeId, itos))

    def delete(ito: Ito): BySource =
      this
        .modify(_.map.index(ito.sourceCotoId)).using(_.delete(ito))
        .modify(_.map).using(_.filterNot(_._2.isEmpty))
  }

  // Sibling ito IDs grouped by belonging nodes.
  // Each grouped IDs are sorted in TreeMap by Ito.order.
  case class SiblingIds(
      byNode: Map[Id[Node], TreeMap[Int, Id[Ito]]] = Map.empty
  ) extends AnyVal {
    def isEmpty: Boolean = byNode.isEmpty

    def all: Iterable[Id[Ito]] = byNode.values.map(_.values).flatten

    def onlyOne: Option[Id[Ito]] =
      if (byNode.size == 1)
        byNode.values.headOption.flatMap { itos =>
          if (itos.size == 1)
            itos.values.headOption
          else
            None
        }
      else None

    def group(id: Id[Node]): Iterable[Id[Ito]] =
      byNode.get(id).map(_.values).getOrElse(Seq.empty)

    def hasDuplicateOrder(ito: Ito): Boolean =
      byNode.get(ito.nodeId).map(_.contains(ito.order)).getOrElse(false)

    def put(ito: Ito): SiblingIds =
      this.modify(
        _.byNode.atOrElse(ito.nodeId, TreeMap(ito.order -> ito.id))
      ).using(
        _.filterNot(_._2 == ito.id) // remove old version
          .updated(ito.order, ito.id)
      )

    def replaceGroup(nodeId: Id[Node], itos: Iterable[Ito]): SiblingIds =
      this.modify(_.byNode).using(
        _.updated(
          nodeId,
          TreeMap.from(itos.map(ito => (ito.order, ito.id)))
        )
      )

    def delete(ito: Ito): SiblingIds =
      this
        .modify(_.byNode.index(ito.nodeId)).using(
          _.filterNot(_._2 == ito.id)
        )
        .modify(_.byNode).using(_.filterNot(_._2.isEmpty))
  }

  // Ito IDs grouped by target coto IDs.
  case class ByTarget(map: Map[Id[Coto], HashSet[Id[Ito]]] = Map.empty)
      extends AnyVal {
    def get(id: Id[Coto]): Option[HashSet[Id[Ito]]] = map.get(id)

    def put(ito: Ito): ByTarget =
      this.modify(_.map.atOrElse(ito.targetCotoId, HashSet(ito.id))).using(
        _ + ito.id
      )

    def putAll(itos: Iterable[Ito]): ByTarget =
      itos.foldLeft(this)(_ put _)

    def delete(id: Id[Ito]): ByTarget =
      copy(map = map.map { case (cotoId, itoIds) => (cotoId, itoIds - id) }
        .filterNot(_._2.isEmpty))
  }
}
