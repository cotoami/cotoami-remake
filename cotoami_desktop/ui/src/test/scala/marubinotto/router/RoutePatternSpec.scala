package marubinotto.router

import org.scalatest.funsuite.AnyFunSuite

class RoutePatternSpec extends AnyFunSuite {
  case class Id(value: String)

  implicit object IdCodec extends Codec[Id] {
    override def encode(value: Id): Option[String] = Some(value.value)
    override def decode(value: Option[String]): Option[Id] = value.map(Id(_))
  }

  private val index = Root
  private val node = Root / "nodes" / Arg[Id]()
  private val nested = Root / "nodes" / Arg[Id]() / "cotonomas" / Arg[Id]()

  test("root route matches only the root path") {
    assert(index.url(()) == "/")
    assert(index.unapply("/").contains(()))
    assert(index.unapply("").contains(()))
    assert(index.unapply("/nodes").isEmpty)
  }

  test("single-argument route round-trips") {
    val nodeId = Id("abc-123")

    assert(node.url(nodeId) == "/nodes/abc-123")
    assert(node.unapply("/nodes/abc-123").contains(nodeId))
    assert(node.unapply("/nodes").isEmpty)
  }

  test("multi-argument route round-trips") {
    val nodeId = Id("node-1")
    val cotonomaId = Id("cotonoma-9")

    assert(
      nested.url((nodeId, cotonomaId)) == "/nodes/node-1/cotonomas/cotonoma-9"
    )
    assert(
      nested.unapply("/nodes/node-1/cotonomas/cotonoma-9").contains(
        (nodeId, cotonomaId)
      )
    )
  }

  test("route arguments are URI encoded") {
    val nodeId = Id("name with/slash")

    assert(node.url(nodeId) == "/nodes/name%20with%2Fslash")
    assert(node.unapply("/nodes/name%20with%2Fslash").contains(nodeId))
  }
}
