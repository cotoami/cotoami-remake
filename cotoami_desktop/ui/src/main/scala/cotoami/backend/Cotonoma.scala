package cotoami.backend

import scala.scalajs.js
import java.time.Instant
import cotoami.Validation

case class Cotonoma(json: CotonomaJson) {
  def id: Id[Cotonoma] = Id(this.json.uuid)
  def nodeId: Id[Node] = Id(this.json.node_id)
  def cotoId: Id[Coto] = Id(this.json.coto_id)
  def name: String = this.json.name
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)
  lazy val updatedAt: Instant = parseJsonDateTime(this.json.updated_at)
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
