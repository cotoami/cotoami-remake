package cotoami

import slinky.core.facade.ReactElement
import slinky.web.html._
import slinky.web.SyntheticKeyboardEvent

import cotoami.backend.Node
import cotoami.repositories.ParentStatus
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

  case class ParentStatusParts(
      status: ParentStatus,
      slug: String,
      icon: ReactElement,
      message: Option[String]
  )

  def parentStatusParts(status: ParentStatus): Option[ParentStatusParts] =
    status match {
      case ParentStatus.Disabled =>
        Some(
          ParentStatusParts(
            status,
            "disabled",
            materialSymbol("link_off"),
            None
          )
        )
      case ParentStatus.Connecting(message) =>
        Some(
          ParentStatusParts(
            status,
            "connecting",
            span(className := "busy", aria - "busy" := "true")(),
            message
          )
        )
      case ParentStatus.InitFailed(message) =>
        Some(
          ParentStatusParts(
            status,
            "init-failed",
            materialSymbol("error"),
            Some(message)
          )
        )
      case ParentStatus.Disconnected(message) =>
        Some(
          ParentStatusParts(
            status,
            "disconnected",
            materialSymbol("do_not_disturb_on"),
            message
          )
        )
      case _ => None
    }
}
