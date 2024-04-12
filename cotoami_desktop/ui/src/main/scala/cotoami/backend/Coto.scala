package cotoami.backend

import scala.scalajs.js
import java.time.Instant

case class Coto(json: CotoJson) {
  def id: Id[Coto] = Id(this.json.uuid)
  def nodeId: Id[Node] = Id(this.json.node_id)
  def postedInId: Option[Id[Cotonoma]] =
    Option(this.json.posted_in_id).map(Id(_))
  def postedById: Id[Node] = Id(this.json.posted_by_id)
  def content: Option[String] = Option(this.json.content)
  def summary: Option[String] = Option(this.json.summary)
  def isCotonoma: Boolean = this.json.is_cotonoma
  def repostOfId: Option[Id[Coto]] = Option(this.json.repost_of_id).map(Id(_))
  def repostedInIds: Option[Seq[Id[Cotonoma]]] =
    Option(this.json.reposted_in_ids).map(_.map(Id[Cotonoma](_)).toSeq)
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)
  lazy val updatedAt: Instant = parseJsonDateTime(this.json.updated_at)
  def outgoingLinks: Int = this.json.outgoing_links

  def postedInIds: Seq[Id[Cotonoma]] =
    Seq(this.postedInId).flatten ++
      this.repostedInIds.getOrElse(Seq.empty)

  def nameAsCotonoma: Option[String] =
    if (this.isCotonoma)
      this.summary.orElse(this.content)
    else
      None
}

object Coto {
  def toMap(jsons: js.Array[CotoJson]): Map[Id[Coto], Coto] =
    jsons.map(json => (Id[Coto](json.uuid), Coto(json))).toMap
}

@js.native
trait CotoJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val posted_in_id: String = js.native
  val posted_by_id: String = js.native
  val content: String = js.native
  val summary: String = js.native
  val is_cotonoma: Boolean = js.native
  val repost_of_id: String = js.native
  val reposted_in_ids: js.Array[String] = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
  val outgoing_links: Int = js.native
}

@js.native
trait PaginatedCotosJson extends js.Object {
  val page: Paginated[CotoJson] = js.native
  val posted_in: js.Array[CotonomaJson] = js.native
  val as_cotonomas: js.Array[CotonomaJson] = js.native
  val originals: js.Array[CotoJson] = js.native
}

object PaginatedCotosJson {
  def debug(cotos: PaginatedCotosJson): String = {
    val s = new StringBuilder
    s ++= s"cotos: {page_index: ${cotos.page.page_index}"
    s ++= s", page_size: ${cotos.page.page_size}"
    s ++= s", total_rows: ${cotos.page.total_rows}}"
    s ++= s", posted_in: ${cotos.posted_in.size}"
    s ++= s", as_cotonomas: ${cotos.as_cotonomas.size}"
    s ++= s", originals: ${cotos.originals.size}"
    s.result()
  }
}
