package cotoami

import slinky.core.facade.ReactElement
import slinky.web.html._

import cotoami.backend.Node

package object subparts {

  def modal(model: Model, dispatch: Msg => Unit): Option[ReactElement] =
    if (model.domain.nodes.local.isEmpty) {
      model.systemInfo.map(info =>
        ModalWelcome(
          model.modalWelcome,
          info.recent_databases.toSeq,
          dispatch
        )
      )
    } else {
      None
    }

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
}
