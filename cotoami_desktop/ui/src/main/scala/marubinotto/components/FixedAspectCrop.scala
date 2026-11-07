package marubinotto.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom

import cats.effect.IO

import marubinotto.react._

import marubinotto.fui.Browser

object FixedAspectCrop extends ExternalComponent {
  case class Props(
      image: String,
      onCropChange: Position => Unit,
      crop: Position = position(0, 0),
      aspect: Option[Double] = None,
      onMediaLoaded: Option[() => Unit] = None,
      onCropComplete: Option[(Area, Area) => Unit] = None
  ) derives PropsEncoder

  def apply(
      image: String,
      onCropChange: Position => Unit,
      crop: Position = position(0, 0),
      aspect: Option[Double] = None,
      onMediaLoaded: Option[() => Unit] = None,
      onCropComplete: Option[(Area, Area) => Unit] = None
  ) =
    super.apply(
      Props(
        image,
        onCropChange,
        crop,
        aspect,
        onMediaLoaded,
        onCropComplete
      )
    )

  override val component = ReactEasyCrop

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

  def getCroppedImg(imageUrl: String, crop: Area): IO[dom.Blob] =
    Browser.createImage(imageUrl).flatMap { image =>
      IO {
        val canvas = dom.document.createElement("canvas")
          .asInstanceOf[dom.HTMLCanvasElement]
        val ctx = canvas.getContext("2d")
        if (ctx == null)
          throw new RuntimeException("2D context is not supported.")
        canvas.width = crop.width.toInt
        canvas.height = crop.height.toInt
        ctx.asInstanceOf[dom.CanvasRenderingContext2D].drawImage(
          image,
          crop.x,
          crop.y,
          crop.width,
          crop.height,
          0,
          0,
          crop.width,
          crop.height
        )
        canvas
      }.flatMap { canvas =>
        IO.async_[dom.Blob] { complete =>
          canvas.asInstanceOf[ToBlob].toBlob { blob =>
            if (blob == null)
              complete(Left(new RuntimeException("Image encoding failed.")))
            else complete(Right(blob))
          }
        }
      }
    }

  // Workaround for `org.scalajs.dom.HTMLCanvasElement` not supporting the `toBlob` method as of v2.8.0.
  // https://javadoc.io/static/org.scala-js/scalajs-dom_sjs1_2.13/2.8.0/org/scalajs/dom/HTMLCanvasElement.html
  @js.native
  trait ToBlob extends js.Object {
    def toBlob(callback: js.Function1[dom.Blob, Unit]): Unit = js.native
  }
}

@js.native
@JSImport("react-easy-crop", JSImport.Default)
object ReactEasyCrop extends js.Object
