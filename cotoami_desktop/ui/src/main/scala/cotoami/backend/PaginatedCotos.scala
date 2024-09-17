package cotoami.backend

import scala.scalajs.js
import fui.Cmd

import cotoami.models.{Id, Node}

case class PaginatedCotos(json: PaginatedCotosJson) {
  def page: Paginated[Coto, _] = Paginated(this.json.page, Coto(_, false))
  def relatedData: CotosRelatedData = CotosRelatedData(this.json.related_data)
  def outgoingLinks: js.Array[Link] = this.json.outgoing_links.map(Link(_))

  def debug: String = {
    val s = new StringBuilder
    s ++= s"cotos: {${this.page.debug}}"
    s ++= s", relatedData: {${this.relatedData.debug}}"
    s ++= s", outgoingLinks: {${this.outgoingLinks.size}}"
    s.result()
  }
}

object PaginatedCotos {
  def fetchRecent(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, PaginatedCotos]] =
    PaginatedCotosJson.fetchRecent(nodeId, cotonomaId, pageIndex)
      .map(_.map(PaginatedCotos(_)))

  def search(
      query: String,
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, PaginatedCotos]] =
    PaginatedCotosJson.search(query, nodeId, cotonomaId, pageIndex)
      .map(_.map(PaginatedCotos(_)))
}

@js.native
trait PaginatedCotosJson extends js.Object {
  val page: PaginatedJson[CotoJson] = js.native
  val related_data: CotosRelatedDataJson = js.native
  val outgoing_links: js.Array[LinkJson] = js.native
}

object PaginatedCotosJson {
  def fetchRecent(
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, PaginatedCotosJson]] =
    Commands.send(Commands.RecentCotos(nodeId, cotonomaId, pageIndex))

  def search(
      query: String,
      nodeId: Option[Id[Node]],
      cotonomaId: Option[Id[Cotonoma]],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, PaginatedCotosJson]] =
    Commands.send(Commands.SearchCotos(query, nodeId, cotonomaId, pageIndex))
}
