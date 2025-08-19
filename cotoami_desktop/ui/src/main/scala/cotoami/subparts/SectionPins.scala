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

  sealed trait Layout {
    def name: String
    def displaysCotonomaContent: Boolean
  }

  object Layout {
    case object Document extends Layout {
      override def name = toString()
      override def displaysCotonomaContent: Boolean = true
    }

    case object Columns extends Layout {
      override def name = toString()
      override def displaysCotonomaContent: Boolean = false
    }

    case class Masonry(columnWidth: Int = 300) extends Layout {
      override def name = "Masonry"
      override def toString(): String = s"$name:$columnWidth"
      override def displaysCotonomaContent: Boolean = true
    }

    val variantNames =
      Seq(Document.toString, Columns.toString, classOf[Masonry].getSimpleName)

    def fromString(s: String): Option[Layout] = s match {
      case "Document" => Some(Document)
      case "Columns"  => Some(Columns)
      case "Masonry"  => Some(Masonry())
      case m if m.startsWith("Masonry:") =>
        m.stripPrefix("Masonry:").toIntOption.map(Masonry(_))
      case _ => None
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Update
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg extends Into[AppMsg] {
    def into = AppMsg.SectionPinsMsg(this)
  }

  object Msg {
    case class SwitchLayout(cotonoma: Id[Cotonoma], layout: Layout) extends Msg
    case class SetMasonryColumnWidth(cotonoma: Id[Cotonoma], columnWidth: Int)
        extends Msg
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

      case Msg.SetMasonryColumnWidth(cotonoma, columnWidth) =>
        context.uiState
          .map(_.setPinsLayout(cotonoma, Layout.Masonry(columnWidth)).pipe {
            state => default.copy(_1 = Some(state), _2 = state.save)
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
        className := s"body ${layout.name.toLowerCase()}-layout",
        id := PinsBodyId
      )(
        ScrollArea(scrollableClassName = Some("scrollable-pins"))(
          Option.when(layout.displaysCotonomaContent) {
            sectionCotonomaContent(cotonomaCoto)
          },
          layout match {
            case Layout.Document =>
              DocumentLayout(
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

            case Layout.Masonry(columnWidth) =>
              MasonryLayout(pins, cotonoma.id, columnWidth)
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
          Layout.Masonry(),
          layout,
          "browse",
          "Masonry"
        )
      )
    )

  private def sectionCotonomaContent(
      cotonomaCoto: Coto
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    PartsCoto.sectionCotonomaContent(cotonomaCoto).map(
      div(
        className := "cotonoma-content",
        onDoubleClick := (_ => dispatch(AppMsg.FocusCoto(cotonomaCoto.id)))
      )(_)
    )

  private def buttonLayout(
      cotonomaId: Id[Cotonoma],
      layout: Layout,
      currentLayout: Layout,
      symbol: String,
      tip: String
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement = {
    val selected = layout.name == currentLayout.name
    toolButton(
      symbol = symbol,
      tip = Some(tip),
      classes = optionalClasses(
        Seq(
          (s"layout-${layout.name.toLowerCase()}", true),
          ("selected", selected)
        )
      ),
      disabled = selected,
      onClick = _ => dispatch(Msg.SwitchLayout(cotonomaId, layout))
    )
  }
}
