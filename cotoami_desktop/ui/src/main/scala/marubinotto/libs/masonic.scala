package marubinotto.libs

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{ReactElement, ReactRef}

// https://github.com/jaredLunde/masonic
object masonic {

  @js.native
  @JSImport("masonic", JSImport.Namespace)
  object Masonic extends js.Object {
    @js.native
    object Masonry extends js.Object

    @js.native
    object MasonryScroller extends js.Object

    @js.native
    object List extends js.Object

    def useContainerPosition(
        ref: ReactRef[dom.HTMLElement],
        deps: Iterable[scala.Any] = Seq.empty
    ): ContainerPosition = js.native

    def usePositioner(
        options: UsePositionerOptions,
        deps: Iterable[scala.Any] = Seq.empty
    ): Positioner = js.native
  }

  @js.native
  trait RenderComponentProps extends js.Object {
    val index: Int = js.native
    val width: Double = js.native
    val data: scala.Any = js.native
  }

  @js.native
  trait ContainerPosition extends js.Object {
    val offset: Double = js.native
    val width: Double = js.native
  }

  trait UsePositionerOptions extends js.Object {
    val width: Double
    val columnWidth: js.UndefOr[Double] = js.undefined
    val columnGutter: js.UndefOr[Double] = js.undefined
    val rowGutter: js.UndefOr[Double] = js.undefined
    val columnCount: js.UndefOr[Double] = js.undefined
    val maxColumnCount: js.UndefOr[Double] = js.undefined
  }

  @js.native
  trait Positioner extends js.Object {
    val columnCount: Double = js.native
    val columnWidth: Double = js.native
    def set(index: Double, height: Double): Unit = js.native
    def get(index: Double): js.UndefOr[PositionerItem] = js.native
    def update(updates: js.Array[Double]): Unit = js.native
    def range(
        lo: Double,
        hi: Double,
        renderCallback: js.Function3[Double, Double, Double, Unit]
    ): Unit = js.native
    def size(): Double = js.native
    def estimateHeight(itemCount: Double, defaultItemHeight: Double): Double =
      js.native
    def shortestColumn(): Double = js.native
    def all(): js.Array[PositionerItem] = js.native
  }

  @js.native
  trait PositionerItem extends js.Object {
    val top: Double = js.native
    val left: Double = js.native
    val height: Double = js.native
    val column: Double = js.native
  }

  @react object Masonry extends ExternalComponent {
    case class Props(
        items: js.Array[scala.Any],
        itemKey: js.Function2[js.Any, Int, String] = (item, index) =>
          index.toString(),
        render: RenderComponentProps => ReactElement,
        columnWidth: Int = 240,
        columnGutter: Option[Int] = Some(0),
        rowGutter: Option[Int] = None,
        overscanBy: Double = 2
    )
    override val component = Masonic.Masonry
  }

  @react object MasonryScroller extends ExternalComponent {
    case class Props(
        items: js.Array[scala.Any],
        itemKey: js.Function2[js.Any, Int, String] = (item, index) =>
          index.toString(),
        render: RenderComponentProps => ReactElement,
        overscanBy: Double = 2,
        positioner: Positioner,
        offset: Double = 0,
        height: Double,
        containerRef: ReactRef[dom.HTMLElement] = null
    )
    override val component = Masonic.MasonryScroller
  }
}
