package cotoami

import marubinotto.router._
import cotoami.models.{Cotonoma, Id, Node}

object Route {
  given nodeIdCodec: IdCodec[Node] = new IdCodec[Node]
  given cotonomaIdCodec: IdCodec[Cotonoma] = new IdCodec[Cotonoma]

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
