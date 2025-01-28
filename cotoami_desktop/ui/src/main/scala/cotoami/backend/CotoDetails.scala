package cotoami.backend

import scala.scalajs.js
import fui.Cmd

import cotoami.models.{Coto, Id, Link}

case class CotoDetails(json: CotoDetailsJson) {
  def coto: Coto = CotoBackend.toModel(json.coto)
  def relatedData: CotosRelatedData = CotosRelatedData(json.related_data)
  def outgoingLinks: js.Array[Link] =
    json.outgoing_links.map(LinkBackend.toModel)
}

object CotoDetails {
  def fetch(id: Id[Coto]): Cmd.One[Either[ErrorJson, CotoDetails]] =
    CotoDetailsJson.fetch(id).map(_.map(CotoDetails(_)))
}

@js.native
trait CotoDetailsJson extends js.Object {
  val coto: CotoJson = js.native
  val related_data: CotosRelatedDataJson = js.native
  val outgoing_links: js.Array[LinkJson] = js.native
}

object CotoDetailsJson {
  def fetch(
      id: Id[Coto]
  ): Cmd.One[Either[ErrorJson, CotoDetailsJson]] =
    Commands.send(Commands.CotoDetails(id))
}
