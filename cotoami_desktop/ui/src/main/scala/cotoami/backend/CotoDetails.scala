package cotoami.backend

import scala.scalajs.js
import marubinotto.fui.Cmd

import cotoami.models.{Coto, Id, Ito}

case class CotoDetails(json: CotoDetailsJson) {
  def coto: Coto = CotoBackend.toModel(json.coto)
  def itos: js.Array[Ito] = json.itos.map(ItoBackend.toModel)
  def incomingNeighbors: js.Array[Coto] =
    json.incoming_neighbors.map(CotoBackend.toModel)
  def relatedData: CotosRelatedData = CotosRelatedData(json.related_data)
}

object CotoDetails {
  def fetch(id: Id[Coto]): Cmd.One[Either[ErrorJson, CotoDetails]] =
    CotoDetailsJson.fetch(id).map(_.map(CotoDetails(_)))
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
  ): Cmd.One[Either[ErrorJson, CotoDetailsJson]] =
    Commands.send(Commands.CotoDetails(id))
}
