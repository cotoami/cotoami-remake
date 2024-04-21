package cotoami

import slinky.core.facade.ReactElement
import slinky.web.html._

package object components {

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }

  def materialSymbol(name: String, classNames: String = ""): ReactElement =
    span(className := s"material-symbols ${classNames}")(name)
}
