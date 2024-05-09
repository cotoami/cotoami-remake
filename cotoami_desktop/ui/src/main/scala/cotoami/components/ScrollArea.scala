package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom
import org.scalajs.dom.html

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{React, ReactElement, ReactRef}
import slinky.core.facade.Hooks._

@react object ScrollArea {
  case class Props(
      scrollableElementId: Option[String],
      autoHide: Boolean,
      bottomThreshold: Option[Int],
      onScrollToBottom: () => Unit,
      children: ReactElement*
  )

  val DefaultBottomThreshold = 1

  val component = FunctionalComponent[Props] { props =>
    val scrollableNodeRef = React.createRef[html.Div]
    val bottomThreshold =
      props.bottomThreshold.getOrElse(DefaultBottomThreshold)

    val onScroll: js.Function1[dom.Event, Unit] = (e: dom.Event) => {
      // cf. https://developer.mozilla.org/en-US/docs/Web/API/Element/scrollHeight
      val target = e.target.asInstanceOf[dom.Element]
      val scrollHeight = target.scrollHeight
      val scrollTop = target.scrollTop.toFloat.round
      val clientHeight = target.clientHeight
      val isBottomReached =
        (scrollHeight - clientHeight - scrollTop).abs <= bottomThreshold
      if (isBottomReached) {
        props.onScrollToBottom()
      }
    }

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

    SimpleBar(
      autoHide = props.autoHide,
      scrollableNodeProps = SimpleBar.ScrollableNodeProps(
        props.scrollableElementId,
        None,
        Some(scrollableNodeRef)
      )
    )(props.children: _*)
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
