package cotoami.components

import scala.scalajs.js

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.maplibre._

@react object MapLibre {
  case class Props(
      id: String,
      defaultPosition: (Double, Double)
  )

  val component = FunctionalComponent[Props] { props =>
    useEffect(
      () => {
        println("MapLibre effect")
        val map = new Map(new MapOptions {
          override val container = props.id
          override val zoom = 6
          override val center = js.Tuple2.fromScalaTuple2(props.defaultPosition)
          override val style =
            "https://tile.openstreetmap.jp/styles/osm-bright-en/style.json"
        })
      },
      Seq.empty
    )

    println("MapLibre render")

    section(id := props.id, className := "map")()
  }
}
