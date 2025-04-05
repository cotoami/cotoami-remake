package cotoami.backend

import scala.scalajs.js
import marubinotto.fui.Cmd

import cotoami.models.{Coto, Id, Ito}

case class CotoDetails(json: CotoDetailsJson) {
  def coto: Coto = CotoBackend.toModel(json.coto)
  def relatedData: CotosRelatedData = CotosRelatedData(json.related_data)
  def outgoingItos: js.Array[Ito] =
    json.outgoing_itos.map(ItoBackend.toModel)
}

object CotoDetails {
  def fetch(id: Id[Coto]): Cmd.One[Either[ErrorJson, CotoDetails]] =
    CotoDetailsJson.fetch(id).map(_.map(CotoDetails(_)))
}

@js.native
trait CotoDetailsJson extends js.Object {
  val coto: CotoJson = js.native
  val related_data: CotosRelatedDataJson = js.native
  val outgoing_itos: js.Array[ItoJson] = js.native
}

object CotoDetailsJson {
  def fetch(
      id: Id[Coto]
  ): Cmd.One[Either[ErrorJson, CotoDetailsJson]] =
    Commands.send(Commands.CotoDetails(id))
}
