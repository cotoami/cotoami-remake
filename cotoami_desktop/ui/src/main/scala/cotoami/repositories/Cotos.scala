package cotoami.repositories

import com.softwaremill.quicklens._

import cotoami.models.{Coto, Geolocation, Id}
import cotoami.backend._

case class Cotos(
    map: Map[Id[Coto], Coto] = Map.empty,
    focusedId: Option[Id[Coto]] = None
) {
  def get(id: Id[Coto]): Option[Coto] = map.get(id)

  def getOriginal(coto: Coto): Coto =
    coto.repostOfId.flatMap(get).getOrElse(coto)

  def contains(id: Id[Coto]): Boolean = map.contains(id)

  def put(coto: Coto): Cotos =
    this.modify(_.map).using { map =>
      map.get(coto.id) match {
        case Some(existingCoto) if existingCoto == coto => {
          // To avoid a redundant media url change,
          // it won't be replaced with the same coto.
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

  def delete(id: Id[Coto]): Cotos =
    copy(
      map = map - id,
      focusedId =
        if (focusedId == Some(id))
          None
        else
          focusedId
    )

  def destroyAndCreate(): Cotos = {
    this.map.values.foreach(_.revokeMediaUrl()) // Side-effect!
    Cotos()
  }

  def importFrom(cotos: CotosPage): Cotos =
    this
      .putAll(cotos.page.items)
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
    focusedId.map(_ == id).getOrElse(false)

  def focused: Option[Coto] = focusedId.flatMap(get)

  def focus(id: Id[Coto]): Cotos =
    get(id).map(coto =>
      // It can't focus on a repost, but only on an original coto.
      copy(focusedId = Some(getOriginal(coto).id))
    ).getOrElse(this)

  def unfocus: Cotos = copy(focusedId = None)

  lazy val geolocated: Seq[(Coto, Geolocation)] =
    map.values.flatMap(coto => coto.geolocation.map(coto -> _)).toSeq
}
