package cotoami.models

import scala.util.chaining._
import org.scalajs.dom
import java.time.Instant

import marubinotto.Validation
import marubinotto.libs.unified.Remark
import marubinotto.libs.unified.remarkPlugins.StripMarkdown

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
        val text = Coto.stripMarkdown(content)
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
    mediaBlob: Option[(dom.Blob, String)],
    geolocation: Option[Geolocation],
    dateTimeRange: Option[DateTimeRange],
    isCotonoma: Boolean,
    repostOfId: Option[Id[Coto]],
    repostedInIds: Option[Seq[Id[Cotonoma]]],
    createdAtUtcIso: String,
    updatedAtUtcIso: String
) extends Entity[Coto]
    with CotoContent {

  // If two coto objects have the same ID and update-timestamp,
  // they can be regarded as the same coto.
  override def equals(that: Any): Boolean =
    that match {
      case that: Coto =>
        (id, updatedAtUtcIso) == (that.id, that.updatedAtUtcIso)
      case _ => false
    }

  lazy val mediaUrl: Option[(String, String)] = mediaBlob.map {
    case (blob, mimeType) => (dom.URL.createObjectURL(blob), mimeType)
  }

  def revokeMediaUrl(): Unit = mediaUrl.foreach { case (url, _) =>
    dom.URL.revokeObjectURL(url)
  }

  def geolocated: Boolean = geolocation.isDefined

  def isRepost: Boolean = repostOfId.isDefined

  def toPromote: Coto =
    ((summary, content) match {
      case (Some(summary), _) =>
        copy(summary = Some(summary.take(Cotonoma.NameMaxLength)))
      case (None, Some(content)) => {
        val text = Coto.stripMarkdown(content)
        if (content.length() <= Cotonoma.NameMaxLength)
          copy(summary = Some(text), content = None)
        else
          copy(summary = Some(text.take(Cotonoma.NameMaxLength)))
      }
      case _ => this
    }).copy(isCotonoma = true)

  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
  lazy val updatedAt: Instant = parseUtcIso(updatedAtUtcIso)

  lazy val postedInIds: Seq[Id[Cotonoma]] =
    Seq(postedInId).flatten ++ repostedInIds.getOrElse(Seq.empty)
}

object Coto {
  final val IconName = "text_snippet"
  final val RepostIconName = "repeat"
  final val MarkdownSpecials = """\`*_{}\[\]()#+\-\.!"""

  final val SummaryMaxLength = 200

  private final val stripMarkdown = Remark.remark().use(StripMarkdown)

  def validateSummary(summary: String): Seq[Validation.Error] = {
    val fieldName = "summary"
    Vector(
      Validation.nonBlank(fieldName, summary),
      Validation.length(fieldName, summary, 1, SummaryMaxLength)
    ).flatten
  }

  def validateContent(
      content: String,
      isCotonoma: Boolean
  ): Seq[Validation.Error] = {
    val fieldName = "content"
    if (isCotonoma)
      Seq.empty // the content of cotonoma can be blank
    else
      Vector(
        Validation.nonBlank(fieldName, content)
      ).flatten
  }

  def stripMarkdown(content: String): String =
    Coto.stripMarkdown.processSync(content).toString()
      .pipe(unescapeMarkdown)

  def unescapeMarkdown(text: String): String = {
    val pattern = raw"""\\([$MarkdownSpecials])""".r
    pattern.replaceAllIn(text, _.group(1))
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
