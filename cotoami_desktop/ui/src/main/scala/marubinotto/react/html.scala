package marubinotto.react

import scala.scalajs.js
import scala.language.implicitConversions
import org.scalajs.dom

type TagMod[T] = AttrPair[T] | ReactNode
final case class AttrPair[-T](name: String, private[react] val value: js.Any)
object AttrPair {
  given [T]: Conversion[Option[AttrPair[T]], AttrPair[T]] =
    _.getOrElse(AttrPair("", js.undefined))
}
class Attr[A](val name: String)(using encoder: ValueEncoder[A]) {
  def :=(value: A): AttrPair[Any] = AttrPair(name, encoder.encode(value))
  def :=(value: Option[A]): AttrPair[Any] =
    AttrPair(name, value.fold[js.Any](js.undefined)(encoder.encode))
}
class EventAttr[E[_]](name: String) {
  def :=(callback: () => Unit): AttrPair[Any] =
    AttrPair(name, (() => callback()): js.Function0[Unit])
  def :=(callback: Option[() => Unit]): AttrPair[Any] =
    callback.fold(AttrPair[Any](name, js.undefined))(this.:=)

  def :=[T](callback: E[T] => Unit): AttrPair[T] =
    AttrPair(
      name,
      ((event: js.Any) => callback(event.asInstanceOf[E[T]])): js.Function1[
        js.Any,
        Unit
      ]
    )
}
class Tag[T <: dom.Element](val name: String) {
  type tagType = T
  def apply(items: TagMod[T]*): TagElement[T] = new TagElement(name, items)
}
final class TagElement[T <: dom.Element](name: String, items: Seq[TagMod[T]])
    extends ReactElement {
  def apply(children: ReactNode*): TagElement[T] =
    new TagElement(name, items ++ children)
  def withKey(key: String): TagElement[T] =
    new TagElement(name, items :+ AttrPair("key", key))
  lazy val raw: js.Any = {
    val props = js.Dictionary.empty[js.Any]
    val children = js.Array[js.Any]()
    items.foreach {
      case attr: AttrPair[?] =>
        if (attr.name.nonEmpty && !js.isUndefined(attr.value))
          props(attr.name) = attr.value
      case child: ReactNode => children.push(child.raw)
    }
    RawReact.createElement(name, props, children.toSeq*)
  }
}
object html {
  val time = new Tag[dom.HTMLElement]("time")
  val progress = new Tag[dom.HTMLProgressElement]("progress")
  val a = new Tag[dom.html.Anchor]("a")
  val abbr = new Tag[dom.html.Element]("abbr")
  val address = new Tag[dom.html.Element]("address")
  val article = new Tag[dom.html.Element]("article")
  val aside = new Tag[dom.html.Element]("aside")
  val b = new Tag[dom.html.Element]("b")
  val blockquote = new Tag[dom.html.Element]("blockquote")
  val br = new Tag[dom.html.Element]("br")
  val button = new Tag[dom.html.Button]("button")
  val canvas = new Tag[dom.html.Canvas]("canvas")
  val code = new Tag[dom.html.Element]("code")
  val details = new Tag[dom.HTMLElement]("details")
  val dialog = new Tag[dom.HTMLElement]("dialog")
  val div = new Tag[dom.html.Div]("div")
  val em = new Tag[dom.html.Element]("em")
  val fieldset = new Tag[dom.html.Element]("fieldset")
  val figcaption = new Tag[dom.html.Element]("figcaption")
  val figure = new Tag[dom.html.Element]("figure")
  val footer = new Tag[dom.html.Element]("footer")
  val form = new Tag[dom.html.Form]("form")
  val h1 = new Tag[dom.html.Element]("h1")
  val h2 = new Tag[dom.html.Element]("h2")
  val h3 = new Tag[dom.html.Element]("h3")
  val h4 = new Tag[dom.html.Element]("h4")
  val h5 = new Tag[dom.html.Element]("h5")
  val h6 = new Tag[dom.html.Element]("h6")
  val header = new Tag[dom.html.Element]("header")
  val hr = new Tag[dom.html.Element]("hr")
  val i = new Tag[dom.html.Element]("i")
  val img = new Tag[dom.html.Image]("img")
  val input = new Tag[dom.html.Input]("input")
  val label = new Tag[dom.html.Label]("label")
  val li = new Tag[dom.html.LI]("li")
  val main = new Tag[dom.html.Element]("main")
  val nav = new Tag[dom.html.Element]("nav")
  val ol = new Tag[dom.html.OList]("ol")
  val option = new Tag[dom.html.Element]("option")
  val p = new Tag[dom.html.Element]("p")
  val pre = new Tag[dom.html.Pre]("pre")
  val section = new Tag[dom.html.Element]("section")
  val select = new Tag[dom.html.Select]("select")
  val small = new Tag[dom.html.Element]("small")
  val span = new Tag[dom.html.Span]("span")
  val strong = new Tag[dom.html.Element]("strong")
  val summary = new Tag[dom.html.Element]("summary")
  val table = new Tag[dom.html.Element]("table")
  val tbody = new Tag[dom.html.Element]("tbody")
  val td = new Tag[dom.html.Element]("td")
  val textarea = new Tag[dom.html.TextArea]("textarea")
  val th = new Tag[dom.html.Element]("th")
  val thead = new Tag[dom.html.Element]("thead")
  val tr = new Tag[dom.html.Element]("tr")
  val ul = new Tag[dom.html.UList]("ul")
  val alt = new Attr[String]("alt")
  val className = new Attr[String]("className")
  val href = new Attr[String]("href")
  val htmlFor = new Attr[String]("htmlFor")
  val id = new Attr[String]("id")
  val name = new Attr[String]("name")
  val placeholder = new Attr[String]("placeholder")
  val role = new Attr[String]("role")
  val src = new Attr[String]("src")
  val title = new Attr[String]("title")
  val `type` = new Attr[String]("type")
  val key = new Attr[String]("key")
  val autoFocus = new Attr[Boolean]("autoFocus")
  val checked = new Attr[Boolean]("checked")
  val disabled = new Attr[Boolean]("disabled")
  val hidden = new Attr[Boolean]("hidden")
  val open = new Attr[Boolean]("open")
  val readOnly = new Attr[Boolean]("readOnly")
  val spellCheck = new Attr[Boolean]("spellCheck")
  val tabIndex = new Attr[Int]("tabIndex")
  val value =
    new Attr[String | Double]("value")(using
      new ValueEncoder[String | Double] {
        def encode(v: String | Double): js.Any = v.asInstanceOf[js.Any]
      }
    )
  val defaultValue = new Attr[String | Double]("defaultValue")(using
    new ValueEncoder[String | Double] {
      def encode(v: String | Double): js.Any = v.asInstanceOf[js.Any]
    }
  )
  val min =
    new Attr[String | Double]("min")(using
      new ValueEncoder[String | Double] {
        def encode(v: String | Double): js.Any = v.asInstanceOf[js.Any]
      }
    )
  val max =
    new Attr[String | Double]("max")(using
      new ValueEncoder[String | Double] {
        def encode(v: String | Double): js.Any = v.asInstanceOf[js.Any]
      }
    )
  val style = new Attr[js.Object]("style")
  val dangerouslySetInnerHTML = new Attr[js.Object]("dangerouslySetInnerHTML")
  val onClick = new EventAttr[SyntheticMouseEvent]("onClick")
  val onDoubleClick = new EventAttr[SyntheticMouseEvent]("onDoubleClick")
  val onMouseDown = new EventAttr[SyntheticMouseEvent]("onMouseDown")
  val onMouseEnter = new EventAttr[SyntheticMouseEvent]("onMouseEnter")
  val onMouseLeave = new EventAttr[SyntheticMouseEvent]("onMouseLeave")
  val onMouseMove = new EventAttr[SyntheticMouseEvent]("onMouseMove")
  val onMouseOver = new EventAttr[SyntheticMouseEvent]("onMouseOver")
  val onMouseUp = new EventAttr[SyntheticMouseEvent]("onMouseUp")
  val onKeyDown = new EventAttr[SyntheticKeyboardEvent]("onKeyDown")
  val onBlur = new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onBlur")
  val onChange = new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onChange")
  val onCompositionEnd =
    new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onCompositionEnd")
  val onCompositionStart =
    new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onCompositionStart")
  val onError = new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onError")
  val onFocus = new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onFocus")
  val onInput = new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onInput")
  val onLoad = new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onLoad")
  val onScroll = new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onScroll")
  val onSubmit = new EventAttr[[T] =>> SyntheticEvent[T, dom.Event]]("onSubmit")
  object ref {
    def :=[T <: dom.Element](value: DomRef[T]): AttrPair[T] =
      AttrPair("ref", value.raw)
    def :=[T <: dom.Element](callback: (T | Null) => Unit): AttrPair[T] =
      AttrPair(
        "ref",
        ((node: T | Null) => callback(node)): js.Function1[T | Null, Unit]
      )
    def cleanup[T <: dom.Element](callback: T => (() => Unit)): AttrPair[T] =
      AttrPair(
        "ref",
        ((node: T | Null) => {
          if (node == null) js.undefined
          else {
            val release = callback(node.asInstanceOf[T]);
            (() => release()): js.Function0[Unit]
          }
        }): js.Function1[T | Null, js.UndefOr[js.Function0[Unit]]]
      )
  }
  object aria { def -(name: String): Attr[String] = new Attr("aria-" + name) }
  object data { def -(name: String): Attr[String] = new Attr("data-" + name) }
}
