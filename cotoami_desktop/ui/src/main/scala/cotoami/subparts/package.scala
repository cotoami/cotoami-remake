package cotoami

import slinky.core.facade.ReactElement
import slinky.web.html._
import slinky.web.SyntheticKeyboardEvent

import cotoami.backend.{Node, NotConnected}
import cotoami.repositories.Nodes
import cotoami.components.materialSymbol

package object subparts {

  def nodeImg(node: Node): ReactElement =
    img(
      className := "node-icon",
      alt := node.name,
      src := s"data:image/png;base64,${node.icon}"
    )

  sealed trait CollapseDirection
  case object ToLeft extends CollapseDirection
  case object ToRight extends CollapseDirection

  def paneToggle(
      paneName: String,
      dispatch: Msg => Unit,
      direction: CollapseDirection = ToLeft
  ): ReactElement =
    div(className := "pane-toggle")(
      button(
        className := "fold default",
        title := "Fold",
        onClick := (_ => dispatch(OpenOrClosePane(paneName, false)))
      )(
        span(className := "material-symbols")(
          direction match {
            case ToLeft  => "arrow_left"
            case ToRight => "arrow_right"
          }
        )
      ),
      button(
        className := "unfold default",
        title := "Unfold",
        onClick := (_ => dispatch(OpenOrClosePane(paneName, true)))
      )(
        span(className := "material-symbols")(
          direction match {
            case ToLeft  => "arrow_right"
            case ToRight => "arrow_left"
          }
        )
      )
    )

  val EnterKey = "Enter"

  def detectCtrlEnter[T](e: SyntheticKeyboardEvent[T]): Boolean =
    e.key == EnterKey && (e.ctrlKey || e.metaKey)

  case class ParentStatus(
      name: String,
      icon: ReactElement,
      message: Option[String] = None
  )

  def parentStatus(
      node: Node,
      nodes: Nodes
  ): Option[ParentStatus] =
    nodes.getServer(node.id).flatMap(_.notConnected.map {
      case NotConnected.Disabled =>
        ParentStatus("disabled", materialSymbol("sync_disabled"))
      case NotConnected.Connecting(details) =>
        ParentStatus(
          "connecting",
          span(className := "busy", aria - "busy" := "true")(),
          details
        )
      case NotConnected.InitFailed(details) =>
        ParentStatus("error", materialSymbol("error"), Some(details))
      case NotConnected.Disconnected(details) =>
        ParentStatus(
          "disconnected",
          materialSymbol("do_not_disturb_on"),
          details
        )
    })
}
