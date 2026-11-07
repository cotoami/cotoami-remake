package cotoami.backend

import cats.effect.IO

import scala.scalajs.js

import cotoami.models.{Coto, Cotonoma}

case class CotosRelatedData(
    json: CotosRelatedDataJson,
    originals: js.Array[Coto]
) {
  def postedIn: js.Array[Cotonoma] =
    json.posted_in.map(CotonomaBackend.toModel)
  def asCotonomas: js.Array[Cotonoma] =
    json.as_cotonomas.map(CotonomaBackend.toModel)

}

@js.native
trait CotosRelatedDataJson extends js.Object {
  val posted_in: js.Array[CotonomaJson] = js.native
  val as_cotonomas: js.Array[CotonomaJson] = js.native
  val originals: js.Array[CotoJson] = js.native
}

object CotosRelatedData {
  def decode(json: CotosRelatedDataJson): IO[CotosRelatedData] =
    for {
      originals <- decodeArray(json.originals)(CotoBackend.toModel)
    } yield CotosRelatedData(json, originals)
}
