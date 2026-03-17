package marubinotto.components

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.html

import slinky.core._
import slinky.core.facade.ReactElement
import slinky.core.facade.Hooks._
import slinky.web.html._

object Masonry {
  case class RenderProps(
      index: Int,
      width: Double,
      data: scala.Any
  )

  case class Props(
      items: js.Array[scala.Any],
      itemKey: js.Function2[js.Any, Int, String] = (_, index) =>
        index.toString(),
      render: RenderProps => ReactElement,
      columnWidth: Int = 240,
      columnGutter: Option[Int] = Some(0),
      rowGutter: Option[Int] = None,
      className: Option[String] = None
  )

  def apply(
      items: js.Array[scala.Any],
      itemKey: js.Function2[js.Any, Int, String] = (_, index) =>
        index.toString(),
      render: RenderProps => ReactElement,
      columnWidth: Int = 240,
      columnGutter: Option[Int] = Some(0),
      rowGutter: Option[Int] = None,
      className: Option[String] = None
  ) =
    component(
      Props(
        items,
        itemKey,
        render,
        columnWidth,
        columnGutter,
        rowGutter,
        className
      )
    )

  private case class ItemLayout(
      top: Double,
      left: Double,
      height: Double
  )

  private case class LayoutState(
      columnCount: Int,
      itemWidth: Double,
      height: Double,
      items: Vector[ItemLayout],
      ready: Boolean
  )

  private val EmptyLayout =
    LayoutState(1, 0, 0, Vector.empty, ready = false)

  val component = FunctionalComponent[Props] { props =>
    val rootRef = useRef[html.Div](null)
    val (containerWidth, setContainerWidth) = useState(0.0)
    val (measurementVersion, setMeasurementVersion) = useState(0)
    val (layout, setLayout) = useState(EmptyLayout)

    val keysSignature =
      props.items.zipWithIndex
        .map((item, index) => props.itemKey(item.asInstanceOf[js.Any], index))
        .mkString("|")

    useEffect(
      () => {
        val root = rootRef.current
        if (root == null) () => ()
        else {
          val updateWidth = () => {
            val width = root.clientWidth.toDouble
            setContainerWidth(current =>
              if ((current - width).abs < 0.5) current else width
            )
          }

          updateWidth()

          val observer = new dom.ResizeObserver((_, _) => updateWidth())
          observer.observe(root)
          () => observer.disconnect()
        }
      },
      Seq.empty
    )

    useEffect(
      () => {
        val root = rootRef.current
        if (root == null) () => ()
        else {
          val refreshLayout = () =>
            setMeasurementVersion(current => current + 1)

          refreshLayout()

          val observer = new dom.ResizeObserver((_, _) => refreshLayout())
          root.querySelectorAll(".masonry-item")
            .foreach(observer.observe)

          () => observer.disconnect()
        }
      },
      Seq(
        props.items.length,
        containerWidth,
        props.columnWidth,
        props.columnGutter.getOrElse(0),
        props.rowGutter.getOrElse(0),
        keysSignature
      )
    )

    useEffect(
      () => {
        val root = rootRef.current
        if (root == null || containerWidth <= 0) ()
        else {
          val heights = Array.fill(props.items.length)(0.0)
          root.querySelectorAll(".masonry-item")
            .foreach(node => {
              val element = node.asInstanceOf[html.Div]
              Option(element.getAttribute("data-index"))
                .flatMap(_.toIntOption)
                .foreach(index => {
                  if (index >= 0 && index < heights.length)
                    heights(index) = element.offsetHeight.toDouble
                })
            })

          setLayout(
            calculateLayout(
              containerWidth = containerWidth,
              preferredColumnWidth = props.columnWidth.toDouble,
              columnGutter = props.columnGutter.getOrElse(0).toDouble,
              rowGutter = props.rowGutter.getOrElse(0).toDouble,
              itemHeights = heights.toVector
            )
          )
        }
      },
      Seq(
        containerWidth,
        measurementVersion,
        props.items.length,
        props.columnWidth,
        props.columnGutter.getOrElse(0),
        props.rowGutter.getOrElse(0),
        keysSignature
      )
    )

    val itemWidth =
      if (layout.itemWidth > 0) layout.itemWidth
      else containerWidth.max(0)

    div(
      className := s"masonry ${props.className.getOrElse("")}".trim,
      ref := rootRef,
      style := js.Dynamic.literal(
        position = "relative",
        width = "100%",
        height = s"${layout.height}px"
      )
    )(
      props.items.zipWithIndex.map { case (item, index) =>
        val itemLayout =
          layout.items.lift(index).getOrElse(ItemLayout(0, 0, 0))
        div(
          key := props.itemKey(item.asInstanceOf[js.Any], index),
          className := "masonry-item",
          data - "index" := index.toString,
          style := js.Dynamic.literal(
            position = "absolute",
            width = s"${itemWidth}px",
            top = s"${itemLayout.top}px",
            left = s"${itemLayout.left}px",
            opacity = if (layout.ready) 1 else 0
          )
        )(
          props.render(RenderProps(index, itemWidth, item))
        )
      }.toSeq*
    )
  }

  private def calculateLayout(
      containerWidth: Double,
      preferredColumnWidth: Double,
      columnGutter: Double,
      rowGutter: Double,
      itemHeights: Vector[Double]
  ): LayoutState = {
    val safeColumnWidth = preferredColumnWidth.max(1)
    val columnCount =
      (((containerWidth + columnGutter) / (safeColumnWidth + columnGutter)).floor
        .toInt)
        .max(1)
    val computedItemWidth =
      ((containerWidth - ((columnCount - 1) * columnGutter)) / columnCount)
        .max(0)

    val columnHeights = Array.fill(columnCount)(0.0)
    val itemLayouts = itemHeights.map { height =>
      val columnIndex = shortestColumnIndex(columnHeights)
      val top = columnHeights(columnIndex)
      val left = columnIndex * (computedItemWidth + columnGutter)
      columnHeights(columnIndex) += height + rowGutter
      ItemLayout(top, left, height)
    }

    val totalHeight =
      if (itemLayouts.isEmpty) 0.0
      else columnHeights.max - rowGutter

    LayoutState(
      columnCount = columnCount,
      itemWidth = computedItemWidth,
      height = totalHeight.max(0),
      items = itemLayouts,
      ready = itemLayouts.length == itemHeights.length
    )
  }

  private def shortestColumnIndex(columnHeights: Array[Double]): Int =
    columnHeights.indices.minBy(columnHeights)
}
