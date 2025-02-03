package cotoami

import slinky.core.facade.ReactElement
import slinky.web.SyntheticMouseEvent
import slinky.web.html._

package object components {

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }

  def materialSymbol(name: String, classNames: String = ""): ReactElement =
    span(className := s"material-symbols ${classNames}")(name)

  def toolButton(
      symbol: String,
      tip: Option[String] = None,
      tipPlacement: String = "bottom",
      classes: String = "",
      disabled: Boolean = false,
      onClick: SyntheticMouseEvent[_] => Unit = (_ => ())
  ): ReactElement =
    button(
      className := s"default tool ${classes}",
      data - "tooltip" := tip,
      data - "placement" := tipPlacement,
      slinky.web.html.disabled := disabled,
      slinky.web.html.onClick := onClick
    )(
      materialSymbol(symbol)
    )
}
