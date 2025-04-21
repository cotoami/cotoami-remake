package cotoami.models

import java.time.Instant
import scala.collection.immutable.TreeMap
import com.softwaremill.quicklens._

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
  final val ConnectIconName = "add_link"
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

// Outgoing itos grouped by belonging nodes.
// Each grouped itos are sorted in TreeMap by Ito.order.
case class OutgoingItos(byNode: Map[Id[Node], TreeMap[Int, Ito]] = Map.empty) {
  def isEmpty: Boolean = byNode.isEmpty

  def all: Iterable[Ito] = byNode.values.map(_.values).flatten

  def group(id: Id[Node]): Iterable[Ito] =
    byNode.get(id).map(_.values).getOrElse(Seq.empty)

  def hasDuplicateOrder(ito: Ito): Boolean =
    byNode.get(ito.nodeId).map(_.contains(ito.order)).getOrElse(false)

  def put(ito: Ito): OutgoingItos =
    this.modify(_.byNode.atOrElse(ito.nodeId, TreeMap(ito.order -> ito))).using(
      _.filterNot(_._2.id == ito.id) // remove old version
        .updated(ito.order, ito)
    )

  def delete(ito: Ito): OutgoingItos =
    this
      .modify(_.byNode.index(ito.nodeId)).using(
        _.filterNot(_._2.id == ito.id)
      )
      .modify(_.byNode).using(_.filterNot(_._2.isEmpty))
}

// Sibling itos and their target cotos from the same source coto and
// belonging to the same node.
case class SiblingGroup(sorted: Seq[(Ito, Coto)]) {
  def length = sorted.length

  def isEmpty: Boolean = sorted.isEmpty

  val minOrder = sorted.headOption.map(_._1.order).getOrElse(0)
  val maxOrder = sorted.lastOption.map(_._1.order).getOrElse(0)

  def itos: Iterable[Ito] = sorted.map(_._1)

  // Returns each sibling with the previous and next ones.
  def eachWithNeighbors
      : Iterable[(Option[(Ito, Coto)], (Ito, Coto), Option[(Ito, Coto)])] =
    sorted.zipWithIndex.map { case (sibling, index) =>
      (
        sorted.lift(index - 1),
        sibling,
        sorted.lift(index + 1)
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

object SiblingGroup {
  def empty: SiblingGroup = SiblingGroup(Seq.empty)
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
