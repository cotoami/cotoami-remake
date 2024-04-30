package cotoami.repositories

import scala.collection.immutable.TreeSet
import com.softwaremill.quicklens._
import cotoami.backend._

case class Links(
    map: Map[Id[Link], Link] = Map.empty,
    mapBySourceCotoId: Map[Id[Coto], TreeSet[Link]] = Map.empty
) {
  def get(id: Id[Link]): Option[Link] = this.map.get(id)

  def linksFrom(id: Id[Coto]): TreeSet[Link] =
    this.mapBySourceCotoId.get(id).getOrElse(TreeSet.empty)

  def anyLinksFrom(id: Id[Coto]): Boolean =
    this.mapBySourceCotoId.get(id).map(!_.isEmpty).getOrElse(false)

  def linksTo(id: Id[Coto]): Seq[Link] =
    this.map.values.filter(_.targetCotoId == id).toSeq

  def add(link: Link): Links = {
    this
      .modify(_.map).using(_ + (link.id -> link))
      .modify(_.mapBySourceCotoId).using(map => {
        val links =
          map.get(link.sourceCotoId).map(_ + link)
            .getOrElse(TreeSet(link))
        map + (link.sourceCotoId -> links)
      })
  }

  def addAll(links: Iterable[Link]): Links = links.foldLeft(this)(_ add _)
}
