package cotoami

import trail._
import cotoami.backend.Node

object Route {
  implicit case object NodeIdCodec extends IdCodec[Node]

  val index = Root
  val node = Root / "nodes" / Arg[Id[Node]]()
}

class IdCodec[T] extends Codec[Id[T]] {
  override def encode(id: Id[T]): Option[String] = Some(id.uuid)

  // FIXME: Maybe, we should check if the source string is a valid UUID.
  override def decode(s: Option[String]): Option[Id[T]] = s.map(Id(_))
}
