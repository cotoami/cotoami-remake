package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom
import org.scalajs.dom.html

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{ReactElement, ReactRef}
import slinky.core.facade.Hooks._

@react object ScrollArea {
  case class Props(
      className: Option[String] = None,
      scrollableElementId: Option[String] = None,
      scrollableClassName: Option[String] = None,
      autoHide: Boolean = true,
      initialScrollTop: Option[Double] = None,
      bottomThreshold: Option[Int] = None,
      onScrollToBottom: Option[() => Unit] = None,
      // Notify when being unmounted with the scroll position (scrollTop).
      onUnmounted: Option[Double => Unit] = None
  )(children: ReactElement*) {
    // While @react converts `children: ReactElement*` into a curried parameter of `ScrollArea.apply`,
    // the varargs makes default `Props` values unusable, which is inconvenient.
    // https://github.com/shadaj/slinky/issues/245
    // https://github.com/shadaj/slinky/blob/v0.7.4/core/src/main/scala-2/slinky/core/annotations/react.scala#L43
    //
    // Putting the `children` in the secondary parameters seems to work without this limitation.
    def getChildren: Seq[ReactElement] = children
  }

  val DefaultClassName = "scroll-area"
  val DefaultBottomThreshold = 1

  val component = FunctionalComponent[Props] { props =>
    val scrollableNodeRef = useRef[html.Div](null)
    val bottomThreshold =
      props.bottomThreshold.getOrElse(DefaultBottomThreshold)

    val onScroll: js.Function1[dom.Event, Unit] = useCallback(
      (e: dom.Event) => {
        // cf. https://developer.mozilla.org/en-US/docs/Web/API/Element/scrollHeight
        val target = e.target.asInstanceOf[dom.Element]
        val scrollHeight = target.scrollHeight
        val scrollTop = target.scrollTop.toFloat.round
        val clientHeight = target.clientHeight
        val isBottomReached =
          (scrollHeight - clientHeight - scrollTop).abs <= bottomThreshold
        if (isBottomReached) {
          props.onScrollToBottom.map(_())
        }
      },
      Seq(props.onScrollToBottom)
    )

    // Set the initial scrollTop
    useEffect(
      () => {
        props.initialScrollTop match {
          case Some(scrollTop) =>
            scrollableNodeRef.current.scrollTop = scrollTop
          case None => ()
        }
      },
      Seq.empty
    )

    // Register onScroll
    useEffect(
      () => {
        val scrollable = scrollableNodeRef.current
        scrollable.addEventListener("scroll", onScroll)
        () => {
          scrollable.removeEventListener("scroll", onScroll)
        }
      },
      Seq.empty
    )

    // onUnmounted
    useEffect(
      () => { () =>
        {
          props.onUnmounted.map(_(scrollableNodeRef.current.scrollTop))
        }
      },
      Seq.empty
    )

    SimpleBar(
      className = s"${DefaultClassName} ${props.className.getOrElse("")}",
      autoHide = props.autoHide,
      scrollableNodeProps = SimpleBar.ScrollableNodeProps(
        props.scrollableElementId,
        props.scrollableClassName,
        Some(scrollableNodeRef)
      )
    )(props.getChildren: _*)
  }
}

@js.native
@JSImport("simplebar-react", JSImport.Default)
object SimpleBarReact extends js.Object

@js.native
@JSImport("simplebar-react/dist/simplebar.min.css", JSImport.Namespace)
object SimpleBarCSS extends js.Object

@react object SimpleBar extends ExternalComponent {
  val css = SimpleBarCSS

  case class Props(
      className: String,
      autoHide: Boolean,
      scrollableNodeProps: ScrollableNodeProps,
      children: ReactElement*
  )

  case class ScrollableNodeProps(
      id: Option[String] = None,
      className: Option[String] = None,
      ref: Option[ReactRef[html.Div]] = None
  )

  override val component = SimpleBarReact
}
