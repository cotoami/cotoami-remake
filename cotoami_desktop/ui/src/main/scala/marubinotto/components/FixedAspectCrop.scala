package marubinotto.components

import scala.util.{Failure, Success}
import scala.concurrent._
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import slinky.core._
import slinky.core.annotations.react

import marubinotto.fui.Browser

@react object FixedAspectCrop extends ExternalComponent {
  case class Props(
      image: String,
      onCropChange: Position => Unit,
      crop: Position = position(0, 0),
      aspect: Option[Double] = None,
      onMediaLoaded: Option[() => Unit] = None,
      onCropComplete: Option[(Area, Area) => Unit] = None
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

  def getCroppedImg(imageUrl: String, crop: Area): Future[dom.Blob] = {
    val promise = Promise[dom.Blob]()
    Browser.createImage(imageUrl).onComplete {
      case Success(image) => {
        var canvas = dom.document.createElement("canvas")
          .asInstanceOf[dom.HTMLCanvasElement]
        var ctx = canvas.getContext("2d")
        if (ctx != null) {
          canvas.width = crop.width.toInt
          canvas.height = crop.height.toInt

          var ctx2d = ctx.asInstanceOf[dom.CanvasRenderingContext2D]
          ctx2d.drawImage(
            image,

            // the cropped area in the image
            crop.x,
            crop.y,
            crop.width,
            crop.height,

            // the target area in the canvas
            0,
            0,
            crop.width,
            crop.height
          )

          var canvas2 = canvas.asInstanceOf[ToBlob]
          canvas2.toBlob(blob => promise.success(blob))
        } else {
          promise.failure(new RuntimeException("2D context is not supported."))
        }
      }
      case Failure(t) => promise.failure(t)
    }
    promise.future
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
