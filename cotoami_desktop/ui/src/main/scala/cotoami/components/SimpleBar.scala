package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement

@js.native
@JSImport("simplebar-react", JSImport.Default)
object SimpleBarReact extends js.Object

@js.native
@JSImport("simplebar-react/dist/simplebar.min.css", JSImport.Namespace)
object SimpleBarCSS extends js.Object

@react object SimpleBar extends ExternalComponent {
  val css = SimpleBarCSS

  case class Props(
      autoHide: Boolean,
      children: ReactElement*
  )
  override val component = SimpleBarReact
}
