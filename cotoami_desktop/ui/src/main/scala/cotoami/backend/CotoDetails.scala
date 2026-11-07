package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js

import cotoami.models.{Coto, Id, Ito}

case class CotoDetails(
    json: CotoDetailsJson,
    coto: Coto,
    incomingNeighbors: js.Array[Coto],
    relatedData: CotosRelatedData
) {
  def itos: js.Array[Ito] = json.itos.map(ItoBackend.toModel)

}

object CotoDetails {
  def decode(json: CotoDetailsJson): IO[CotoDetails] =
    for {
      coto <- CotoBackend.toModel(json.coto)
      incomingNeighbors <- decodeArray(json.incoming_neighbors)(
        CotoBackend.toModel
      )
      relatedData <- CotosRelatedData.decode(json.related_data)
    } yield CotoDetails(json, coto, incomingNeighbors, relatedData)

  def fetch(id: Id[Coto]): IO[Either[ErrorJson, CotoDetails]] =
    CotoDetailsJson.fetch(id).flatMap(_.traverse(CotoDetails.decode))
}

@js.native
trait CotoDetailsJson extends js.Object {
  val coto: CotoJson = js.native
  val itos: js.Array[ItoJson] = js.native
  val incoming_neighbors: js.Array[CotoJson] = js.native
  val related_data: CotosRelatedDataJson = js.native
}

object CotoDetailsJson {
  def fetch(
      id: Id[Coto]
  ): IO[Either[ErrorJson, CotoDetailsJson]] =
    Commands.send(Commands.CotoDetails(id))
}
