package cotoami.models

import scala.math.Ordering
import java.time.Instant

case class Link(
    id: Id[Link],
    nodeId: Id[Node],
    createdInId: Option[Id[Cotonoma]],
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
}

object Link {
  implicit val ordering: Ordering[Link] =
    Ordering.fromLessThan[Link](_.order < _.order)
}
