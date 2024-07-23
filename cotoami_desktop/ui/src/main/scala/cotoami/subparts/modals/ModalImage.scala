package cotoami.subparts.modals

import scala.util.chaining._
import org.scalajs.dom

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.subparts.{InputImage, Modal}

object ModalImage {

  case class Model(
      title: String,
      image: Option[dom.Blob] = None
  )

  sealed trait Msg {
    def toApp: AppMsg =
      Modal.Msg.ImageMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.ImageMsg andThen AppMsg.ModalMsg

    case class ImageInput(image: dom.Blob) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.ImageInput(image) => (model.copy(image = Some(image)), Seq.empty)
    }

  def apply(model: Model, dispatch: AppMsg => Unit): ReactElement =
    Modal.view(
      elementClasses = "image",
      closeButton = Some((classOf[Modal.Image], dispatch))
    )(
      model.title
    )(
      model.image.map(image => {
        val url = dom.URL.createObjectURL(image)
        section(className := "preview")(
          img(
            src := url,
            // Revoke data uri after image is loaded
            onLoad := (_ => dom.URL.revokeObjectURL(url))
          )
        )
      }).getOrElse(
        InputImage(tagger = Msg.toApp(Msg.ImageInput(_)), dispatch = dispatch)
      )
    )
}
