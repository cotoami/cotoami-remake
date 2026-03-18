package cotoami.subparts.modeless

import scala.scalajs.js
import scala.util.chaining._
import org.scalajs.dom
import org.scalajs.dom.html

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.core.facade.Hooks._
import slinky.web.SyntheticMouseEvent
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.{Browser, Cmd}
import marubinotto.components.materialSymbol

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma}
import cotoami.backend.{CotoBackend, ErrorJson}
import cotoami.subparts.EditorCoto._
import cotoami.subparts.SectionGeomap.{Model => Geomap}

object ModelessNewCoto {

  private case class Position(left: Double, top: Double)
  private case class DragState(
      mouseX: Double,
      mouseY: Double,
      left: Double,
      top: Double
  )
  private case class PanelBounds(
      width: Double,
      height: Double
  )

  private def clampPosition(
      position: Position,
      bounds: PanelBounds
  ): Position = {
    val maxLeft = (dom.window.innerWidth - bounds.width).max(0.0)
    val maxTop = (dom.window.innerHeight - bounds.height).max(0.0)
    Position(
      position.left.max(0.0).min(maxLeft),
      position.top.max(0.0).min(maxTop)
    )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      cotoForm: CotoForm.Model = CotoForm.Model(),
      posting: Boolean = false,
      error: Option[String] = None
  ) {
    def readyToPost: Boolean =
      !posting && cotoForm.hasValidContents

    def post(postTo: Cotonoma): (Model, Cmd.One[AppMsg]) =
      (
        copy(posting = true),
        CotoBackend.post(cotoForm.toBackendInput, postTo.id)
          .map(Msg.Posted(_).into)
      )
  }

  object Model {
    def apply(cotoForm: CotoForm.Model): (Model, Cmd[AppMsg]) =
      (
        Model(cotoForm = cotoForm),
        cotoForm.scanMediaMetadata.map(Msg.CotoFormMsg.apply).map(_.into)
      )
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    override def into: AppMsg = AppMsg.ModelessNewCotoMsg(this)
  }

  object Msg {
    case class Open(model: Model) extends Msg
    case object Close extends Msg
    case class CotoFormMsg(submsg: CotoForm.Msg) extends Msg
    case object Post extends Msg
    case class Posted(result: Either[ErrorJson, Coto]) extends Msg
  }

  def open(cotoForm: CotoForm.Model): Cmd[AppMsg] = {
    val (model, cmd) = Model(cotoForm)
    Browser.send(Msg.Open(model).into) ++ cmd
  }

  def close: Cmd.One[AppMsg] =
    Browser.send(Msg.Close.into)

  def update(msg: Msg, model: Model)(using
      context: Context
  ): (Option[Model], Geomap, Cmd[AppMsg]) = {
    val default = (Some(model), context.geomap, Cmd.none)
    msg match {
      case Msg.Open(opened) =>
        default.copy(_1 = Some(opened))

      case Msg.Close =>
        default.copy(_1 = None)

      case Msg.CotoFormMsg(submsg) =>
        val (form, geomap, subcmd) = CotoForm.update(submsg, model.cotoForm)
        default.copy(
          _1 = Some(model.copy(cotoForm = form)),
          _2 = geomap,
          _3 = subcmd.map(Msg.CotoFormMsg.apply).map(_.into)
        )

      case Msg.Post =>
        context.repo.currentCotonoma match {
          case Some(cotonoma) =>
            model.post(cotonoma).pipe { case (updated, cmd) =>
              default.copy(_1 = Some(updated), _3 = cmd)
            }
          case None => default
        }

      case Msg.Posted(Right(_)) =>
        default.copy(
          _1 = Some(model.copy(posting = false)),
          _3 = close
        )

      case Msg.Posted(Left(e)) =>
        default.copy(
          _1 = Some(model.copy(posting = false, error = Some(e.default_message)))
        )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  case class ViewProps(
      model: Model,
      context: Context,
      dispatch: Into[AppMsg] => Unit
  )

  def apply(model: Model)(using
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): ReactElement =
    component(ViewProps(model, context, dispatch))

  private val component = FunctionalComponent[ViewProps] { props =>
    val panelRef = useRef[html.Div](null)
    val dragRef = useRef(Option.empty[DragState])
    val (position, setPosition) = useState(Position(24, 24))

    val onMouseMove: js.Function1[dom.MouseEvent, Unit] = useCallback(
      (e: dom.MouseEvent) => {
        dragRef.current.foreach { drag =>
          val bounds = PanelBounds(
            width =
              Option(panelRef.current).map(_.offsetWidth.toDouble).getOrElse(0.0),
            height =
              Option(panelRef.current).map(_.offsetHeight.toDouble).getOrElse(0.0)
          )
          setPosition(
            clampPosition(
              Position(
                drag.left + e.clientX - drag.mouseX,
                drag.top + e.clientY - drag.mouseY
              ),
              bounds
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
        val clampToViewport: js.Function1[dom.Event, Unit] =
          (_: dom.Event) => {
            val bounds = PanelBounds(
              width =
                Option(panelRef.current).map(_.offsetWidth.toDouble).getOrElse(0.0),
              height =
                Option(panelRef.current).map(_.offsetHeight.toDouble).getOrElse(0.0)
            )
            setPosition(current => clampPosition(current, bounds))
          }

        clampToViewport(new dom.Event("resize"))
        dom.window.addEventListener("resize", clampToViewport)

        () => dom.window.removeEventListener("resize", clampToViewport)
      },
      Seq()
    )

    val startDragging = useCallback(
      (e: SyntheticMouseEvent[dom.Element]) => {
        if (e.button == 0) {
          dragRef.current = Some(
            DragState(
              mouseX = e.clientX,
              mouseY = e.clientY,
              left = position.left,
              top = position.top
            )
          )
          e.preventDefault()
        }
      },
      Seq(position.left, position.top)
    )

    div(className := "modeless-new-coto-layer")(
      div(
        className := optionalClasses(
          Seq(
            ("modeless-new-coto", true),
            ("with-media-content", props.model.cotoForm.mediaBlob.isDefined)
          )
        ),
        ref := panelRef,
        style := js.Dynamic.literal(
          left = s"${position.left}px",
          top = s"${position.top}px",
          width = "min(960px, calc(100vw - 48px))",
          height = "min(760px, calc(100vh - 48px))"
        )
      )(
        article()(
          header(
            className := "drag-handle",
            onMouseDown := (startDragging(_))
          )(
            h1()(
              span(className := "title-icon")(materialSymbol(Coto.IconName)),
              props.context.i18n.text.ModalNewCoto_title
            ),
            button(
              `type` := "button",
              className := "close default",
              onMouseDown := (e => e.stopPropagation()),
              onClick := (e => {
                e.stopPropagation()
                props.dispatch(Msg.Close)
              })
            )
          ),
          props.model.error.map(e => section(className := "error")(e)),
          div(className := "modal-body")(
            props.context.repo.currentCotonoma.map(cotonoma =>
              sectionPostTo(cotonoma)(using props.context)
            ),
            CotoForm(
              form = props.model.cotoForm,
              onCtrlEnter = Some(() => props.dispatch(Msg.Post))
            )(using
              props.context,
              (submsg: CotoForm.Msg) => props.dispatch(Msg.CotoFormMsg(submsg))
            ),
            div(className := "buttons")(
              CotoForm.buttonPreview(props.model.cotoForm)(using
                props.context,
                (submsg: CotoForm.Msg) => props.dispatch(Msg.CotoFormMsg(submsg))
              ),
              button(
                className := "post",
                disabled := !props.model.readyToPost,
                aria - "busy" := props.model.posting.toString(),
                onClick := (_ => props.dispatch(Msg.Post))
              )(
                props.context.i18n.text.Post,
                span(className := "shortcut-help")("(Ctrl + Enter)")
              )
            )
          )
        )
      )
    )
  }

  private def sectionPostTo(cotonoma: Cotonoma)(using
      context: Context
  ): ReactElement =
    section(className := "post-to")(
      span(className := "label")(s"${context.i18n.text.PostTo}:"),
      span(className := "name")(cotonoma.name)
    )
}
