package cotoami.backend

import scala.scalajs.js

case class CotosRelatedData(json: CotosRelatedDataJson) {
  def postedIn: js.Array[Cotonoma] = this.json.posted_in.map(Cotonoma(_))
  def asCotonomas: js.Array[Cotonoma] =
    this.json.as_cotonomas.map(Cotonoma(_))
  def originals: js.Array[Coto] = this.json.originals.map(Coto(_))

  def debug: String = {
    val s = new StringBuilder
    s ++= s"postedIn: ${this.postedIn.size}"
    s ++= s", asCotonomas: ${this.asCotonomas.size}"
    s ++= s", originals: ${this.originals.size}"
    s.result()
  }
}

@js.native
trait CotosRelatedDataJson extends js.Object {
  val posted_in: js.Array[CotonomaJson] = js.native
  val as_cotonomas: js.Array[CotonomaJson] = js.native
  val originals: js.Array[CotoJson] = js.native
}
