package cotoami.models

import java.time.Instant

import fui.Cmd
import cotoami.utils.Validation

case class Cotonoma(
    id: Id[Cotonoma],
    nodeId: Id[Node],
    cotoId: Id[Coto],
    name: String,
    createdAtUtcIso: String,
    updatedAtUtcIso: String,
    posts: Int
) extends Entity[Cotonoma] {
  override def equals(that: Any): Boolean =
    that match {
      case that: Cotonoma =>
        (this.id, this.updatedAtUtcIso) == (that.id, that.updatedAtUtcIso)
      case _ => false
    }

  lazy val createdAt: Instant = parseUtcIso(this.createdAtUtcIso)
  lazy val updatedAt: Instant = parseUtcIso(this.updatedAtUtcIso)

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

  import cotoami.backend.{CotoBackend, CotonomaJson, ErrorJson, Paginated}

  def apply(json: CotonomaJson): Cotonoma =
    Cotonoma(
      id = Id(json.uuid),
      nodeId = Id(json.node_id),
      cotoId = Id(json.coto_id),
      name = json.name,
      createdAtUtcIso = json.created_at,
      updatedAtUtcIso = json.updated_at,
      posts = json.posts
    )

  def fetch(id: Id[Cotonoma]): Cmd[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.fetch(id)
      .map(_.map(pair => (Cotonoma(pair._1), CotoBackend.toModel(pair._2))))

  def fetchByName(
      name: String,
      nodeId: Id[Node]
  ): Cmd[Either[ErrorJson, Cotonoma]] =
    CotonomaJson.fetchByName(name, nodeId).map(_.map(Cotonoma(_)))

  def fetchRecent(
      nodeId: Option[Id[Node]],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, Paginated[Cotonoma, _]]] =
    CotonomaJson.fetchRecent(nodeId, pageIndex)
      .map(_.map(Paginated(_, Cotonoma(_: CotonomaJson))))

  def fetchSubs(
      id: Id[Cotonoma],
      pageIndex: Double
  ): Cmd[Either[ErrorJson, Paginated[Cotonoma, _]]] =
    CotonomaJson.fetchSubs(id, pageIndex)
      .map(_.map(Paginated(_, Cotonoma(_: CotonomaJson))))

  def post(
      name: String,
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ): Cmd[Either[ErrorJson, (Cotonoma, Coto)]] =
    CotonomaJson.post(name, location, postTo)
      .map(_.map(pair => (Cotonoma(pair._1), CotoBackend.toModel(pair._2))))
}
