package cotoami.backend

import scala.scalajs.js
import java.time.Instant

import fui.Cmd
import cotoami.utils.Validation

case class Cotonoma(json: CotonomaJson) extends Entity[Cotonoma] {
  override def id: Id[Cotonoma] = Id(this.json.uuid)
  def nodeId: Id[Node] = Id(this.json.node_id)
  def cotoId: Id[Coto] = Id(this.json.coto_id)
  def name: String = this.json.name
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)
  lazy val updatedAt: Instant = parseJsonDateTime(this.json.updated_at)
  def posts: Int = this.json.posts

  def abbreviateName(length: Int): String =
    if (this.name.size > length)
      s"${this.name.substring(0, length)}…"
    else
      this.name
}

object Cotonoma {
  final val NameMaxLength = 50

  def validateName(name: String): Seq[Validation.Error] = {
    val fieldName = "name"
    Seq(
      Validation.nonBlank(fieldName, name),
      Validation.length(fieldName, name, 1, NameMaxLength)
    ).flatten
  }

  def fetch(id: Id[Cotonoma]): Cmd[Either[ErrorJson, Cotonoma]] =
    CotonomaJson.fetch(id).map(_.map(Cotonoma(_)))

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, Paginated[Cotonoma, _]]] =
    CotonomaJson.fetchRecent(nodeId, pageIndex)
      .map(_.map(Paginated(_, Cotonoma(_))))
}

@js.native
trait CotonomaJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val coto_id: String = js.native
  val name: String = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
  val posts: Int = js.native
}

object CotonomaJson {
  def fetch(id: Id[Cotonoma]): Cmd[Either[ErrorJson, CotonomaJson]] =
    Commands.send(Commands.Cotonoma(id))

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, PaginatedJson[CotonomaJson]]] =
    Commands.send(Commands.RecentCotonomas(nodeId, pageIndex))
}
