package marubinotto.components

import marubinotto.react.*
import marubinotto.react.html.*
import org.scalatest.funsuite.AnyFunSuite
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("react-dom/server", "renderToStaticMarkup")
private object RenderInterop extends js.Function1[js.Any, String] {
  def apply(node: js.Any): String = js.native
}
class ReactInteropSpec extends AnyFunSuite {
  private def markup(node: ReactNode): String = RenderInterop(
    ReactNode.toJs(node)
  )
  private val option = new Select.SelectOption {
    val value = "one"
    val label = "One"
    val isDisabled = false
  }

  test(
    "react-select receives encoded options, renders labels, and supports explicit clearing"
  ) {
    val selected = Select(
      "test-select",
      Seq(option),
      value = Some(Nullable.Value(option)),
      formatOptionLabel = Some(o => span(o.label))
    )
    assert(markup(selected).contains("One"))
    val cleared = Select(
      "test-select",
      Seq(option),
      placeholder = Some("Choose"),
      value = Some(Nullable.Null)
    )
    assert(markup(cleared).contains("Choose"))
    val encoded = summon[PropsEncoder[Select.Props]].encode(
      Select.Props("test", Seq(option), value = Some(Nullable.Null))
    ).asInstanceOf[js.Dynamic]
    assert(encoded.value == null)
    val uncontrolled = summon[PropsEncoder[Select.Props]].encode(
      Select.Props("test", Seq(option))
    )
    assert(!js.Object.hasProperty(uncontrolled, "value"))
    var wasCleared = false
    val callback = summon[PropsEncoder[Select.Props]].encode(
      Select.Props(
        "test",
        Seq(option),
        onChange = Some((value, _) => wasCleared = value == null)
      )
    ).asInstanceOf[js.Dynamic]
    callback.onChange(null, js.Dynamic.literal(action = "clear"))
    assert(wasCleared)
  }

  test("react-markdown custom components exchange native React nodes") {
    val components =
      js.Dynamic.literal(a = React.component[js.Dynamic] { props =>
        a(href := props.href.asInstanceOf[String], className := "custom-link")(
          ReactNode.fromJs(props.children)
        )
      })
    val rendered = markup(
      Markdown(Seq.empty, Seq.empty, components.asInstanceOf[js.Object])(
        "[link](https://example.com)"
      )
    )
    assert(rendered.contains("custom-link"))
    assert(rendered.contains(">link</a>"))
  }

  test("react-easy-crop receives native positions and encoded callbacks") {
    val position = FixedAspectCrop.position(1, 2)
    var changed: FixedAspectCrop.Position = null
    val props = FixedAspectCrop.Props(
      "data:image/png;base64,",
      p => changed = p,
      position
    )
    val encoded = summon[PropsEncoder[FixedAspectCrop.Props]].encode(
      props
    ).asInstanceOf[js.Dynamic]
    assert(encoded.crop.asInstanceOf[js.Object] eq position)
    encoded.onCropChange(position)
    assert(changed eq position)
    assert(
      markup(FixedAspectCrop("test.png", _ => (), position)).contains(
        "test.png"
      )
    )
  }

  test("react-flip-toolkit accepts keyed external children") {
    val rendered = markup(
      Flipper("div", "flip-test", "first")(
        Flipped("one", "one")(div("content"))
      )
    )
    assert(rendered.contains("data-flip-id=\"one\""))
    assert(rendered.contains("content"))
  }
  test("react-dropzone hook props render a real file input") {
    val rendered = markup(
      cotoami.subparts.InputFile(
        js.Dictionary("image/*" -> js.Array[String]()),
        "Drop image",
        _ => ()
      )
    )
    assert(rendered.contains("type=\"file\""))
    assert(rendered.contains("accept=\"image/*\""))
    assert(rendered.contains("Drop image"))
  }

  test("SimpleBar receives nested props and a native ref object") {
    var encodedRef: js.Dynamic = null
    val wrapper = FunctionalComponent[Unit] { _ =>
      val scrollRef = Hooks.useDomRef[org.scalajs.dom.html.Div]()
      val props = SimpleBar.Props(
        "scroll-test",
        true,
        SimpleBar.ScrollableNodeProps(
          Some("scroll-node"),
          Some("scroll-content"),
          Some(scrollRef)
        )
      )
      val encoded = summon[PropsEncoder[SimpleBar.Props]].encode(props)
      encodedRef = encoded.asInstanceOf[js.Dynamic].scrollableNodeProps.ref
      React.createElement(SimpleBarReact, encoded, div("scroll content"))
    }
    val rendered = markup(wrapper(()))
    assert(rendered.contains("scroll-node"))
    assert(rendered.contains("scroll content"))
    assert(encodedRef.current == null)
  }

}
