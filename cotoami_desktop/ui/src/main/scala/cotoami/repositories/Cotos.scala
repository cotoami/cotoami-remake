package cotoami.repositories

import com.softwaremill.quicklens._
import cotoami.backend._

case class Cotos(
    map: Map[Id[Coto], Coto] = Map.empty
) {
  def get(id: Id[Coto]): Option[Coto] = this.map.get(id)

  def getOriginal(coto: Coto): Coto =
    coto.repostOfId.flatMap(this.get).getOrElse(coto)

  def put(coto: Coto): Cotos =
    this.modify(_.map).using(_ + (coto.id -> coto))

  def putAll(cotos: Iterable[Coto]): Cotos = cotos.foldLeft(this)(_ put _)

  def importFrom(cotos: PaginatedCotos): Cotos =
    this
      .putAll(cotos.page.rows)
      .putAll(cotos.relatedData.originals)

  def importFrom(graph: CotoGraph): Cotos =
    this
      .putAll(graph.cotos)
      .putAll(graph.cotosRelatedData.originals)
}
