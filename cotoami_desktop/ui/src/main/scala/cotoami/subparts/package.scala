package cotoami

import slinky.core.facade.ReactElement
import slinky.web.html._
import slinky.web.SyntheticKeyboardEvent

import cotoami.{Msg => AppMsg}
import cotoami.models.{Node, ParentStatus}
import cotoami.components.materialSymbol

package object subparts {

  def imgNode(node: Node, additionalClasses: String = ""): ReactElement =
    img(
      className := s"node-icon ${additionalClasses}",
      alt := node.name,
      src := node.iconUrl
    )

  def spanNode(node: Node): ReactElement =
    span(className := "node")(
      imgNode(node),
      span(className := "name")(node.name)
    )

  def buttonHelp(disable: Boolean, onButtonClick: () => Unit): ReactElement =
    button(
      className := s"default help",
      disabled := disable,
      onClick := onButtonClick
    )(
      materialSymbol("help")
    )

  def sectionHelp(
      display: Boolean,
      onCloseClick: () => Unit,
      contents: ReactElement*
  ): ReactElement =
    Option.when(display) {
      section(className := "help")(
        button(
          className := "close default",
          onClick := onCloseClick
        ),
        contents
      )
    }

  sealed trait CollapseDirection
  case object ToLeft extends CollapseDirection
  case object ToRight extends CollapseDirection

  def paneToggle(
      paneName: String,
      direction: CollapseDirection = ToLeft
  )(implicit dispatch: AppMsg => Unit): ReactElement =
    div(className := "pane-toggle")(
      button(
        className := "fold default",
        title := "Fold",
        onClick := (_ => dispatch(AppMsg.OpenOrClosePane(paneName, false)))
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
        onClick := (_ => dispatch(AppMsg.OpenOrClosePane(paneName, true)))
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
