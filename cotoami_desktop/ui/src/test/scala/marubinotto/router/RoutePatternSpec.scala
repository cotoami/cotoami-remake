package marubinotto.router

import org.scalatest.funsuite.AnyFunSuite

class RoutePatternSpec extends AnyFunSuite {
  case class Id(value: String)

  given IdCodec: Codec[Id] with {
    override def encode(value: Id): Option[String] = Some(value.value)
    override def decode(value: Option[String]): Option[Id] = value.map(Id(_))
  }

  private val index = Root
  private val node = Root / "nodes" / Arg[Id]()
  private val nested = Root / "nodes" / Arg[Id]() / "cotonomas" / Arg[Id]()

  test("root route matches only the root path") {
    assert(index.url(()).toOption.get == "/")
    assert(index.unapply("/").contains(()))
    assert(index.unapply("").contains(()))
    assert(index.unapply("/nodes").isEmpty)
  }

  test("single-argument route round-trips") {
    val nodeId = Id("abc-123")

    assert(node.url(nodeId).toOption.get == "/nodes/abc-123")
    assert(node.unapply("/nodes/abc-123").contains(nodeId))
    assert(node.unapply("/nodes").isEmpty)
  }

  test("multi-argument route round-trips") {
    val nodeId = Id("node-1")
    val cotonomaId = Id("cotonoma-9")

    assert(
      nested.url((nodeId, cotonomaId)).toOption.get == "/nodes/node-1/cotonomas/cotonoma-9"
    )
    assert(
      nested.unapply("/nodes/node-1/cotonomas/cotonoma-9").contains(
        (nodeId, cotonomaId)
      )
    )
  }

  test("route arguments are URI encoded") {
    val nodeId = Id("name with/slash")

    assert(node.url(nodeId).toOption.get == "/nodes/name%20with%2Fslash")
    assert(node.unapply("/nodes/name%20with%2Fslash").contains(nodeId))
  }

  private val search = Root & Param[String]("query")

  test("required query distinguishes missing and empty values") {
    assert(search.unapply("/").isEmpty)
    assert(search.unapply("/?query=").contains(""))
    assert(search.unapply("/?query").contains(""))
    assert(search.url("").toOption.get == "/?query=")
  }

  test("queries round-trip reserved characters and Unicode") {
    Seq("日本語", "two words", "+", "&", "/", "#", "a?b=c%20").foreach { value =>
      assert(search.unapply(search.url(value).toOption.get).contains(value))
    }
    assert(search.unapply("/?query=a+b%2Bc").contains("a b+c"))
    assert(search.url("a b+c").toOption.get == "/?query=a+b%2Bc")
  }

  test("first duplicate wins and unknown parameters and fragments are ignored") {
    assert(search.unapply("/?other=1&query=first&query=second#query=ignored").contains("first"))
    assert(Root.unapply("/?query=hello#section").contains(()))
    assert(node.unapply("/nodes/abc?query=hello#section").contains(Id("abc")))
    assert(search.unapply("/other?query=hello").isEmpty)
  }

  test("multiple typed queries combine with path arguments") {
    val route = Root / "work" / Arg[String]() & Param[String]("query") & Param[Int]("page")
    val value = ("id/1", "日本語 + & / #", 2)
    assert(route.unapply(route.url(value).toOption.get).contains(value))
    assert(route.unapply("/work/id?page=2&query=hello").contains(("id", "hello", 2)))
    assert(route.unapply("/work/id?query=hello").isEmpty)
    assert(route.unapply("/work/id?query=hello&page=bad").isEmpty)
    assert(route.unapply("/work/id?query=hello&page=").isEmpty)
  }

  test("query parameter names are encoded and later path composition preserves queries") {
    val route = (Root & Param[String]("a & b")) / "work" / Arg[Int]()
    assert(route.unapply(route.url(("value", 3)).toOption.get).contains(("value", 3)))
  }

  test("malformed path encoding does not throw") {
    assert(node.unapply("/nodes/%ZZ").isEmpty)
  }

  test("six mixed route arguments remain a flat typed tuple") {
    val inferred =
      Root / Arg[String]() / Arg[Int]() / Arg[Long]() &
        Param[Boolean]("enabled") & Param[Double]("score") & Param[String]("query")
    val route: RoutePattern[(String, Int, Long, Boolean, Double, String)] = inferred
    val value = ("item/1", 2, 3L, true, 4.5, "日本語 & +")
    assert(route.unapply(route.url(value).toOption.get).contains(value))
  }

  test("tuple appenders preserve types and split the appended value") {
    val empty = summon[Appender[EmptyTuple, Int, Tuple1[Int]]]
    assert(empty.split(empty.append(EmptyTuple, 1)) == (EmptyTuple, 1))
    val single = summon[Appender[Tuple1[String], Int, (String, Int)]]
    assert(single.split(single.append(Tuple1("a"), 1)) == (Tuple1("a"), 1))
    val tuple = summon[Appender[(String, Int), (Boolean, Long), (String, Int, (Boolean, Long))]]
    assert(tuple.split(tuple.append(("a", 1), (true, 2L))) == (("a", 1), (true, 2L)))
  }

  test("encoding rejection distinguishes path arguments and query parameters") {
    given Codec[Unit] with {
      def encode(value: Unit): Option[String] = None
      def decode(value: Option[String]): Option[Unit] = None
    }
    assert((Root / Arg[Unit]()).url(()) == Left(RouteError.PathArgumentRejected()))
    assert((Root & Param[Unit]("rejected")).url(()) ==
      Left(RouteError.QueryParameterRejected("rejected")))
  }

  test("codec exceptions and malformed Unicode are returned as encoding failures") {
    val error = new IllegalArgumentException("codec failure")
    given Codec[Unit] with {
      def encode(value: Unit): Option[String] = throw error
      def decode(value: Option[String]): Option[Unit] = None
    }
    assert((Root / Arg[Unit]()).url(()) == Left(RouteError.EncodingFailed(error)))
    assert((Root & Param[Unit]("bad")).url(()) == Left(RouteError.EncodingFailed(error)))
    assert((Root / Arg[String]()).url("\uD800").left.toOption.exists {
      case RouteError.EncodingFailed(_) => true
      case _ => false
    })
    assert((Root / "\uD800").url(()).isLeft)
  }

}
