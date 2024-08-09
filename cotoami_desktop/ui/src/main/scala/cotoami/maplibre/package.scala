package cotoami

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom

package object maplibre {

  @js.native
  @JSImport("maplibre-gl", "Map")
  class Map(options: MapOptions) extends js.Object

  trait MapOptions extends js.Object {
    // The HTML element in which MapLibre GL JS will render the map,
    // or the element's string id. The specified element must have no children.
    val container: js.UndefOr[dom.HTMLElement | String] = js.undefined

    // The map's MapLibre style.
    // This must be a JSON object conforming to the schema described in
    // the MapLibre Style Specification, or a URL to such JSON. When the style is
    // not specified, calling Map#setStyle is required to render the map.
    val style: js.UndefOr[String] = js.undefined

    // The initial zoom level of the map. If zoom is not specified in the
    // constructor options, MapLibre GL JS will look for it in the map's style
    // object. If it is not specified in the style, either, it will default to 0.
    val zoom: js.UndefOr[Int] = js.undefined

    // The initial geographical centerpoint of the map.
    // If center is not specified in the constructor options,
    // MapLibre GL JS will look for it in the map's style object.
    // If it is not specified in the style, either, it will default to [0, 0]
    val center: js.UndefOr[js.Tuple2[Double, Double]] = js.undefined
  }
}
