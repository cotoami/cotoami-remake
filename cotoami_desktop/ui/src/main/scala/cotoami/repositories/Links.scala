package cotoami.repositories

import scala.collection.immutable.{HashSet, TreeSet}
import com.softwaremill.quicklens._

import cotoami.models.{Coto, Id, Link}

case class Links(
    map: Map[Id[Link], Link] = Map.empty,
    // It breaks SSoT to hold link data duplicated from `map`,
    // but it's needed to keep them sorted in TreeSet.
    mapBySourceCotoId: Map[Id[Coto], TreeSet[Link]] = Map.empty,
    mapByTargetCotoId: Map[Id[Coto], HashSet[Id[Link]]] = Map.empty
) {
  def get(id: Id[Link]): Option[Link] = map.get(id)

  def put(link: Link): Links = {
    this
      .modify(_.map).using(_ + (link.id -> link))
      .modify(_.mapBySourceCotoId).using(map => {
        val outgoingLinks =
          map.get(link.sourceCotoId).map(_ + link)
            .getOrElse(TreeSet(link))
        map + (link.sourceCotoId -> outgoingLinks)
      })
      .modify(_.mapByTargetCotoId).using(map => {
        val incomingLinks =
          map.get(link.targetCotoId).map(_ + link.id)
            .getOrElse(HashSet(link.id))
        map + (link.targetCotoId -> incomingLinks)
      })
  }

  def putAll(links: Iterable[Link]): Links = links.foldLeft(this)(_ put _)

  def delete(id: Id[Link]): Links = {
    this
      .modify(_.map).using(_ - id)
      .modify(_.mapBySourceCotoId).using(
        _.map { case (cotoId, links) => (cotoId, links.filterNot(_.id == id)) }
          .filterNot(_._2.isEmpty)
      )
      .modify(_.mapByTargetCotoId).using(
        _.map { case (cotoId, linkIds) => (cotoId, linkIds - id) }
          .filterNot(_._2.isEmpty)
      )
  }

  def linked(from: Id[Coto], to: Id[Coto]): Boolean =
    mapByTargetCotoId.get(to).map(
      _.exists(get(_).map(_.sourceCotoId == from).getOrElse(false))
    ).getOrElse(false)

  def from(id: Id[Coto]): TreeSet[Link] =
    mapBySourceCotoId.get(id).getOrElse(TreeSet.empty)

  def anyFrom(id: Id[Coto]): Boolean =
    mapBySourceCotoId.get(id).map(!_.isEmpty).getOrElse(false)

  def to(id: Id[Coto]): Seq[Link] =
    mapByTargetCotoId.get(id).map(_.map(get).flatten.toSeq)
      .getOrElse(Seq.empty)

  def onCotoDelete(id: Id[Coto]): Links = {
    val toDelete = (from(id).toSeq ++ to(id)).map(_.id)
    toDelete.foldLeft(this)(_ delete _)
  }
}
