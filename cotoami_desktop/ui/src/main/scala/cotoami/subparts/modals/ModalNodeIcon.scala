package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import org.scalajs.dom

import slinky.core.facade.ReactElement
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.components.EasyCrop
import cotoami.subparts.{InputImage, Modal}

object ModalNodeIcon {

  case class Model(
      image: Option[dom.Blob] = None
  )

  sealed trait Msg {
    def toApp: AppMsg =
      Modal.Msg.NodeIconMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.NodeIconMsg andThen AppMsg.ModalMsg

    case class ImageInput(image: dom.Blob) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.ImageInput(image) => (model.copy(image = Some(image)), Seq.empty)
    }

  def apply(model: Model, dispatch: AppMsg => Unit): ReactElement =
    Modal.view(
      elementClasses = "image",
      closeButton = Some((classOf[Modal.NodeIcon], dispatch))
    )(
      "Node Icon"
    )(
      model.image.map(image => {
        val url = dom.URL.createObjectURL(image)
        section(className := "crop")(
          EasyCrop(
            image = url,
            onCropChange = (crop: EasyCrop.Position) =>
              println(s"crop: ${js.JSON.stringify(crop)}"),
            onMediaLoaded = Some(() => {
              println("onMediaLoaded")
              dom.URL.revokeObjectURL(url)
            }),
            aspect = Some(1.0)
          )
        )
      }).getOrElse(
        InputImage(tagger = Msg.toApp(Msg.ImageInput(_)), dispatch = dispatch)
      )
    )
}
