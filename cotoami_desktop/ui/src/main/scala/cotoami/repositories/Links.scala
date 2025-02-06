package cotoami.repositories

import scala.collection.immutable.{HashSet, TreeSet}
import com.softwaremill.quicklens._

import cotoami.models.{Coto, Id, Link}

case class Links(
    map: Map[Id[Link], Link] = Map.empty,

    // Hold each outgoing links in TreeSet so that they are ordered by Link.order
    outgoingLinks: Map[Id[Coto], TreeSet[Link]] = Map.empty,

    // Link IDs indexed by target coto ID
    incomingLinkIds: Map[Id[Coto], HashSet[Id[Link]]] = Map.empty
) {
  def get(id: Id[Link]): Option[Link] = map.get(id)

  def put(link: Link): Links =
    this
      .modify(_.map).using(_ + (link.id -> link))
      .modify(_.outgoingLinks).using(map => {
        map + (link.sourceCotoId ->
          map.get(link.sourceCotoId)
            .map(_.filterNot(_.id == link.id)) // remove old version
            .map(_ + link)
            .getOrElse(TreeSet(link)))
      })
      .modify(_.incomingLinkIds).using(map => {
        map + (link.targetCotoId ->
          map.get(link.targetCotoId)
            .map(_ + link.id)
            .getOrElse(HashSet(link.id)))
      })

  def putAll(links: Iterable[Link]): Links = links.foldLeft(this)(_ put _)

  def replaceOutgoingLinks(cotoId: Id[Coto], links: Iterable[Link]): Links =
    this
      .modify(_.map).using(map =>
        links.foldLeft(map)((map, link) => map + (link.id -> link))
      )
      .modify(_.outgoingLinks).using(map =>
        (map - cotoId) + (cotoId -> TreeSet.from(links))
      )
      .modify(_.incomingLinkIds).using(map =>
        links.foldLeft(map)((map, link) =>
          map + (link.targetCotoId ->
            map.get(link.targetCotoId)
              .map(_ + link.id)
              .getOrElse(HashSet(link.id)))
        )
      )

  def delete(id: Id[Link]): Links =
    this
      .modify(_.map).using(_ - id)
      .modify(_.outgoingLinks).using(
        _.map { case (cotoId, links) => (cotoId, links.filterNot(_.id == id)) }
          .filterNot(_._2.isEmpty)
      )
      .modify(_.incomingLinkIds).using(
        _.map { case (cotoId, linkIds) => (cotoId, linkIds - id) }
          .filterNot(_._2.isEmpty)
      )

  def linked(from: Id[Coto], to: Id[Coto]): Boolean =
    incomingLinkIds.get(to).map(
      _.exists(get(_).map(_.sourceCotoId == from).getOrElse(false))
    ).getOrElse(false)

  def from(id: Id[Coto]): TreeSet[Link] =
    outgoingLinks.get(id).getOrElse(TreeSet.empty)

  def anyFrom(id: Id[Coto]): Boolean =
    outgoingLinks.get(id).map(!_.isEmpty).getOrElse(false)

  def to(id: Id[Coto]): Seq[Link] =
    incomingLinkIds.get(id).map(_.map(get).flatten.toSeq)
      .getOrElse(Seq.empty)

  def onCotoDelete(id: Id[Coto]): Links = {
    val toDelete = (from(id).toSeq ++ to(id)).map(_.id)
    toDelete.foldLeft(this)(_ delete _)
  }
}
