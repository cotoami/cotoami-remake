package cotoami.backend

import scala.scalajs.js
import java.time.Instant

import fui.Cmd
import cotoami.utils.{Remark, StripMarkdown, Validation}
import cotoami.models.Geolocation

trait CotoContent {
  def content: Option[String]
  def summary: Option[String]
  def mediaContent: Option[(String, String)]
  def geolocation: Option[Geolocation]
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

case class Coto(json: CotoJson, posted: Boolean = false)
    extends Entity[Coto]
    with CotoContent {
  override def id: Id[Coto] = Id(this.json.uuid)
  def nodeId: Id[Node] = Id(this.json.node_id)

  def postedInId: Option[Id[Cotonoma]] =
    Nullable.toOption(this.json.posted_in_id).map(Id(_))

  def postedById: Id[Node] = Id(this.json.posted_by_id)

  override def content: Option[String] = Nullable.toOption(this.json.content)

  override def summary: Option[String] = Nullable.toOption(this.json.summary)

  override def mediaContent: Option[(String, String)] = (
    Nullable.toOption(this.json.media_content),
    Nullable.toOption(this.json.media_type)
  ) match {
    case (Some(content), Some(mediaType)) => Some((content, mediaType))
    case _                                => None
  }

  override lazy val geolocation: Option[Geolocation] =
    (
      Nullable.toOption(this.json.longitude),
      Nullable.toOption(this.json.latitude)
    ) match {
      case (Some(longitude), Some(latitude)) =>
        Some(Geolocation.fromLngLat((longitude, latitude)))
      case _ => None
    }

  override def isCotonoma: Boolean = this.json.is_cotonoma

  def repostOfId: Option[Id[Coto]] =
    Nullable.toOption(this.json.repost_of_id).map(Id(_))

  def repostedInIds: Option[Seq[Id[Cotonoma]]] =
    Nullable.toOption(this.json.reposted_in_ids)
      .map(_.map(Id[Cotonoma](_)).toSeq)

  lazy val createdAt: Instant = parseJsonDateTime(this.json.created_at)
  lazy val updatedAt: Instant = parseJsonDateTime(this.json.updated_at)

  def outgoingLinks: Int = this.json.outgoing_links

  def postedInIds: Seq[Id[Cotonoma]] =
    Seq(this.postedInId).flatten ++
      this.repostedInIds.getOrElse(Seq.empty)
}

object Coto {
  final val SummaryMaxLength = 200
  final val stripMarkdown = Remark.remark().use(StripMarkdown)

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

  def post(
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ): Cmd[Either[ErrorJson, Coto]] =
    CotoJson.post(content, summary, mediaContent, location, postTo)
      .map(_.map(Coto(_)))
}

@js.native
trait CotoJson extends js.Object {
  val uuid: String = js.native
  val node_id: String = js.native
  val posted_in_id: Nullable[String] = js.native
  val posted_by_id: String = js.native
  val content: Nullable[String] = js.native
  val summary: Nullable[String] = js.native
  val media_content: Nullable[String] = js.native
  val media_type: Nullable[String] = js.native
  val is_cotonoma: Boolean = js.native
  val longitude: Nullable[Double] = js.native
  val latitude: Nullable[Double] = js.native
  val repost_of_id: Nullable[String] = js.native
  val reposted_in_ids: Nullable[js.Array[String]] = js.native
  val created_at: String = js.native
  val updated_at: String = js.native
  val outgoing_links: Int = js.native
}

object CotoJson {
  def post(
      content: String,
      summary: Option[String],
      mediaContent: Option[(String, String)],
      location: Option[Geolocation],
      postTo: Id[Cotonoma]
  ): Cmd[Either[ErrorJson, CotoJson]] =
    Commands.send(
      Commands.PostCoto(content, summary, mediaContent, location, postTo)
    )
}
