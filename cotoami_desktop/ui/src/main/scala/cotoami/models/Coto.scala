package cotoami.models

import org.scalajs.dom
import java.time.Instant

import cotoami.utils.{Remark, StripMarkdown, Validation}

trait CotoContent {
  def content: Option[String]
  def summary: Option[String]
  def mediaUrl: Option[(String, String)]
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
    mediaContent: Option[(dom.Blob, String)],
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

  lazy val mediaUrl: Option[(String, String)] = this.mediaContent.map {
    case (content, mimeType) => (dom.URL.createObjectURL(content), mimeType)
  }

  def revokeMediaUrl(): Unit = this.mediaUrl.foreach { case (url, _) =>
    dom.URL.revokeObjectURL(url)
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

  def geolocationOf(cotos: Seq[Coto]): Option[Either[Geolocation, GeoBounds]] =
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
