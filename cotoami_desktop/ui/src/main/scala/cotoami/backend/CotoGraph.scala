package cotoami.backend

import scala.scalajs.js
import marubinotto.fui.Cmd

import cotoami.utils.facade.Nullable
import cotoami.models.{Coto, Cotonoma, Id, Ito}

case class CotoGraph(json: CotoGraphJson) {
  def rootCotoId: Id[Coto] = Id(json.root_coto_id)
  def rootCotonoma: Option[Cotonoma] =
    Nullable.toOption(json.root_cotonoma).map(CotonomaBackend.toModel)
  def cotos: js.Array[Coto] = json.cotos.map(CotoBackend.toModel)
  def cotosRelatedData: CotosRelatedData =
    CotosRelatedData(json.cotos_related_data)
  def itos: js.Array[Ito] = json.itos.map(ItoBackend.toModel)
}

object CotoGraph {
  def fetchFromCoto(coto: Id[Coto]): Cmd.One[Either[ErrorJson, CotoGraph]] =
    CotoGraphJson.fetchFromCoto(coto).map(_.map(CotoGraph(_)))

  def fetchFromCotonoma(
      cotonoma: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, CotoGraph]] =
    CotoGraphJson.fetchFromCotonoma(cotonoma).map(_.map(CotoGraph(_)))
}

@js.native
trait CotoGraphJson extends js.Object {
  val root_coto_id: String = js.native
  val root_cotonoma: Nullable[CotonomaJson] = js.native
  val cotos: js.Array[CotoJson] = js.native
  val cotos_related_data: CotosRelatedDataJson = js.native
  val itos: js.Array[ItoJson] = js.native
}

object CotoGraphJson {
  def fetchFromCoto(
      coto: Id[Coto]
  ): Cmd.One[Either[ErrorJson, CotoGraphJson]] =
    Commands.send(Commands.GraphFromCoto(coto))

  def fetchFromCotonoma(
      cotonoma: Id[Cotonoma]
  ): Cmd.One[Either[ErrorJson, CotoGraphJson]] =
    Commands.send(Commands.GraphFromCotonoma(cotonoma))
}
