package cotoami.subparts

import scala.util.chaining._

import org.scalajs.dom
import org.scalajs.dom.HTMLElement

import cats.effect.IO

import slinky.core.facade.ReactElement
import slinky.web.html._

import marubinotto.optionalClasses
import marubinotto.fui.Cmd
import marubinotto.components.{toolButton, ScrollArea}

import cotoami.{Context, Into, Msg => AppMsg}
import cotoami.models.{Coto, Cotonoma, Id, Ito, Siblings, UiState}
import cotoami.subparts.pins._

object SectionPins {

  sealed trait Layout
  object Layout {
    case object Document extends Layout
    case object Columns extends Layout
    case object Masonry extends Layout

    val variants = Seq(Document, Columns, Masonry)

    def fromString(s: String): Option[Layout] =
      variants.find(_.toString == s)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionPinsMsg(this)
  }

  object Msg {
    case class SwitchLayout(cotonoma: Id[Cotonoma], layout: Layout) extends Msg
    case class ScrollToPin(pin: Ito) extends Msg
  }

  def update(msg: Msg)(implicit
      context: Context
  ): (Option[UiState], Cmd[AppMsg]) = {
    val default = (context.uiState, Cmd.none)
    msg match {
      case Msg.SwitchLayout(cotonoma, layout) =>
        context.uiState
          .map(_.setPinsLayout(cotonoma, layout).pipe { state =>
            default.copy(_1 = Some(state), _2 = state.save)
          })
          .getOrElse(default)

      case Msg.ScrollToPin(pin) =>
        default.copy(
          _2 = Cmd(
            IO {
              dom.document.getElementById(elementIdOfPin(pin)) match {
                case element: HTMLElement =>
                  element.scrollIntoView(true)
                case _ => ()
              }
              None
            }
          )
        )
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  final val PinsBodyId = "pins-body"

  def apply(
      uiState: UiState
  )(implicit
      context: Context,
      dispatch: Into[AppMsg] => Unit
  ): Option[ReactElement] = {
    (context.repo.pins, context.repo.currentCotonomaPair) match {
      case (Some(pins), Some(cotonoma)) =>
        Some(sectionPins(pins, uiState, cotonoma))
      case _ => None
    }
  }

  private def sectionPins(
      pins: Siblings,
      uiState: UiState,
      currentCotonoma: (Cotonoma, Coto)
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement = {
    val (cotonoma, cotonomaCoto) = currentCotonoma
    val layout = uiState.pinsLayout(cotonoma.id).getOrElse(Layout.Document)
    section(className := "pins header-and-body fill")(
      header(cotonoma, cotonomaCoto, layout),
      div(
        className := optionalClasses(
          Seq(
            ("body", true),
            (s"${layout.toString().toLowerCase()}-layout", true)
          )
        ),
        id := PinsBodyId
      )(
        ScrollArea(scrollableClassName = Some("scrollable-pins"))(
          layout match {
            case Layout.Document =>
              DocumentLayout(
                cotonomaCoto = cotonomaCoto,
                pins = pins,
                viewportId = PinsBodyId,
                context = context,
                dispatch = dispatch
              )

            case Layout.Columns =>
              sectionPinnedCotos(pins) { subCotos =>
                ScrollArea(className = Some("scrollable-sub-cotos"))(
                  cotoami.subparts.pins.sectionSubCotos(subCotos)
                )
              }

            case Layout.Masonry =>
              div()()
          }
        )
      )
    )
  }

  private def header(
      cotonoma: Cotonoma,
      cotonomaCoto: Coto,
      layout: Layout
  )(implicit context: Context, dispatch: Into[AppMsg] => Unit): ReactElement =
    slinky.web.html.header()(
      ToolbarCoto(cotonomaCoto),
      section(className := "title")(
        span(
          className := "current-cotonoma-name",
          onDoubleClick := (_ => dispatch(AppMsg.FocusCoto(cotonomaCoto.id)))
        )(
          context.repo.nodes.get(cotonoma.nodeId).map(PartsNode.imgNode(_)),
          cotonoma.name
        )
      ),
      section(className := "layout-switch")(
        buttonLayout(
          cotonoma.id,
          Layout.Document,
          layout,
          "view_agenda",
          "Document"
        ),
        buttonLayout(
          cotonoma.id,
          Layout.Columns,
          layout,
          "view_column",
          "Columns"
        ),
        buttonLayout(
          cotonoma.id,
          Layout.Masonry,
          layout,
          "browse",
          "Masonry"
        )
      )
    )

  private def buttonLayout(
      cotonomaId: Id[Cotonoma],
      layout: Layout,
      currentLayout: Layout,
      symbol: String,
      tip: String
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    toolButton(
      symbol = symbol,
      tip = Some(tip),
      classes = optionalClasses(
        Seq(
          (s"layout-${layout.toString().toLowerCase()}", true),
          ("selected", layout == currentLayout)
        )
      ),
      disabled = layout == currentLayout,
      onClick = _ => dispatch(Msg.SwitchLayout(cotonomaId, layout))
    )
}
