package cotoami.repository

import scala.collection.immutable.{HashSet, TreeMap}
import com.softwaremill.quicklens._

import cotoami.models.{Coto, Id, Ito}

case class Itos(
    map: Map[Id[Ito], Ito] = Map.empty,
    outgoingItos: OutgoingItos = OutgoingItos(),
    incomingItoIds: IncomingItoIds = IncomingItoIds()
) {
  def get(id: Id[Ito]): Option[Ito] = map.get(id)

  def put(ito: Ito): Itos =
    this
      .modify(_.map).using(_ + (ito.id -> ito))
      .modify(_.outgoingItos).using(_.put(ito))
      .modify(_.incomingItoIds).using(_.put(ito))

  def putAll(itos: Iterable[Ito]): Itos = itos.foldLeft(this)(_ put _)

  def replaceOutgoingItos(cotoId: Id[Coto], itos: Iterable[Ito]): Itos =
    this
      .modify(_.map).using(map =>
        itos.foldLeft(map)((map, ito) => map + (ito.id -> ito))
      )
      .modify(_.outgoingItos).using(_.replace(cotoId, itos))
      .modify(_.incomingItoIds).using(_.putAll(itos))

  def delete(id: Id[Ito]): Itos =
    this
      .modify(_.map).using(_ - id)
      .modify(_.outgoingItos).using(_.delete(id))
      .modify(_.incomingItoIds).using(_.delete(id))

  def connected(from: Id[Coto], to: Id[Coto]): Boolean =
    incomingItoIds.get(to).map(
      _.exists(get(_).map(_.sourceCotoId == from).getOrElse(false))
    ).getOrElse(false)

  def from(id: Id[Coto]): Iterable[Ito] = outgoingItos.from(id)

  def anyFrom(id: Id[Coto]): Boolean = outgoingItos.anyFrom(id)

  def to(id: Id[Coto]): Seq[Ito] =
    incomingItoIds.get(id).map(_.map(get).flatten.toSeq)
      .getOrElse(Seq.empty)

  def onCotoDelete(id: Id[Coto]): Itos = {
    val toDelete = (from(id).toSeq ++ to(id)).map(_.id)
    toDelete.foldLeft(this)(_ delete _)
  }
}

// Hold each outgoing itos in TreeMap so that they are ordered by Ito.order
case class OutgoingItos(map: Map[Id[Coto], TreeMap[Int, Ito]] = Map.empty)
    extends AnyVal {
  def from(id: Id[Coto]): Iterable[Ito] =
    map.get(id).map(_.values).getOrElse(Seq.empty)

  def anyFrom(id: Id[Coto]): Boolean =
    map.get(id).map(!_.isEmpty).getOrElse(false)

  def put(ito: Ito): OutgoingItos =
    copy(map =
      map + (ito.sourceCotoId ->
        map.get(ito.sourceCotoId)
          .map(_.filterNot(_._2.id == ito.id)) // remove old version
          .map(_ + (ito.order -> ito))
          .getOrElse(TreeMap(ito.order -> ito)))
    )

  def replace(cotoId: Id[Coto], itos: Iterable[Ito]): OutgoingItos =
    copy(map =
      (map - cotoId) + (cotoId ->
        TreeMap.from(itos.map(ito => ito.order -> ito)))
    )

  def delete(id: Id[Ito]): OutgoingItos =
    copy(map = map.map { case (cotoId, itos) =>
      (cotoId, itos.filterNot(_._2.id == id))
    }
      .filterNot(_._2.isEmpty))
}

// Ito IDs indexed by target coto ID
case class IncomingItoIds(map: Map[Id[Coto], HashSet[Id[Ito]]] = Map.empty)
    extends AnyVal {
  def get(id: Id[Coto]): Option[HashSet[Id[Ito]]] = map.get(id)

  def put(ito: Ito): IncomingItoIds =
    copy(map =
      map + (ito.targetCotoId ->
        map.get(ito.targetCotoId)
          .map(_ + ito.id)
          .getOrElse(HashSet(ito.id)))
    )

  def putAll(itos: Iterable[Ito]): IncomingItoIds =
    itos.foldLeft(this)(_ put _)

  def delete(id: Id[Ito]): IncomingItoIds =
    copy(map = map.map { case (cotoId, itoIds) => (cotoId, itoIds - id) }
      .filterNot(_._2.isEmpty))
}
