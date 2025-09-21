package marubinotto.libs

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal

package object geomap {

  def basemapsStyle(
      pmtilesUrl: String,
      flavorName: String,
      language: String,
      glyphsUrl: String,
      spriteUrl: String
  ): js.Object = {
    val sourceName = "basemaps"
    val flavor = basemaps.namedFlavor(flavorName)
    val layers = basemaps.layers(
      sourceName,
      flavor,
      new basemaps.LayersOptions {
        override val lang = language
      }
    )
    literal(
      version = 8,
      name = s"Basemaps $flavorName layers ($language)",
      sources = literal(
        sourceName -> literal(
          `type` = "vector",
          attribution =
            """<a href="https://github.com/protomaps/basemaps" target="_blank">Protomaps</a> © <a href="https://openstreetmap.org" target="_blank">OpenStreetMap</a>""",
          url = pmtilesUrl
        )
      ),
      layers = layers,
      glyphs = glyphsUrl,
      sprite = spriteUrl
    )
  }
}
