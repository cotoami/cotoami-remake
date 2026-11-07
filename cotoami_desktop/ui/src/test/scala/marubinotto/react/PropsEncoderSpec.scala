package marubinotto.react

import org.scalatest.funsuite.AnyFunSuite
import scala.scalajs.js

class PropsEncoderSpec extends AnyFunSuite {
  case class Nested(label: String) derives PropsEncoder
  case class Props(
      name: String,
      nested: Nested,
      items: Seq[String],
      omitted: Option[String],
      undefined: js.UndefOr[String],
      nullable: Nullable[String],
      obj: js.Object,
      node: ReactNode,
      callback: (String, Int) => String
  ) derives PropsEncoder

  test(
    "derive external props with explicit omission, null, nested values and callbacks"
  ) {
    val obj = js.Dynamic.literal(marker = true)
    val node = html.div("content")
    val encoded = summon[PropsEncoder[Props]].encode(
      Props(
        "name",
        Nested("nested"),
        Seq("a", "b"),
        None,
        js.undefined,
        Nullable.Null,
        obj,
        node,
        (s, n) => s + n
      )
    ).asInstanceOf[js.Dynamic]
    assert(encoded.name.asInstanceOf[String] == "name")
    assert(encoded.nested.label.asInstanceOf[String] == "nested")
    assert(encoded.items.asInstanceOf[js.Array[String]].toSeq == Seq("a", "b"))
    assert(!js.Object.hasProperty(encoded.asInstanceOf[js.Object], "omitted"))
    assert(!js.Object.hasProperty(encoded.asInstanceOf[js.Object], "undefined"))
    assert(encoded.nullable == null)
    assert(encoded.obj.asInstanceOf[js.Object] eq obj)
    assert(encoded.node.`type`.asInstanceOf[String] == "div")
    assert(
      encoded.node.asInstanceOf[js.Object] eq ReactNode.toJs(node).asInstanceOf[
        js.Object
      ]
    )
    assert(
      encoded.callback.asInstanceOf[js.Function2[String, Int, String]](
        "a",
        3
      ) == "a3"
    )
  }
  test("UndefOr composes with nullable and nested props") {
    case class OptionalProps(
        value: js.UndefOr[Nullable[String]],
        nested: js.UndefOr[Nested]
    ) derives PropsEncoder
    val encoder = summon[PropsEncoder[OptionalProps]]
    val omitted = encoder.encode(OptionalProps(js.undefined, js.undefined))
    assert(!js.Object.hasProperty(omitted, "value"))
    assert(!js.Object.hasProperty(omitted, "nested"))
    val explicitNull = encoder.encode(
      OptionalProps(Nullable.Null, js.undefined)
    ).asInstanceOf[js.Dynamic]
    assert(js.Object.hasProperty(explicitNull.asInstanceOf[js.Object], "value"))
    assert(explicitNull.value == null)
    val defined = encoder.encode(
      OptionalProps(Nullable.Value("text"), Nested("nested"))
    ).asInstanceOf[js.Dynamic]
    assert(defined.value.asInstanceOf[String] == "text")
    assert(defined.nested.label.asInstanceOf[String] == "nested")
  }
  test("manual encoder overrides special external representations") {
    case class Special(value: String)
    given PropsEncoder[Special] =
      value => js.Dynamic.literal(renamed = value.value)
    case class Outer(special: Special) derives PropsEncoder
    val encoded = summon[PropsEncoder[Outer]].encode(
      Outer(Special("x"))
    ).asInstanceOf[js.Dynamic]
    assert(encoded.special.renamed.asInstanceOf[String] == "x")
  }
  test("unsupported fields and callback inputs fail at compile time") {
    assertDoesNotCompile(
      """import marubinotto.react.*; case class Bad(value: Any) derives PropsEncoder"""
    )
    assertDoesNotCompile(
      """import marubinotto.react.*; import scala.scalajs.js; case class Unsupported(value: String); case class Bad(value: js.UndefOr[Nullable[Unsupported]]) derives PropsEncoder"""
    )
    assertDoesNotCompile(
      """import marubinotto.react.*; case class Input(value: String); case class Bad(callback: Input => Unit) derives PropsEncoder"""
    )
  }
}
