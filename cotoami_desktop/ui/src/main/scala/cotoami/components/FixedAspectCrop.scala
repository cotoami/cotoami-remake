package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react

@react object FixedAspectCrop extends ExternalComponent {
  case class Props(
      image: String,
      onCropChange: Position => Unit,
      crop: Position = position(0, 0),
      aspect: Option[Double] = None,
      onMediaLoaded: Option[() => Unit] = None,
      onCropComplete: Option[(Area, Area) => Unit] = None
  )

  @js.native
  trait Position extends js.Object {
    val x: Double = js.native
    val y: Double = js.native
  }

  @js.native
  trait Area extends js.Object {
    val x: Double = js.native
    val y: Double = js.native
    val width: Double = js.native
    val height: Double = js.native
  }

  def position(x: Int, y: Int): Position =
    js.Dynamic.literal(x = x, y = y).asInstanceOf[Position]

  override val component = ReactEasyCrop
}

@js.native
@JSImport("react-easy-crop", JSImport.Default)
object ReactEasyCrop extends js.Object
