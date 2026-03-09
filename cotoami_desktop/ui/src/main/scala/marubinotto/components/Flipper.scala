package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.facade.ReactElement

object Flipper extends ExternalComponent {
  case class Props(
      element: String,
      className: String,
      flipKey: String
  )

  def apply(
      element: String,
      className: String,
      flipKey: String
  )(children: ReactElement*) =
    super.apply(Props(element, className, flipKey))(children*)

  override val component = ReactFlipToolkit.Flipper
}

object Flipped extends ExternalComponent {
  case class Props(
      flipId: String
  )

  def apply(
      key: String,
      flipId: String
  )(children: ReactElement*) =
    super.apply(Props(flipId)).withKey(key).apply(children*)

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
