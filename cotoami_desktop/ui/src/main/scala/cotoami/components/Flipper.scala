package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement

@react object Flipper extends ExternalComponent {
  case class Props(
      element: String,
      className: String,
      flipKey: String,
      children: ReactElement*
  )

  override val component = ReactFlipToolkit.Flipper
}

@react object Flipped extends ExternalComponent {
  case class Props(
      key: String,
      flipId: String,
      children: ReactElement*
  )

  override val component = ReactFlipToolkit.Flipped
}

@js.native
@JSImport("react-flip-toolkit", JSImport.Namespace)
object ReactFlipToolkit extends js.Object {
  @js.native
  object Flipper extends js.Object

  @js.native
  object Flipped extends js.Object
}
