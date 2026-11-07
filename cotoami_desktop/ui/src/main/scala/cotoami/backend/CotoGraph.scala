package cotoami.backend

import cats.effect.IO
import cats.syntax.all.*

import scala.scalajs.js

import marubinotto.facade.Nullable

import cotoami.models.{Coto, Cotonoma, Id, Ito}

case class CotoGraph(
    json: CotoGraphJson,
    cotos: js.Array[Coto],
    cotosRelatedData: CotosRelatedData
) {
  def rootCotoId: Id[Coto] = Id(json.root_coto_id)
  def rootCotonoma: Option[Cotonoma] =
    Nullable.toOption(json.root_cotonoma).map(CotonomaBackend.toModel)
  def itos: js.Array[Ito] = json.itos.map(ItoBackend.toModel)
}

object CotoGraph {
  def decode(json: CotoGraphJson): IO[CotoGraph] =
    for {
      cotos <- decodeArray(json.cotos)(CotoBackend.toModel)
      cotosRelatedData <- CotosRelatedData.decode(json.cotos_related_data)
    } yield CotoGraph(json, cotos, cotosRelatedData)

  def fetchFromCoto(coto: Id[Coto]): IO[Either[ErrorJson, CotoGraph]] =
    CotoGraphJson.fetchFromCoto(coto).flatMap(_.traverse(CotoGraph.decode))

  def fetchFromCotonoma(
      cotonoma: Id[Cotonoma]
  ): IO[Either[ErrorJson, CotoGraph]] =
    CotoGraphJson.fetchFromCotonoma(cotonoma).flatMap(
      _.traverse(CotoGraph.decode)
    )
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
  ): IO[Either[ErrorJson, CotoGraphJson]] =
    Commands.send(Commands.GraphFromCoto(coto))

  def fetchFromCotonoma(
      cotonoma: Id[Cotonoma]
  ): IO[Either[ErrorJson, CotoGraphJson]] =
    Commands.send(Commands.GraphFromCotonoma(cotonoma))
}
