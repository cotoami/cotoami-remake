package cotoami

import slinky.core.facade.{Fragment, ReactElement}
import slinky.web.html._

import cotoami.backend.Node

package object components {

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }

  def material_symbol(name: String, classNames: String = ""): ReactElement =
    span(className := s"material-symbols ${classNames}")(name)

  def paneToggle(paneName: String, dispatch: Msg => Unit): ReactElement =
    Fragment(
      button(
        className := "fold default",
        title := "Fold",
        onClick := ((e) => dispatch(TogglePane(paneName)))
      )(
        span(className := "material-symbols")("arrow_left")
      ),
      button(
        className := "unfold default",
        title := "Unfold",
        onClick := ((e) => dispatch(TogglePane(paneName)))
      )(
        span(className := "material-symbols")("arrow_right")
      )
    )

  def node_img(node: Node): ReactElement =
    img(
      className := "node-icon",
      alt := node.name,
      src := s"data:image/png;base64,${node.icon}"
    )
}
