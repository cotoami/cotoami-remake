import slinky.core.facade.{ReactElement, Fragment}
import slinky.web.html._

import cats.effect.IO

import fui.FunctionalUI._

package object cotoami {

  def log_debug(message: String, details: Option[String] = None): Cmd[Msg] =
    IO { Some(AddLogEntry(Log.Debug, message, details)) }
  def log_info(message: String, details: Option[String] = None): Cmd[Msg] =
    IO { Some(AddLogEntry(Log.Info, message, details)) }
  def log_warn(message: String, details: Option[String] = None): Cmd[Msg] =
    IO { Some(AddLogEntry(Log.Warn, message, details)) }
  def log_error(message: String, details: Option[String] = None): Cmd[Msg] =
    IO { Some(AddLogEntry(Log.Error, message, details)) }

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }

  def icon(name: String): ReactElement =
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
