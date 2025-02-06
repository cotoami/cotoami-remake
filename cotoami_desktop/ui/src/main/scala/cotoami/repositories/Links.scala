package cotoami.repositories

import scala.collection.immutable.{HashSet, TreeSet}
import com.softwaremill.quicklens._

import cotoami.models.{Coto, Id, Link}

case class Links(
    map: Map[Id[Link], Link] = Map.empty,
    outgoingLinks: OutgoingLinks = OutgoingLinks(),
    incomingLinkIds: IncomingLinkIds = IncomingLinkIds()
) {
  def get(id: Id[Link]): Option[Link] = map.get(id)

  def put(link: Link): Links =
    this
      .modify(_.map).using(_ + (link.id -> link))
      .modify(_.outgoingLinks).using(_.put(link))
      .modify(_.incomingLinkIds).using(_.put(link))

  def putAll(links: Iterable[Link]): Links = links.foldLeft(this)(_ put _)

  def replaceOutgoingLinks(cotoId: Id[Coto], links: Iterable[Link]): Links =
    this
      .modify(_.map).using(map =>
        links.foldLeft(map)((map, link) => map + (link.id -> link))
      )
      .modify(_.outgoingLinks).using(_.replace(cotoId, links))
      .modify(_.incomingLinkIds).using(_.putAll(links))

  def delete(id: Id[Link]): Links =
    this
      .modify(_.map).using(_ - id)
      .modify(_.outgoingLinks).using(_.delete(id))
      .modify(_.incomingLinkIds).using(_.delete(id))

  def linked(from: Id[Coto], to: Id[Coto]): Boolean =
    incomingLinkIds.get(to).map(
      _.exists(get(_).map(_.sourceCotoId == from).getOrElse(false))
    ).getOrElse(false)

  def from(id: Id[Coto]): TreeSet[Link] = outgoingLinks.get(id)

  def anyFrom(id: Id[Coto]): Boolean = outgoingLinks.anyFrom(id)

  def to(id: Id[Coto]): Seq[Link] =
    incomingLinkIds.get(id).map(_.map(get).flatten.toSeq)
      .getOrElse(Seq.empty)

  def onCotoDelete(id: Id[Coto]): Links = {
    val toDelete = (from(id).toSeq ++ to(id)).map(_.id)
    toDelete.foldLeft(this)(_ delete _)
  }
}

// Hold each outgoing links in TreeSet so that they are ordered by Link.order
case class OutgoingLinks(map: Map[Id[Coto], TreeSet[Link]] = Map.empty)
    extends AnyVal {
  def get(id: Id[Coto]): TreeSet[Link] = map.get(id).getOrElse(TreeSet.empty)

  def anyFrom(id: Id[Coto]): Boolean =
    map.get(id).map(!_.isEmpty).getOrElse(false)

  def put(link: Link): OutgoingLinks =
    copy(map =
      map + (link.sourceCotoId ->
        map.get(link.sourceCotoId)
          .map(_.filterNot(_.id == link.id)) // remove old version
          .map(_ + link)
          .getOrElse(TreeSet(link)))
    )

  def replace(cotoId: Id[Coto], links: Iterable[Link]): OutgoingLinks =
    copy(map = (map - cotoId) + (cotoId -> TreeSet.from(links)))

  def delete(id: Id[Link]): OutgoingLinks =
    copy(map = map.map { case (cotoId, links) =>
      (cotoId, links.filterNot(_.id == id))
    }
      .filterNot(_._2.isEmpty))
}

// Link IDs indexed by target coto ID
case class IncomingLinkIds(map: Map[Id[Coto], HashSet[Id[Link]]] = Map.empty)
    extends AnyVal {
  def get(id: Id[Coto]): Option[HashSet[Id[Link]]] = map.get(id)

  def put(link: Link): IncomingLinkIds =
    copy(map =
      map + (link.targetCotoId ->
        map.get(link.targetCotoId)
          .map(_ + link.id)
          .getOrElse(HashSet(link.id)))
    )

  def putAll(links: Iterable[Link]): IncomingLinkIds =
    links.foldLeft(this)(_ put _)

  def delete(id: Id[Link]): IncomingLinkIds =
    copy(map = map.map { case (cotoId, linkIds) => (cotoId, linkIds - id) }
      .filterNot(_._2.isEmpty))
}
