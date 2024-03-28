package cotoami

import trail._
import cotoami.backend.{Cotonoma, Node}

object Route {
  implicit case object NodeIdCodec extends IdCodec[Node]
  implicit case object CotonomaIdCodec extends IdCodec[Cotonoma]

  val index = Root
  val node = Root / "nodes" / Arg[Id[Node]]()
  val cotonoma = Root / "cotonomas" / Arg[Id[Cotonoma]]()
  val cotonomaInNode =
    Root / "nodes" / Arg[Id[Node]]() / "cotonomas" / Arg[Id[Cotonoma]]()
}

class IdCodec[T] extends Codec[Id[T]] {
  override def encode(id: Id[T]): Option[String] = Some(id.uuid)

  // FIXME: Maybe, we should check if the source string is a valid UUID.
  override def decode(s: Option[String]): Option[Id[T]] = s.map(Id(_))
}
