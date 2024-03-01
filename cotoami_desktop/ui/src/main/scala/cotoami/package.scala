import slinky.core.facade.{ReactElement, Fragment}
import slinky.web.html._

import cotoami.backend.SystemInfo

package object cotoami {

  /////////////////////////////////////////////////////////////////////////////
  // Msg
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg
  case class ErrorTest(result: Either[backend.Error, String]) extends Msg
  case object ToggleLogView extends Msg
  case class SystemInfoFetched(result: Either[Unit, SystemInfo]) extends Msg
  case object SelectDirectory extends Msg
  case class DirectorySelected(result: Either[Throwable, Option[String]])
      extends Msg

  case class UiStateRestored(state: Option[Model.UiState]) extends Msg
  case class TogglePane(name: String) extends Msg
  case class ResizePane(name: String, newSize: Int) extends Msg

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

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
