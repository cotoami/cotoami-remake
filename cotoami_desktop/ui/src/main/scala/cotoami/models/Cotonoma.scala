package cotoami.models

import java.time.Instant

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
        (id, updatedAtUtcIso) == (that.id, that.updatedAtUtcIso)
      case _ => false
    }

  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
  lazy val updatedAt: Instant = parseUtcIso(updatedAtUtcIso)

  def abbreviateName(length: Int): String =
    if (name.size > length)
      s"${name.substring(0, length)}…"
    else
      name
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
}
