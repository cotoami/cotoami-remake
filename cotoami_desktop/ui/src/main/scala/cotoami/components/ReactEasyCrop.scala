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
      onCropChange: Position => Unit,
      onMediaLoaded: Option[() => Unit] = None,
      crop: Position = position(0, 0),
      aspect: Option[Double] = None
  )

  @js.native
  trait Position extends js.Object {
    val x: Int = js.native
    val y: Int = js.native
  }

  def position(x: Int, y: Int): Position =
    js.Dynamic.literal(x = x, y = y).asInstanceOf[Position]

  override val component = ReactEasyCrop
}
