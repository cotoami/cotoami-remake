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
  private case class MinimumSize(width: Double, height: Double)
  private case class DragState(
      mouseX: Double,
      mouseY: Double,
      left: Double,
      top: Double
  )
  private enum ResizeCorner {
    case TopLeft, TopRight, BottomLeft, BottomRight
  }
  private case class ResizeState(
      corner: ResizeCorner,
      mouseX: Double,
      mouseY: Double,
      left: Double,
      top: Double,
      width: Double,
      height: Double,
      minWidth: Double,
      minHeight: Double
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

  private def minimumSizeOf(panelRef: ReactRef[html.Div]): MinimumSize =
    Option(panelRef.current).map { panel =>
      val style = dom.window.getComputedStyle(panel)
      MinimumSize(
        width = style.minWidth.stripSuffix("px").toDoubleOption.getOrElse(0.0),
        height = style.minHeight.stripSuffix("px").toDoubleOption.getOrElse(0.0)
      )
    }.getOrElse(MinimumSize(0.0, 0.0))

  private def clampSize(
      position: Position,
      bounds: PanelBounds,
      minimum: MinimumSize
  ): PanelBounds = {
    val maxWidth = (dom.window.innerWidth - position.left).max(0.0)
    val maxHeight = (dom.window.innerHeight - position.top).max(0.0)
    PanelBounds(
      width = bounds.width.max(minimum.width.min(maxWidth)).min(maxWidth),
      height = bounds.height.max(minimum.height.min(maxHeight)).min(maxHeight)
    )
  }

  private def resizedRect(state: ResizeState, mouseX: Double, mouseY: Double): (
      Position,
      PanelBounds
  ) = {
    val dx = mouseX - state.mouseX
    val dy = mouseY - state.mouseY
    val right = state.left + state.width
    val bottom = state.top + state.height
    state.corner match {
      case ResizeCorner.TopLeft =>
        val left = (state.left + dx).max(0.0).min(right - state.minWidth)
        val top = (state.top + dy).max(0.0).min(bottom - state.minHeight)
        (Position(left, top), PanelBounds(right - left, bottom - top))
      case ResizeCorner.TopRight =>
        val newRight = (right + dx)
          .max(state.left + state.minWidth)
          .min(dom.window.innerWidth.toDouble)
        val top = (state.top + dy).max(0.0).min(bottom - state.minHeight)
        (Position(state.left, top), PanelBounds(newRight - state.left, bottom - top))
      case ResizeCorner.BottomLeft =>
        val left = (state.left + dx).max(0.0).min(right - state.minWidth)
        val newBottom = (bottom + dy)
          .max(state.top + state.minHeight)
          .min(dom.window.innerHeight.toDouble)
        (Position(left, state.top), PanelBounds(right - left, newBottom - state.top))
      case ResizeCorner.BottomRight =>
        val newRight = (right + dx)
          .max(state.left + state.minWidth)
          .min(dom.window.innerWidth.toDouble)
        val newBottom = (bottom + dy)
          .max(state.top + state.minHeight)
          .min(dom.window.innerHeight.toDouble)
        (
          Position(state.left, state.top),
          PanelBounds(newRight - state.left, newBottom - state.top)
        )
    }
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
    val resizeRef = useRef(Option.empty[ResizeState])
    val (position, setPosition) =
      useState(Option.empty[Position])
    val (size, setSize) =
      useState(Option.empty[PanelBounds])

    val onMouseMove: js.Function1[dom.MouseEvent, Unit] = useCallback(
      (e: dom.MouseEvent) => {
        resizeRef.current.foreach { resize =>
          val (newPosition, newSize) =
            resizedRect(resize, e.clientX, e.clientY)
          setPosition(_ => Some(newPosition))
          setSize(_ => Some(newSize))
        }
        Option.when(resizeRef.current.isEmpty) {
          dragRef.current.foreach { drag =>
            setPosition(
              _.map(_ =>
                clampPosition(
                  Position(
                    drag.left + e.clientX - drag.mouseX,
                    drag.top + e.clientY - drag.mouseY
                  ),
                  size.getOrElse(panelBoundsOf(panelRef))
                )
              )
            )
          }
        }
      },
      Seq(size.map(_.width).getOrElse(0.0), size.map(_.height).getOrElse(0.0))
    )

    val onMouseUp: js.Function1[dom.MouseEvent, Unit] = useCallback(
      (_: dom.MouseEvent) => {
        dragRef.current = None
        resizeRef.current = None
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
            setSize(_ => Some(bounds))
          }
        }
      },
      Seq(position.isEmpty)
    )

    useEffect(
      () => {
        val clampToViewport: js.Function1[dom.Event, Unit] =
          (_: dom.Event) => {
            val minimum = minimumSizeOf(panelRef)
            val currentSize = size.getOrElse(panelBoundsOf(panelRef))
            if (currentSize.width > 0.0 && currentSize.height > 0.0) {
              setSize(_ => Some(currentSize))
              position.foreach { currentPosition =>
                val clampedPosition =
                  clampPosition(currentPosition, currentSize)
                val clampedSize =
                  clampSize(clampedPosition, currentSize, minimum)
                setPosition(_ => Some(clampPosition(clampedPosition, clampedSize)))
                setSize(_ => Some(clampedSize))
              }
            }
          }

        clampToViewport(new dom.Event("resize"))
        dom.window.addEventListener("resize", clampToViewport)

        () => dom.window.removeEventListener("resize", clampToViewport)
      },
      Seq(
        position.map(_.left).getOrElse(0.0),
        position.map(_.top).getOrElse(0.0),
        size.map(_.width).getOrElse(0.0),
        size.map(_.height).getOrElse(0.0)
      )
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

    val startResizing = useCallback(
      (
          corner: ResizeCorner,
          e: SyntheticMouseEvent[dom.Element]
      ) => {
        if (e.button == 0) {
          val bounds = panelBoundsOf(panelRef)
          val minimum = minimumSizeOf(panelRef)
          position.foreach { current =>
            resizeRef.current = Some(
              ResizeState(
                corner = corner,
                mouseX = e.clientX,
                mouseY = e.clientY,
                left = current.left,
                top = current.top,
                width = size.getOrElse(bounds).width,
                height = size.getOrElse(bounds).height,
                minWidth = minimum.width,
                minHeight = minimum.height
              )
            )
          }
          props.onFocus()
          e.stopPropagation()
          e.preventDefault()
        }
      },
      Seq(
        position.map(_.left).getOrElse(0.0),
        position.map(_.top).getOrElse(0.0),
        size.map(_.width).getOrElse(0.0),
        size.map(_.height).getOrElse(0.0)
      )
    )

    val displayPosition = position.getOrElse(Position(0.0, 0.0))
    val displaySize = size.getOrElse(panelBoundsOf(panelRef))
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
          width =
            if (size.isDefined) s"${displaySize.width}px" else props.initialWidth,
          height =
            if (size.isDefined) s"${displaySize.height}px" else props.initialHeight,
          visibility = if (visible) "visible" else "hidden"
        )
      )(
        div(
          className := "resize-handle top-left",
          onMouseDown := (e => startResizing(ResizeCorner.TopLeft, e))
        ),
        div(
          className := "resize-handle top-right",
          onMouseDown := (e => startResizing(ResizeCorner.TopRight, e))
        ),
        div(
          className := "resize-handle bottom-left",
          onMouseDown := (e => startResizing(ResizeCorner.BottomLeft, e))
        ),
        div(
          className := "resize-handle bottom-right",
          onMouseDown := (e => startResizing(ResizeCorner.BottomRight, e))
        ),
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
