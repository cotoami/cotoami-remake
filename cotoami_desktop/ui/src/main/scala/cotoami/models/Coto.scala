package cotoami.models

import java.time.Instant

import fui.Cmd
import cotoami.utils.{Remark, StripMarkdown, Validation}

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

case class Coto(
    id: Id[Coto],
    nodeId: Id[Node],
    postedInId: Option[Id[Cotonoma]],
    postedById: Id[Node],
    content: Option[String],
    summary: Option[String],
    mediaContent: Option[(String, String)],
    geolocation: Option[Geolocation],
    isCotonoma: Boolean,
    repostOfId: Option[Id[Coto]],
    repostedInIds: Option[Seq[Id[Cotonoma]]],
    createdAtUtcIso: String,
    updatedAtUtcIso: String,
    outgoingLinks: Int,
    posted: Boolean
) extends Entity[Coto]
    with CotoContent {
  override def equals(that: Any): Boolean =
    that match {
      case that: Coto =>
        (this.id, this.updatedAtUtcIso) == (that.id, that.updatedAtUtcIso)
      case _ => false
    }

  def geolocated: Boolean = this.geolocation.isDefined

  lazy val createdAt: Instant = parseUtcIso(this.createdAtUtcIso)
  lazy val updatedAt: Instant = parseUtcIso(this.updatedAtUtcIso)

  lazy val postedInIds: Seq[Id[Cotonoma]] =
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

  import cotoami.backend.{CotoJson, ErrorJson, Nullable}

  def apply(json: CotoJson, posted: Boolean = false): Coto =
    Coto(
      id = Id(json.uuid),
      nodeId = Id(json.node_id),
      postedInId = Nullable.toOption(json.posted_in_id).map(Id(_)),
      postedById = Id(json.posted_by_id),
      content = Nullable.toOption(json.content),
      summary = Nullable.toOption(json.summary),
      mediaContent = (
        Nullable.toOption(json.media_content),
        Nullable.toOption(json.media_type)
      ) match {
        case (Some(content), Some(mediaType)) => Some((content, mediaType))
        case _                                => None
      },
      geolocation = (
        Nullable.toOption(json.longitude),
        Nullable.toOption(json.latitude)
      ) match {
        case (Some(longitude), Some(latitude)) =>
          Some(Geolocation.fromLngLat((longitude, latitude)))
        case _ => None
      },
      isCotonoma = json.is_cotonoma,
      repostOfId = Nullable.toOption(json.repost_of_id).map(Id(_)),
      repostedInIds = Nullable.toOption(json.reposted_in_ids)
        .map(_.map(Id[Cotonoma](_)).toSeq),
      createdAtUtcIso = json.created_at,
      updatedAtUtcIso = json.updated_at,
      outgoingLinks = json.outgoing_links,
      posted = posted
    )

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
