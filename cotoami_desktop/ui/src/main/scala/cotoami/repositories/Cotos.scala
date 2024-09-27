package cotoami.repositories

import com.softwaremill.quicklens._

import cotoami.models.{Coto, Geolocation, Id}
import cotoami.backend._

case class Cotos(
    map: Map[Id[Coto], Coto] = Map.empty,
    focusedId: Option[Id[Coto]] = None
) {
  def get(id: Id[Coto]): Option[Coto] = this.map.get(id)

  def getOriginal(coto: Coto): Coto =
    coto.repostOfId.flatMap(this.get).getOrElse(coto)

  def contains(id: Id[Coto]): Boolean = this.map.contains(id)

  def put(coto: Coto): Cotos =
    this.modify(_.map).using { map =>
      map.get(coto.id) match {
        case Some(existingCoto) if existingCoto == coto => {
          // To avoid redundant media url changes,
          // a coto with the same content as the existing one won't be stored.
          map
        }
        case Some(existingCoto) => {
          existingCoto.revokeMediaUrl() // Side-effect!
          map + (coto.id -> coto)
        }
        case None => map + (coto.id -> coto)
      }
    }

  def putAll(cotos: Iterable[Coto]): Cotos = cotos.foldLeft(this)(_ put _)

  def destroyAndCreate(): Cotos = {
    this.map.values.foreach(_.revokeMediaUrl()) // Side-effect!
    Cotos()
  }

  def importFrom(cotos: PaginatedCotos): Cotos =
    this
      .putAll(cotos.page.rows)
      .putAll(cotos.relatedData.originals)

  def importFrom(cotos: GeolocatedCotos): Cotos =
    this
      .putAll(cotos.cotos)
      .putAll(cotos.relatedData.originals)

  def importFrom(graph: CotoGraph): Cotos =
    this
      .putAll(graph.cotos)
      .putAll(graph.cotosRelatedData.originals)

  def isFocusing(id: Id[Coto]): Boolean =
    this.focusedId.map(_ == id).getOrElse(false)

  def focused: Option[Coto] = this.focusedId.flatMap(this.get)

  def focus(id: Id[Coto]): Cotos =
    this.get(id).map(coto =>
      // It can't focus on a repost, but only on an original coto.
      this.copy(focusedId = Some(this.getOriginal(coto).id))
    ).getOrElse(this)

  def unfocus: Cotos = this.copy(focusedId = None)

  lazy val geolocated: Seq[(Coto, Geolocation)] =
    this.map.values.flatMap(coto => coto.geolocation.map(coto -> _)).toSeq
}
