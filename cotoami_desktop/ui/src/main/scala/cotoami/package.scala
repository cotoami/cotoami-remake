import slinky.core.facade.{ReactElement, Fragment}
import slinky.web.html._

package object cotoami {

  /////////////////////////////////////////////////////////////////////////////
  // Model
  /////////////////////////////////////////////////////////////////////////////

  case class Model(
      uiState: UiState = UiState()
  )

  case class UiState(
      paneToggles: Map[String, Boolean] = Map(),
      paneSizes: Map[String, Int] = Map()
  ) {
    def paneOpened(name: String): Boolean =
      this.paneToggles.getOrElse(name, true)

    def togglePane(name: String): UiState =
      this.copy(paneToggles =
        this.paneToggles + (name -> !this.paneOpened(name))
      )

    def resizePane(name: String, newSize: Int): UiState =
      this.copy(paneSizes = this.paneSizes + (name -> newSize))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Msg
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Msg
  case class TogglePane(name: String) extends Msg
  case class ResizePane(name: String, newSize: Int) extends Msg

  /////////////////////////////////////////////////////////////////////////////
  // View
  /////////////////////////////////////////////////////////////////////////////

  def optionalClasses(classes: Seq[(String, Boolean)]): String = {
    classes.filter(_._2).map(_._1).mkString(" ")
  }

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
