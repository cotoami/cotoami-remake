package cotoami.backend

import scala.scalajs.js

import cotoami.models.{Coto, Cotonoma}

case class CotosRelatedData(json: CotosRelatedDataJson) {
  def postedIn: js.Array[Cotonoma] =
    json.posted_in.map(CotonomaBackend.toModel)
  def asCotonomas: js.Array[Cotonoma] =
    json.as_cotonomas.map(CotonomaBackend.toModel)
  def originals: js.Array[Coto] =
    json.originals.map(CotoBackend.toModel)
}

@js.native
trait CotosRelatedDataJson extends js.Object {
  val posted_in: js.Array[CotonomaJson] = js.native
  val as_cotonomas: js.Array[CotonomaJson] = js.native
  val originals: js.Array[CotoJson] = js.native
}
