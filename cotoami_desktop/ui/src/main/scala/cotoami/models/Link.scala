package cotoami.models

import scala.math.Ordering
import java.time.Instant

import cotoami.utils.Validation

case class Link(
    id: Id[Link],
    nodeId: Id[Node],
    createdById: Id[Node],
    sourceCotoId: Id[Coto],
    targetCotoId: Id[Coto],
    linkingPhrase: Option[String],
    details: Option[String],
    order: Int,
    createdAtUtcIso: String,
    updatedAtUtcIso: String
) extends Entity[Link] {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
  lazy val updatedAt: Instant = parseUtcIso(updatedAtUtcIso)

  // If two link objects have the same ID and update-timestamp,
  // they can be regarded as the same link.
  override def equals(that: Any): Boolean =
    that match {
      case that: Link =>
        (id, updatedAtUtcIso) == (that.id, that.updatedAtUtcIso)
      case _ => false
    }
}

object Link {
  implicit val ordering: Ordering[Link] =
    Ordering.fromLessThan[Link](_.order < _.order)

  final val LinkingPhraseMaxLength = 200

  def validateLinkingPhrase(linkingPhrase: String): Seq[Validation.Error] = {
    val fieldName = "linking phrase"
    Vector(
      Validation.nonBlank(fieldName, linkingPhrase),
      Validation.length(fieldName, linkingPhrase, 1, LinkingPhraseMaxLength)
    ).flatten
  }
}

case class Siblings(sorted: Seq[(Link, Coto)]) {
  def length = sorted.length

  val minOrder = sorted.headOption.map(_._1.order).getOrElse(0)
  val maxOrder = sorted.lastOption.map(_._1.order).getOrElse(0)

  // Returns each sibling with the previous and next ones.
  def window
      : Iterable[(Option[(Link, Coto)], (Link, Coto), Option[(Link, Coto)])] =
    sorted.zipWithIndex.map { case (sibling, index) =>
      (
        sorted.lift(index - 1),
        sibling,
        sorted.lift(index + 1)
      )
    }
}

object Siblings {
  def empty: Siblings = Siblings(Seq.empty)
}
