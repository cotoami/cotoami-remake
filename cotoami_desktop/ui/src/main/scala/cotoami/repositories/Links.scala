package cotoami.repositories

import scala.scalajs.js
import scala.collection.immutable.TreeSet
import com.softwaremill.quicklens._
import cotoami.backend._

case class Links(
    map: Map[Id[Link], Link] = Map.empty,
    mapBySourceCotoId: Map[Id[Coto], TreeSet[Link]] = Map.empty
) {
  def get(id: Id[Link]): Option[Link] = this.map.get(id)

  def linksFrom(id: Id[Coto]): Option[TreeSet[Link]] =
    this.mapBySourceCotoId.get(id)

  def add(json: LinkJson): Links = {
    val link = Link(json)
    this
      .modify(_.map).using(_ + (link.id -> link))
      .modify(_.mapBySourceCotoId).using(map => {
        val links =
          map.get(link.sourceCotoId).map(_ + link)
            .getOrElse(TreeSet(link))
        map + (link.sourceCotoId -> links)
      })
  }

  def addAll(jsons: js.Array[LinkJson]): Links =
    jsons.foldLeft(this)(_ add _)
}
