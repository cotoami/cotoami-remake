package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom
import org.scalajs.dom.html

import marubinotto.react._
import marubinotto.react.{ReactNode, DomRef}
import marubinotto.react.Hooks._

object ScrollArea {
  case class Props(
      className: Option[String] = None,
      scrollableElementId: Option[String] = None,
      scrollableClassName: Option[String] = None,
      scrollToRightWhen: Option[String] = None,
      scrollToTopWhen: Option[String] = None,
      autoHide: Boolean = true,
      bottomThreshold: Option[Int] = None,
      onScrollToBottom: Option[() => Unit] = None,
      onScrollTopChange: Option[Double => Unit] = None,

      // Notify when being unmounted with the scroll position (scrollTop).
      onUnmounted: Option[Double => Unit] = None,

      // scrollTop will be set to the given value during init and when the value has changed
      setScrollTop: Option[Double] = None
  )(children: ReactNode*) {
    // Keep children separate so optional props retain their default arguments.
    def getChildren: Seq[ReactNode] = children
  }

  def apply(
      className: Option[String] = None,
      scrollableElementId: Option[String] = None,
      scrollableClassName: Option[String] = None,
      scrollToRightWhen: Option[String] = None,
      scrollToTopWhen: Option[String] = None,
      autoHide: Boolean = true,
      bottomThreshold: Option[Int] = None,
      onScrollToBottom: Option[() => Unit] = None,
      onScrollTopChange: Option[Double => Unit] = None,
      onUnmounted: Option[Double => Unit] = None,
      setScrollTop: Option[Double] = None
  )(children: ReactNode*) =
    component(
      Props(
        className,
        scrollableElementId,
        scrollableClassName,
        scrollToRightWhen,
        scrollToTopWhen,
        autoHide,
        bottomThreshold,
        onScrollToBottom,
        onScrollTopChange,
        onUnmounted,
        setScrollTop
      )(children*)
    )

  val DefaultClassName = "scroll-area"
  val DefaultBottomThreshold = 1
  val DefaultScrollToRightMaxFrames = 30

  val component = FunctionalComponent[Props] { props =>
    val scrollableNodeRef = useDomRef[html.Div]()
    val bottomThreshold =
      props.bottomThreshold.getOrElse(DefaultBottomThreshold)

    val onScroll: js.Function1[dom.Event, Unit] = useCallback(
      (e: dom.Event) => {
        // cf. https://developer.mozilla.org/en-US/docs/Web/API/Element/scrollHeight
        val target = e.target.asInstanceOf[dom.Element]
        val scrollHeight = target.scrollHeight
        val scrollTop = target.scrollTop.toFloat.round
        val clientHeight = target.clientHeight
        props.onScrollTopChange.map(_(scrollTop))
        val isBottomReached =
          (scrollHeight - clientHeight - scrollTop).abs <= bottomThreshold
        if (isBottomReached) {
          props.onScrollToBottom.map(_())
        }
      },
      Seq(props.onScrollToBottom, props.onScrollTopChange)
    )

    // setScrollTop
    useEffect(
      () => {
        props.setScrollTop.foreach { scrollTop =>
          scrollableNodeRef.value.orNull.scrollTop = scrollTop
          props.onScrollTopChange.map(_(scrollTop))
        }
      },
      Seq(props.setScrollTop.getOrElse(-1))
    )

    // scroll to top when requested.
    useEffect(
      () => {
        props.scrollToTopWhen.foreach { _ =>
          val scrollable = scrollableNodeRef.value.orNull
          scrollable
            .asInstanceOf[js.Dynamic]
            .scrollTo(
              js.Dynamic.literal(top = 0, behavior = "smooth")
            )
          props.onScrollTopChange.map(_(0))
        }
      },
      Seq(props.scrollToTopWhen.getOrElse(""))
    )

    // scroll to right end after the content width has settled.
    useEffect(
      () => {
        props.scrollToRightWhen.foreach { _ =>
          val scrollable = scrollableNodeRef.value.orNull
          var frameCount = 0
          var stableFrames = 0
          var lastTarget = -1.0

          def targetLeft: Double =
            (scrollable.scrollWidth - scrollable.clientWidth).max(0).toDouble

          def loop(): Unit =
            dom.window.requestAnimationFrame { _ =>
              val nextTarget = targetLeft
              val targetChanged = (nextTarget - lastTarget).abs > 1
              if (targetChanged) {
                stableFrames = 0
                scrollable
                  .asInstanceOf[js.Dynamic]
                  .scrollTo(
                    js.Dynamic.literal(left = nextTarget, behavior = "smooth")
                  )
              } else {
                stableFrames = stableFrames + 1
              }

              lastTarget = nextTarget
              frameCount = frameCount + 1
              if (stableFrames < 2 && frameCount < DefaultScrollToRightMaxFrames)
                loop()
            }

          loop()
        }
      },
      Seq(props.scrollToRightWhen.getOrElse(""))
    )

    // Register onScroll
    useEffect(
      () => {
        val scrollable = scrollableNodeRef.value.orNull
        scrollable.addEventListener("scroll", onScroll)
        () => {
          scrollable.removeEventListener("scroll", onScroll)
        }
      },
      Seq.empty
    )

    // onUnmounted
    useEffect(
      () => {
        val scrollable = scrollableNodeRef.value.orNull
        () => {
          props.onUnmounted.map(_(scrollable.scrollTop))
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
    )(props.getChildren*)
  }
}

@js.native
@JSImport("simplebar-react", JSImport.Default)
object SimpleBarReact extends js.Object

@js.native
@JSImport("simplebar-react/dist/simplebar.min.css", JSImport.Namespace)
object SimpleBarCSS extends js.Object

object SimpleBar extends ExternalComponent {
  val css = SimpleBarCSS

  case class Props(
      className: String,
      autoHide: Boolean,
      scrollableNodeProps: ScrollableNodeProps
  ) derives PropsEncoder

  case class ScrollableNodeProps(
      id: Option[String] = None,
      className: Option[String] = None,
      ref: Option[DomRef[html.Div]] = None
  ) derives PropsEncoder

  def apply(
      className: String,
      autoHide: Boolean,
      scrollableNodeProps: ScrollableNodeProps
  )(children: ReactNode*) =
    super.apply(Props(className, autoHide, scrollableNodeProps))(children*)

  override val component = SimpleBarReact
}
