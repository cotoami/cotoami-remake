package cotoami.backend

import scala.scalajs.js
import java.time.Instant

import cotoami.utils.{Remark, StripMarkdown, Validation}

trait CotoContent {
  def content: Option[String]
  def summary: Option[String]
  def isCotonoma: Boolean

  def nameAsCotonoma: Option[String] =
    if (this.isCotonoma)
      this.summary.orElse(this.content)
    else
      None

  lazy val abbreviate: Option[String] =
    this.summary.orElse(
      this.content.map(content => {
        val text = Coto.stripMarkdown.processSync(content).toString()
        if (text.size > Cotonoma.NameMaxLength)
          s"${text.substring(0, Cotonoma.NameMaxLength)}…"
        else
          text
      })
    )
}

case class Coto(json: CotoJson) extends Entity[Coto] with CotoContent {
  override def id: Id[Coto] = Id(this.json.uuid)
  def nodeId: Id[Node] = Id(this.json.node_id)
  def postedInId: Option[Id[Cotonoma]] =
    Option(this.json.posted_in_id).map(Id(_))
  def postedById: Id[Node] = Id(this.json.posted_by_id)
  override def content: Option[String] = Option(this.json.content)
  override def summary: Option[String] = Option(this.json.summary)
  override def isCotonoma: Boolean = this.json.is_cotonoma
  def repostOfId: Option[Id[Coto]] = Option(this.json.repost_of_id).map(Id(_))
  def repostedInIds: Option[Seq[Id[Cotonoma]]] =
    Option(this.json.reposted_in_ids).map(_.map(Id[Cotonoma](_)).toSeq)
  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)
  lazy val updatedAt: Instant = parseJsonDateTime(this.json.updated_at)
  def outgoingLinks: Int = this.json.outgoing_links

  def postedInIds: Seq[Id[Cotonoma]] =
    Seq(this.postedInId).flatten ++
      this.repostedInIds.getOrElse(Seq.empty)
}

object Coto {
  val SummaryMaxLength = 200
  val stripMarkdown = Remark.remark().use(StripMarkdown)

  def validateSummary(summary: String): Seq[Validation.Error] = {
    val fieldName = "summary"
    Vector(
      Validation.nonBlank(fieldName, summary),
      Validation.length(fieldName, summary, 1, SummaryMaxLength)
    ).flatten
  }

  def validateContent(content: String): Seq[Validation.Error] = {
    val fieldName = "content"
    Vector(
      Validation.nonBlank(fieldName, content)
    ).flatten
  }
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
