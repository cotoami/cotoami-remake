package cotoami.models

import java.time.Instant

import marubinotto.Validation

case class Ito(
    id: Id[Ito],
    nodeId: Id[Node],
    createdById: Id[Node],
    sourceCotoId: Id[Coto],
    targetCotoId: Id[Coto],
    description: Option[String],
    details: Option[String],
    order: Int,
    createdAtUtcIso: String,
    updatedAtUtcIso: String
) extends Entity[Ito] {
  lazy val createdAt: Instant = parseUtcIso(createdAtUtcIso)
  lazy val updatedAt: Instant = parseUtcIso(updatedAtUtcIso)

  // If two ito objects have the same ID and update-timestamp,
  // they can be regarded as the same ito.
  override def equals(that: Any): Boolean =
    that match {
      case that: Ito =>
        (id, updatedAtUtcIso) == (that.id, that.updatedAtUtcIso)
      case _ => false
    }
}

object Ito {
  final val IconName = "north_east"
  final val NewIconName = "north_east"
  final val PinIconName = "push_pin"

  final val DescriptionMaxLength = 200

  def validateDescription(description: String): Seq[Validation.Error] = {
    val fieldName = "description"
    Vector(
      Validation.nonBlank(fieldName, description),
      Validation.length(fieldName, description, 1, DescriptionMaxLength)
    ).flatten
  }
}

case class Siblings(
    parent: Coto,
    selfNodeId: Id[Node],
    groups: Map[Id[Node], SiblingGroup]
) {
  def mainGroup: Option[SiblingGroup] = groups.get(parent.nodeId)

  def selfGroup: Option[SiblingGroup] =
    groups.get(selfNodeId) match {
      case Some(group) => Option.when(selfNodeId != parent.nodeId)(group)
      case None        => None
    }

  def count: Int = groups.values.map(_.length).sum

  def otherGroups: Seq[SiblingGroup] =
    groups.filter { case (nodeId, _) =>
      nodeId != parent.nodeId && nodeId != selfNodeId
    }.map(_._2).toSeq

  def groupsInOrder: Seq[SiblingGroup] =
    (mainGroup +: otherGroups.map(Some.apply) :+ selfGroup).flatten

  def cotos: Seq[Coto] =
    groups.values.map(_.siblings.map(_._2)).flatten.toSeq

  def fingerprint: String = groupsInOrder.map(_.fingerprint).mkString
}

// Sibling itos and their target cotos from the same source coto and
// belonging to the same node.
case class SiblingGroup(
    parent: Coto,
    nodeId: Id[Node],
    siblings: Seq[(Ito, Coto)]
) {
  def length = siblings.length

  def isEmpty: Boolean = siblings.isEmpty

  def isMain: Boolean = nodeId == parent.nodeId

  val minOrder = siblings.headOption.map(_._1.order).getOrElse(0)
  val maxOrder = siblings.lastOption.map(_._1.order).getOrElse(0)

  def itos: Iterable[Ito] = siblings.map(_._1)

  // Returns each sibling with the previous and next ones.
  def eachWithNeighbors
      : Iterable[(Option[(Ito, Coto)], (Ito, Coto), Option[(Ito, Coto)])] =
    siblings.zipWithIndex.map { case (sibling, index) =>
      (
        siblings.lift(index - 1),
        sibling,
        siblings.lift(index + 1)
      )
    }

  def eachWithOrderContext: Iterable[(Ito, Coto, OrderContext)] =
    eachWithNeighbors.map { case (previous, (ito, coto), next) =>
      (
        ito,
        coto,
        OrderContext(
          min = minOrder,
          max = maxOrder,
          current = ito.order,
          previous = previous.map(_._1.order),
          next = next.map(_._1.order)
        )
      )
    }

  def fingerprint: String = itos.map(_.id.uuid).mkString
}

case class OrderContext(
    min: Int,
    max: Int,
    current: Int,
    previous: Option[Int],
    next: Option[Int]
) {
  def isFirst: Boolean = current == min
  def isLast: Boolean = current == max
}
