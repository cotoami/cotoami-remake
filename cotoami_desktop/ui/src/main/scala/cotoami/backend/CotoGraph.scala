package cotoami.backend

import scala.scalajs.js

case class CotoGraph(json: CotoGraphJson) {
  def rootCotoId: Id[Coto] = Id(this.json.root_coto_id)
  def rootCotonoma: Option[Cotonoma] =
    Option(this.json.root_cotonoma).map(Cotonoma(_))
  def cotos: js.Array[Coto] = this.json.cotos.map(Coto(_))
  def cotosRelatedData: CotosRelatedData =
    CotosRelatedData(this.json.cotos_related_data)
  def links: js.Array[Link] = this.json.links.map(Link(_))
}

@js.native
trait CotoGraphJson extends js.Object {
  val root_coto_id: String = js.native
  val root_cotonoma: CotonomaJson = js.native
  val cotos: js.Array[CotoJson] = js.native
  val cotos_related_data: CotosRelatedDataJson = js.native
  val links: js.Array[LinkJson] = js.native
}

object CotoGraphJson {
  def debug(graph: CotoGraphJson): String = {
    val s = new StringBuilder
    s ++= s"root_coto_id: ${graph.root_coto_id}"
    s ++= s", root_cotonoma: ${js.JSON.stringify(graph.root_cotonoma)}"
    s ++= s", cotos: ${graph.cotos.size}"
    s ++= s", cotos_related_data: ${CotosRelatedDataJson.debug(graph.cotos_related_data)}"
    s ++= s", links: ${graph.links.size}"
    s.result()
  }
}
