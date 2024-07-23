package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import org.scalajs.dom

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement
import slinky.core.facade.Hooks._
import slinky.web.html._

import fui.Cmd
import cotoami.{log_error, Msg => AppMsg}
import cotoami.components.EasyCrop
import cotoami.components.EasyCrop.Area
import cotoami.backend.{ErrorJson, Node}
import cotoami.subparts.{InputImage, Modal}

object ModalNodeIcon {

  case class Model(
      sourceImage: Option[dom.Blob] = None,
      croppedImage: Option[dom.Blob] = None,
      saving: Boolean = false,
      error: Option[String] = None
  )

  sealed trait Msg {
    def toApp: AppMsg =
      Modal.Msg.NodeIconMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    def toApp[T](tagger: T => Msg): T => AppMsg =
      tagger andThen Modal.Msg.NodeIconMsg andThen AppMsg.ModalMsg

    case class ImageInput(image: dom.Blob) extends Msg
    case object Save extends Msg
    case class Saved(result: Either[ErrorJson, Node]) extends Msg
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    msg match {
      case Msg.ImageInput(image) =>
        (
          model.copy(sourceImage = Some(image), croppedImage = Some(image)),
          Seq.empty
        )

      case Msg.Save =>
        (
          model.copy(saving = true),
          Seq(
          )
        )

      case Msg.Saved(Right(node)) =>
        (model.copy(saving = false), Seq.empty)

      case Msg.Saved(Left(e)) =>
        (
          model.copy(error = Some(e.default_message)),
          Seq(
            log_error("Icon saving error.", Some(js.JSON.stringify(e)))
          )
        )
    }

  def apply(model: Model, dispatch: AppMsg => Unit): ReactElement =
    Modal.view(
      elementClasses = "image",
      closeButton = Some((classOf[Modal.NodeIcon], dispatch)),
      error = model.error
    )(
      "Change Node Icon"
    )(
      model.sourceImage.map(divPreview(_, model, dispatch))
        .getOrElse(
          InputImage(tagger = Msg.toApp(Msg.ImageInput(_)), dispatch = dispatch)
        )
    )

  private def divPreview(
      image: dom.Blob,
      model: Model,
      dispatch: AppMsg => Unit
  ): ReactElement =
    div(className := "preview")(
      SectionCrop(imageUrl = dom.URL.createObjectURL(image)),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ =>
            dispatch(Modal.Msg.CloseModal(classOf[Modal.NodeIcon]).toApp)
          )
        )("Cancel"),
        button(
          `type` := "button",
          aria - "busy" := model.saving.toString()
        )("OK")
      )
    )

  @react object SectionCrop {
    case class Props(
        imageUrl: String
    )

    val component = FunctionalComponent[Props] { props =>
      val (crop, setCrop) = useState(EasyCrop.position(0, 0))

      useEffect(
        () => { () => dom.URL.revokeObjectURL(props.imageUrl) },
        Seq.empty
      )

      section(className := "crop")(
        EasyCrop(
          image = props.imageUrl,
          crop = crop,
          onCropChange = setCrop,
          aspect = Some(1.0),
          onCropComplete =
            Some((croppedArea: Area, croppedAreaPixels: Area) => {
              //
            })
        )
      )
    }
  }
}
