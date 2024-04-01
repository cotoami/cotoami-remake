package cotoami.backend

import cotoami.Id

case class Cotonomas(
    map: Map[Id[Cotonoma], Cotonoma] = Map.empty,

    // The currently selected cotonoma and its super/sub cotonomas
    selectedId: Option[Id[Cotonoma]] = None,
    superIds: Seq[Id[Cotonoma]] = Seq.empty,
    subIds: PaginatedIds[Cotonoma] = PaginatedIds(),

    // Recent
    recentIds: PaginatedIds[Cotonoma] = PaginatedIds()
) {
  def get(id: Id[Cotonoma]): Option[Cotonoma] = this.map.get(id)

  def contains(id: Id[Cotonoma]): Boolean = this.map.contains(id)

  def select(id: Id[Cotonoma]): Cotonomas =
    if (this.contains(id))
      this.deselect().copy(selectedId = Some(id))
    else
      this

  def setCotonomaDetails(details: CotonomaDetailsJson): Cotonomas = {
    val cotonoma = Cotonoma(details.cotonoma)
    val map = Cotonoma.toMap(details.supers) ++
      Cotonoma.toMap(details.subs.rows) +
      (cotonoma.id -> cotonoma)
    this.deselect().copy(
      selectedId = Some(cotonoma.id),
      superIds = details.supers.map(json => Id[Cotonoma](json.uuid)).toSeq,
      subIds = this.subIds.addPage(
        details.subs,
        (json: CotonomaJson) => Id[Cotonoma](json.uuid)
      ),
      map = this.map ++ map
    )
  }

  def deselect(): Cotonomas =
    this.copy(selectedId = None, superIds = Seq.empty, subIds = PaginatedIds())

  def isSelecting(id: Id[Cotonoma]): Boolean =
    this.selectedId.map(_ == id).getOrElse(false)

  def selected: Option[Cotonoma] = this.selectedId.flatMap(this.get(_))

  def supers: Seq[Cotonoma] = this.superIds.map(this.get(_)).flatten

  def subs: Seq[Cotonoma] = this.subIds.order.map(this.get(_)).flatten

  def addPageOfSubs(page: Paginated[CotonomaJson]): Cotonomas =
    this.copy(
      map = this.map ++ Cotonoma.toMap(page.rows),
      subIds = this.subIds.addPage(
        page,
        (json: CotonomaJson) => Id[Cotonoma](json.uuid)
      )
    )

  def recent: Seq[Cotonoma] = this.recentIds.order.map(this.get(_)).flatten

  def addPageOfRecent(page: Paginated[CotonomaJson]): Cotonomas =
    this.copy(
      map = this.map ++ Cotonoma.toMap(page.rows),
      recentIds = this.recentIds.addPage(
        page,
        (json: CotonomaJson) => Id[Cotonoma](json.uuid)
      )
    )
}
