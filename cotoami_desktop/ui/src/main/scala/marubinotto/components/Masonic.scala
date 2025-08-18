package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react

@react object MasonicMasonry extends ExternalComponent {
  case class Props(
      items: js.Array[js.Object],
      render: js.Any,
      columnWidth: Option[Int] = None
  )

  override val component = masonic.Masonry
}

@js.native
@JSImport("masonic", JSImport.Namespace)
object masonic extends js.Object {
  @js.native
  object Masonry extends js.Object

  @js.native
  object MasonryScroller extends js.Object

  @js.native
  object List extends js.Object
}
