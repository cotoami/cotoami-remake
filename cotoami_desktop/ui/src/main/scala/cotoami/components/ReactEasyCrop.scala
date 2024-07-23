package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react

@js.native
@JSImport("react-easy-crop", JSImport.Default)
object ReactEasyCrop extends js.Object

@react object EasyCrop extends ExternalComponent {
  case class Props(
      image: String,
      crop: Position = new Position {
        override val x = 0
        override val y = 0
      },
      aspect: Option[Float]
  )

  trait Position extends js.Object {
    val x: Int
    val y: Int
  }

  override val component = ReactEasyCrop
}
