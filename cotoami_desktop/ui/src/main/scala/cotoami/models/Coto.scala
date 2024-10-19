package cotoami.models

import org.scalajs.dom
import java.time.Instant

import cotoami.utils.{Remark, StripMarkdown, Validation}

trait CotoContent {
  def content: Option[String]
  def summary: Option[String]
  def mediaUrl: Option[(String, String)]
  def geolocation: Option[Geolocation]
  def dateTimeRange: Option[DateTimeRange]
  def isCotonoma: Boolean

  def nameAsCotonoma: Option[String] =
    if (isCotonoma)
      summary.orElse(content)
    else
      None

  lazy val abbreviate: Option[String] =
    summary.orElse(
      content.map(content => {
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
    mediaContent: Option[(dom.Blob, String)],
    geolocation: Option[Geolocation],
    dateTimeRange: Option[DateTimeRange],
    isCotonoma: Boolean,
    repostOfId: Option[Id[Coto]],
    repostedInIds: Option[Seq[Id[Cotonoma]]],
    createdAtUtcIso: String,
    updatedAtUtcIso: String,
    outgoingLinks: Int,
    posted: Boolean
) extends Entity[Coto]
    with CotoContent {

  // If two coto objects have the same ID and update timestamp,
  // they can be regarded as the same coto.
  override def equals(that: Any): Boolean =
    that match {
      case that: Coto =>
        (id, updatedAtUtcIso) == (that.id, that.updatedAtUtcIso)
      case _ => false
    }

  lazy val mediaUrl: Option[(String, String)] = mediaContent.map {
    case (content, mimeType) => (dom.URL.createObjectURL(content), mimeType)
  }

  def revokeMediaUrl(): Unit = mediaUrl.foreach { case (url, _) =>
    dom.URL.revokeObjectURL(url)
  }

  def geolocated: Boolean = geolocation.isDefined

  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
  lazy val updatedAt: Instant = parseUtcIso(updatedAtUtcIso)

  lazy val postedInIds: Seq[Id[Cotonoma]] =
    Seq(postedInId).flatten ++
      repostedInIds.getOrElse(Seq.empty)
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

  def centerOrBoundsOf(cotos: Seq[Coto]): Option[CenterOrBounds] =
    cotos match {
      case Seq()     => None
      case Seq(coto) => coto.geolocation.map(Left(_))
      case cotos => {
        val locations = cotos.flatMap(_.geolocation)
        val bounds = GeoBounds(
          southwest = Geolocation(
            longitude = locations.map(_.longitude).min,
            latitude = locations.map(_.latitude).min
          ),
          northeast = Geolocation(
            longitude = locations.map(_.longitude).max,
            latitude = locations.map(_.latitude).max
          )
        )
        Some(Right(bounds))
      }
    }
}
