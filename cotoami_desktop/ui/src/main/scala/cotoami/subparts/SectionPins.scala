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

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionPinsMsg(this)
  }

  object Msg {
    case class SwitchView(cotonoma: Id[Cotonoma], inColumns: Boolean)
        extends Msg
    case class ScrollToPin(pin: Ito) extends Msg
  }

  def update(msg: Msg)(implicit
      context: Context
  ): (Option[UiState], Cmd[AppMsg]) = {
    val default = (context.uiState, Cmd.none)
    msg match {
      case Msg.SwitchView(cotonoma, inColumns) =>
        context.uiState
          .map(_.setPinsInColumns(cotonoma, inColumns).pipe { state =>
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
    val inColumns = uiState.arePinsInColumns(cotonoma.id)
    section(className := "pins header-and-body fill")(
      header(cotonoma, cotonomaCoto, inColumns),
      div(
        className := optionalClasses(
          Seq(
            ("body", true),
            ("document-view", !inColumns),
            ("column-view", inColumns)
          )
        ),
        id := PinsBodyId
      )(
        ScrollArea(scrollableClassName = Some("scrollable-pins"))(
          if (inColumns)
            sectionPinnedCotos(pins) { subCotos =>
              ScrollArea(className = Some("scrollable-sub-cotos"))(
                cotoami.subparts.pins.sectionSubCotos(subCotos)
              )
            }
          else
            DocumentView(
              cotonomaCoto = cotonomaCoto,
              pins = pins,
              viewportId = PinsBodyId,
              context = context,
              dispatch = dispatch
            )
        )
      )
    )
  }

  private def header(
      cotonoma: Cotonoma,
      cotonomaCoto: Coto,
      inColumns: Boolean
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
      section(className := "view-switch")(
        toolButton(
          symbol = "view_agenda",
          tip = Some("Document"),
          classes = optionalClasses(
            Seq(
              ("view-document", true),
              ("selected", !inColumns)
            )
          ),
          disabled = !inColumns,
          onClick = _ => dispatch(Msg.SwitchView(cotonoma.id, false))
        ),
        toolButton(
          symbol = "view_column",
          tip = Some("Columns"),
          classes = optionalClasses(
            Seq(
              ("view-columns", true),
              ("selected", inColumns)
            )
          ),
          disabled = inColumns,
          onClick = _ => dispatch(Msg.SwitchView(cotonoma.id, true))
        )
      )
    )
}
