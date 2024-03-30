package cotoami.backend

import scala.scalajs.js
import java.time.LocalDateTime
import cotoami.{Id, Validation}

case class Cotonomas(
    map: Map[Id[Cotonoma], Cotonoma] = Map.empty,

    // The currently selected cotonoma and its super/sub cotonomas
    selectedId: Option[Id[Cotonoma]] = None,
    superIds: Seq[Id[Cotonoma]] = Seq.empty,
    subIds: Seq[Id[Cotonoma]] = Seq.empty,

    // Recent
    recentIds: PaginatedIds[Cotonoma] = PaginatedIds()
) {
  def get(id: Id[Cotonoma]): Option[Cotonoma] = this.map.get(id)

  def contains(id: Id[Cotonoma]): Boolean = this.map.contains(id)

  def select(id: Id[Cotonoma]): Cotonomas =
    if (this.contains(id))
      this.copy(selectedId = Some(id))
    else
      this

  def deselect(): Cotonomas =
    this.copy(selectedId = None, superIds = Seq.empty, subIds = Seq.empty)

  def isSelecting(id: Id[Cotonoma]): Boolean =
    this.selectedId.map(_ == id).getOrElse(false)

  def selected: Option[Cotonoma] = this.selectedId.flatMap(this.get(_))

  def anySupers: Boolean = !this.superIds.isEmpty

  def supers: Seq[Cotonoma] = this.superIds.map(this.get(_)).flatten

  def subs: Seq[Cotonoma] = this.subIds.map(this.get(_)).flatten

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

case class Cotonoma(json: CotonomaJson) {
  def id: Id[Cotonoma] = Id(this.json.uuid)
  def nodeId: Id[Node] = Id(this.json.node_id)
  def cotoId: Id[Coto] = Id(this.json.coto_id)
  def name: String = this.json.name
  lazy val createdAt: LocalDateTime = parseJsonDateTime(this.json.created_at)
  lazy val updatedAt: LocalDateTime = parseJsonDateTime(this.json.updated_at)
  def posts: Int = this.json.posts
}

object Cotonoma {
  val NameMaxLength = 50

  def validateName(name: String): Seq[Validation.Error] = {
    Vector(
      Validation.nonBlank(name),
      Validation.length(name, 1, NameMaxLength)
    ).flatten
  }

  def toMap(jsons: js.Array[CotonomaJson]): Map[Id[Cotonoma], Cotonoma] =
    jsons.map(json => (Id[Cotonoma](json.uuid), Cotonoma(json))).toMap
}

@js.native
trait CotonomaJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val coto_id: String = js.native
  val name: String = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
  val posts: Int = js.native
}

@js.native
trait CotonomaDetailsJson extends js.Object {
  val cotonoma: CotonomaJson = js.native
  val coto: CotoJson = js.native
  val supers: js.Array[CotonomaJson] = js.native
  val subs: Paginated[CotonomaJson] = js.native
}
