package cotoami.backend

import scala.scalajs.js
import fui.Cmd

import cotoami.models.{Coto, Cotonoma, Id}

case class CotoGraph(json: CotoGraphJson) {
  def rootCotoId: Id[Coto] = Id(this.json.root_coto_id)
  def rootCotonoma: Option[Cotonoma] =
    Nullable.toOption(this.json.root_cotonoma).map(Cotonoma(_))
  def cotos: js.Array[Coto] = this.json.cotos.map(CotoBackend.toModel(_))
  def cotosRelatedData: CotosRelatedData =
    CotosRelatedData(this.json.cotos_related_data)
  def links: js.Array[Link] = this.json.links.map(Link(_))

  def debug: String = {
    val s = new StringBuilder
    s ++= s"rootCotoId: ${this.rootCotoId}"
    s ++= s", rootCotonoma: ${rootCotonoma.toString()}"
    s ++= s", cotos: ${this.cotos.size}"
    s ++= s", cotosRelatedData: ${this.cotosRelatedData.debug}"
    s ++= s", links: ${this.links.size}"
    s.result()
  }
}

object CotoGraph {
  def fetchFromCoto(coto: Id[Coto]): Cmd[Either[ErrorJson, CotoGraph]] =
    CotoGraphJson.fetchFromCoto(coto).map(_.map(CotoGraph(_)))

  def fetchFromCotonoma(
      cotonoma: Id[Cotonoma]
  ): Cmd[Either[ErrorJson, CotoGraph]] =
    CotoGraphJson.fetchFromCotonoma(cotonoma).map(_.map(CotoGraph(_)))
}

@js.native
trait CotoGraphJson extends js.Object {
  val root_coto_id: String = js.native
  val root_cotonoma: Nullable[CotonomaJson] = js.native
  val cotos: js.Array[CotoJson] = js.native
  val cotos_related_data: CotosRelatedDataJson = js.native
  val links: js.Array[LinkJson] = js.native
}

object CotoGraphJson {
  def fetchFromCoto(coto: Id[Coto]): Cmd[Either[ErrorJson, CotoGraphJson]] =
    Commands.send(Commands.GraphFromCoto(coto))

  def fetchFromCotonoma(
      cotonoma: Id[Cotonoma]
  ): Cmd[Either[ErrorJson, CotoGraphJson]] =
    Commands.send(Commands.GraphFromCotonoma(cotonoma))
}
