package cotoami.repository

import com.softwaremill.quicklens._

import cotoami.models.{Coto, Geolocation, Id}
import cotoami.backend._

case class Cotos(
    map: Map[Id[Coto], Coto] = Map.empty,
    focusedId: Option[Id[Coto]] = None,
    selectedIds: Seq[Id[Coto]] = Seq.empty
) {
  def onCotonomaChange(): Cotos =
    this
      .unfocus
      .modify(_.map).using(
        _.filter { case (id, coto) =>
          if (isSelecting(id))
            true // retain selected cotos
          else {
            coto.revokeMediaUrl() // Side-effect!
            false
          }
        }
      )

  def get(id: Id[Coto]): Option[Coto] = map.get(id)

  def getOriginal(coto: Coto): Option[Coto] =
    coto.repostOfId match {
      case Some(repostOfId) => get(repostOfId)
      case None             => Some(coto)
    }

  def contains(id: Id[Coto]): Boolean = map.contains(id)

  def isCotonoma(id: Id[Coto]): Option[Boolean] = get(id).map(_.isCotonoma)

  def repostsOf(id: Id[Coto]): Seq[Coto] =
    map.values.filter(_.repostOfId == Some(id)).toSeq

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

  def delete(id: Id[Coto]): Cotos = {
    get(id).foreach(_.revokeMediaUrl()) // Side-effect!
    copy(
      map = map - id,
      focusedId = if (isFocusing(id)) None else focusedId
    )
  }

  def importFrom(cotos: PaginatedCotos): Cotos =
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
      copy(focusedId = getOriginal(coto).map(_.id))
    ).getOrElse(this)

  def unfocus: Cotos = copy(focusedId = None)

  def isSelecting(id: Id[Coto]): Boolean = selectedIds.contains(id)

  def selected: Seq[Coto] = selectedIds.flatMap(get)

  def select(id: Id[Coto]): Cotos =
    if (isSelecting(id))
      this
    else
      this.modify(_.selectedIds).using(_ :+ id)

  def deselect(id: Id[Coto]): Cotos =
    this.modify(_.selectedIds).using(_.filterNot(_ == id))

  lazy val geolocated: Seq[(Coto, Geolocation)] =
    map.values.flatMap(coto => coto.geolocation.map(coto -> _)).toSeq
}
