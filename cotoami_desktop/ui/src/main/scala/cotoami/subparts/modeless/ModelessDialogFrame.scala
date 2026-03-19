package cotoami.subparts.modeless

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.html

import slinky.core._
import slinky.core.facade.{ReactElement, ReactRef}
import slinky.core.facade.Hooks._
import slinky.web.SyntheticMouseEvent
import slinky.web.html._

import marubinotto.optionalClasses

object ModelessDialogFrame {

  object Defaults {
    val Left = 24.0
    val Top = 24.0
    val Width = "min(720px, calc(100vw - 32px))"
    val Height = "min(760px, calc(100vh - 48px))"
  }

  private case class Position(left: Double, top: Double)
  private case class DragState(
      mouseX: Double,
      mouseY: Double,
      left: Double,
      top: Double
  )
  private case class PanelBounds(width: Double, height: Double)

  private def panelBoundsOf(panelRef: ReactRef[html.Div]): PanelBounds =
    PanelBounds(
      width = Option(panelRef.current).map(_.offsetWidth.toDouble).getOrElse(0.0),
      height = Option(panelRef.current).map(_.offsetHeight.toDouble).getOrElse(0.0)
    )

  private def clampPosition(position: Position, bounds: PanelBounds): Position = {
    val maxLeft = (dom.window.innerWidth - bounds.width).max(0.0)
    val maxTop = (dom.window.innerHeight - bounds.height).max(0.0)
    Position(
      position.left.max(0.0).min(maxLeft),
      position.top.max(0.0).min(maxTop)
    )
  }

  private def centerPosition(bounds: PanelBounds): Position =
    clampPosition(
      Position(
        left = (dom.window.innerWidth - bounds.width) / 2.0,
        top = (dom.window.innerHeight - bounds.height) / 2.0
      ),
      bounds
    )

  case class Props(
      dialogClasses: Seq[(String, Boolean)],
      title: ReactElement,
      onClose: () => Unit,
      onFocus: () => Unit,
      zIndex: Int,
      initialWidth: String = Defaults.Width,
      initialHeight: String = Defaults.Height,
      error: Option[String] = None
  )(children: ReactElement*) {
    def body: Seq[ReactElement] = children
  }

  def apply(
      dialogClasses: Seq[(String, Boolean)],
      title: ReactElement,
      onClose: () => Unit,
      onFocus: () => Unit,
      zIndex: Int,
      initialWidth: String = Defaults.Width,
      initialHeight: String = Defaults.Height,
      error: Option[String] = None
  )(children: ReactElement*): ReactElement =
    component(
      Props(
        dialogClasses,
        title,
        onClose,
        onFocus,
        zIndex,
        initialWidth,
        initialHeight,
        error
      )(children*)
    )

  private val component = FunctionalComponent[Props] { props =>
    val panelRef = useRef[html.Div](null)
    val dragRef = useRef(Option.empty[DragState])
    val (position, setPosition) =
      useState(Option.empty[Position])

    val onMouseMove: js.Function1[dom.MouseEvent, Unit] = useCallback(
      (e: dom.MouseEvent) => {
        dragRef.current.foreach { drag =>
          setPosition(
            _.map(_ =>
              clampPosition(
                Position(
                  drag.left + e.clientX - drag.mouseX,
                  drag.top + e.clientY - drag.mouseY
                ),
                panelBoundsOf(panelRef)
              )
            )
          )
        }
      },
      Seq()
    )

    val onMouseUp: js.Function1[dom.MouseEvent, Unit] = useCallback(
      (_: dom.MouseEvent) => {
        dragRef.current = None
      },
      Seq()
    )

    useEffect(
      () => {
        dom.document.addEventListener("mousemove", onMouseMove)
        dom.document.addEventListener("mouseup", onMouseUp)

        () => {
          dom.document.removeEventListener("mousemove", onMouseMove)
          dom.document.removeEventListener("mouseup", onMouseUp)
        }
      },
      Seq()
    )

    useEffect(
      () => {
        if (position.isEmpty) {
          val bounds = panelBoundsOf(panelRef)
          if (bounds.width > 0.0 && bounds.height > 0.0) {
            setPosition(_ => Some(centerPosition(bounds)))
          }
        }
      },
      Seq(position.isEmpty)
    )

    useEffect(
      () => {
        val clampToViewport: js.Function1[dom.Event, Unit] =
          (_: dom.Event) =>
            setPosition(_.map(current => clampPosition(current, panelBoundsOf(panelRef))))

        clampToViewport(new dom.Event("resize"))
        dom.window.addEventListener("resize", clampToViewport)

        () => dom.window.removeEventListener("resize", clampToViewport)
      },
      Seq()
    )

    val startDragging = useCallback(
      (e: SyntheticMouseEvent[dom.Element]) => {
        if (e.button == 0) {
          position.foreach { current =>
            dragRef.current = Some(
              DragState(
                mouseX = e.clientX,
                mouseY = e.clientY,
                left = current.left,
                top = current.top
              )
            )
          }
          e.preventDefault()
        }
      },
      Seq(position.map(_.left).getOrElse(0.0), position.map(_.top).getOrElse(0.0))
    )

    val displayPosition = position.getOrElse(Position(0.0, 0.0))
    val visible = position.isDefined

    div(
      className := "modeless-dialog-layer",
      style := js.Dynamic.literal(
        zIndex = props.zIndex
      )
    )(
      div(
        className := optionalClasses(("modeless-dialog", true) +: props.dialogClasses),
        ref := panelRef,
        onMouseDown := (_ => props.onFocus()),
        style := js.Dynamic.literal(
          left = s"${displayPosition.left}px",
          top = s"${displayPosition.top}px",
          width = props.initialWidth,
          height = props.initialHeight,
          visibility = if (visible) "visible" else "hidden"
        )
      )(
        article()(
          header(
            className := "drag-handle",
            onMouseDown := (startDragging(_))
          )(
            h1()(props.title),
            button(
              `type` := "button",
              className := "close default",
              onMouseDown := (e => e.stopPropagation()),
              onClick := (e => {
                e.stopPropagation()
                props.onClose()
              })
            )
          ),
          props.error.map(e => section(className := "error")(e)),
          div(className := "modal-body")(props.body*)
        )
      )
    )
  }
}
