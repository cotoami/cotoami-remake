package cotoami.backend

import scala.math.Ordering
import scala.scalajs.js
import java.time.Instant

import cotoami.models.{Entity, Id, Node}

case class Link(json: LinkJson) extends Entity[Link] {
  override def id: Id[Link] = Id(this.json.uuid)
  def nodeId: Id[Node] = Id(this.json.node_id)
  def createdInId: Option[Id[Cotonoma]] =
    Nullable.toOption(this.json.created_in_id).map(Id(_))
  def createdById: Id[Node] = Id(this.json.created_by_id)
  def sourceCotoId: Id[Coto] = Id(this.json.source_coto_id)
  def targetCotoId: Id[Coto] = Id(this.json.target_coto_id)
  def linkingPhrase: Option[String] =
    Nullable.toOption(this.json.linking_phrase)
  def details: Option[String] = Nullable.toOption(this.json.details)
  def order: Int = this.json.order
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)
  lazy val updatedAt: Instant = parseJsonDateTime(this.json.updated_at)
}

object Link {
  implicit val ordering: Ordering[Link] =
    Ordering.fromLessThan[Link](_.order < _.order)
}

@js.native
trait LinkJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val created_in_id: Nullable[String] = js.native
  val created_by_id: String = js.native
  val source_coto_id: String = js.native
  val target_coto_id: String = js.native
  val linking_phrase: Nullable[String] = js.native
  val details: Nullable[String] = js.native
  val order: Int = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
}
