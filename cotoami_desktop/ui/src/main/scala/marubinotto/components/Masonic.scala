package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement

@react object MasonicMasonry extends ExternalComponent {
  case class Props(
      items: js.Array[scala.Any],
      render: RenderComponentProps => ReactElement,
      columnWidth: Option[Int] = Some(240),
      columnGutter: Option[Int] = Some(0)
  )
  override val component = masonic.Masonry
}

@js.native
trait RenderComponentProps extends js.Object {
  val index: Int = js.native
  val width: Double = js.native
  val data: scala.Any = js.native
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
