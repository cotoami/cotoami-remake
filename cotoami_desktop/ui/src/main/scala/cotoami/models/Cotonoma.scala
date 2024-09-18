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
}
