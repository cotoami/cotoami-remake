package cotoami.backend

import scala.scalajs.js
import cotoami.{Id, Validation}

case class Cotonoma(json: CotonomaJson) {
  def id(): Id[Cotonoma] = Id(this.json.uuid)
  def name(): String = this.json.name
  def nodeId(): Id[Node] = Id(this.json.node_id)
}

object Cotonoma {
  val NameMaxLength = 50

  def validateName(name: String): Seq[Validation.Error] = {
    Vector(
      Validation.nonBlank(name),
      Validation.length(name, 1, NameMaxLength)
    ).flatten
  }

  def toIds(jsons: js.Array[CotonomaJson]): Seq[Id[Cotonoma]] =
    jsons.map(json => Id[Cotonoma](json.uuid)).toSeq

  def toMap(jsons: js.Array[CotonomaJson]): Map[Id[Cotonoma], Cotonoma] =
    jsons.map(json => (Id[Cotonoma](json.uuid), Cotonoma(json))).toMap
}

@js.native
trait CotonomaJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val coto_id: String = js.native
  val name: String = js.native
  val posts: Int = js.native
}
