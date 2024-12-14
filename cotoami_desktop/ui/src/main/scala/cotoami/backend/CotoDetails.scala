package cotoami.backend

import scala.scalajs.js
import fui.Cmd

import cotoami.models.{Coto, Id}

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
