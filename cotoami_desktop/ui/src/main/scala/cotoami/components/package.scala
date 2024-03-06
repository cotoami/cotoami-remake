package cotoami

import slinky.core.facade.{ReactElement, Fragment}
import slinky.web.html._

package object components {

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }

  def material_symbol(name: String): ReactElement =
    span(className := "material-symbols")(name)

  def paneToggle(paneName: String, dispatch: Msg => Unit): ReactElement =
    Fragment(
      button(
        className := "fold icon",
        title := "Fold",
        onClick := ((e) => dispatch(TogglePane(paneName)))
      )(
        span(className := "material-symbols")("arrow_left")
      ),
      button(
        className := "unfold icon",
        title := "Unfold",
        onClick := ((e) => dispatch(TogglePane(paneName)))
      )(
        span(className := "material-symbols")("arrow_right")
      )
    )
}
