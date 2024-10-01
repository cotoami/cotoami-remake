package cotoami.backend

import scala.scalajs.js
import fui.Cmd

import cotoami.models.{Coto, Cotonoma, Id, Link, Node, Page}

case class CotosPage(json: CotosPageJson) {
  def page: Page[Coto] =
    PageBackend.toModel(this.json.page, CotoBackend.toModel(_, false))
  def relatedData: CotosRelatedData = CotosRelatedData(this.json.related_data)
  def outgoingLinks: js.Array[Link] =
    this.json.outgoing_links.map(LinkBackend.toModel(_))
}

object CotosPage {
  def fetchRecent(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, CotosPage]] =
    CotosPageJson.fetchRecent(nodeId, cotonomaId, pageIndex)
      .map(_.map(CotosPage(_)))

  def search(
      query: String,
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, CotosPage]] =
    CotosPageJson.search(query, nodeId, cotonomaId, pageIndex)
      .map(_.map(CotosPage(_)))
}

@js.native
trait CotosPageJson extends js.Object {
  val page: PageJson[CotoJson] = js.native
  val related_data: CotosRelatedDataJson = js.native
  val outgoing_links: js.Array[LinkJson] = js.native
}

object CotosPageJson {
  def fetchRecent(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, CotosPageJson]] =
    Commands.send(Commands.RecentCotos(nodeId, cotonomaId, pageIndex))

  def search(
      query: String,
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd.One[Either[ErrorJson, CotosPageJson]] =
    Commands.send(Commands.SearchCotos(query, nodeId, cotonomaId, pageIndex))
}
