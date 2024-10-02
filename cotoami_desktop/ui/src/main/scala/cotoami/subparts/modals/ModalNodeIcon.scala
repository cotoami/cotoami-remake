package cotoami.subparts.modals

import scala.util.{Failure, Success}
import scala.util.chaining._
import scala.scalajs.js
import org.scalajs.dom

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.{Fragment, ReactElement}
import slinky.core.facade.Hooks._
import slinky.web.html._

import fui.{Browser, Cmd}
import cotoami.{log_error, Context, Into, Msg => AppMsg}
import cotoami.models.Node
import cotoami.repositories.Nodes
import cotoami.backend.{ErrorJson, NodeBackend}
import cotoami.components.FixedAspectCrop
import cotoami.components.FixedAspectCrop.Area
import cotoami.subparts.{InputFile, Modal}

object ModalNodeIcon {

  case class Model(
      sourceImage: Option[(dom.Blob, String)] = None,
      croppedImage: Option[dom.Blob] = None,
      cropping: Boolean = false,
      saving: Boolean = false,
      error: Option[String] = None
  ) {
    def readyToSave: Boolean =
      this.croppedImage.isDefined && !this.cropping && !this.saving
  }

  sealed trait Msg extends Into[AppMsg] {
    def into = Modal.Msg.NodeIconMsg(this).pipe(AppMsg.ModalMsg)
  }

  object Msg {
    case class ImageInput(image: dom.Blob) extends Msg
    case object CropStarted extends Msg
    case class ImageCropped(result: Either[Throwable, dom.Blob]) extends Msg
    case object Save extends Msg
    case class Saved(result: Either[ErrorJson, Node]) extends Msg
  }

  def update(msg: Msg, model: Model)(implicit
      context: Context
  ): (Model, Nodes, Cmd[AppMsg]) = {
    val default = (model, context.domain.nodes, Cmd.none)
    msg match {
      case Msg.ImageInput(image) =>
        default.copy(_1 =
          model.copy(sourceImage = Some(image, dom.URL.createObjectURL(image)))
        )

      case Msg.CropStarted =>
        default.copy(_1 = model.copy(cropping = true))

      case Msg.ImageCropped(Right(image)) =>
        default.copy(_1 =
          model.copy(cropping = false, croppedImage = Some(image))
        )

      case Msg.ImageCropped(Left(t)) =>
        default.copy(
          _1 = model.copy(cropping = false),
          _3 = log_error("Icon cropping error.", Some(t.toString()))
        )

      case Msg.Save =>
        default.copy(
          _1 = model.copy(saving = true),
          _3 = model.croppedImage.map(image =>
            Browser.encodeAsBase64(image, true).flatMap {
              case Right(base64) =>
                NodeBackend.setLocalNodeIcon(base64).map(Msg.Saved(_).into)
              case Left(e) =>
                log_error("Icon encoding error.", Some(js.JSON.stringify(e)))
            }
          ).getOrElse(Cmd.none)
        )

      case Msg.Saved(Right(node)) =>
        default.copy(
          _1 = model.copy(saving = false),
          _2 = context.domain.nodes.put(node),
          _3 = Modal.close(classOf[Modal.NodeIcon])
        )

      case Msg.Saved(Left(e)) =>
        default.copy(
          _1 = model.copy(saving = false, error = Some(e.default_message)),
          _3 = log_error("Icon saving error.", Some(js.JSON.stringify(e)))
        )
    }
  }

  def apply(
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    Modal.view(
      elementClasses = "image",
      closeButton = Some((classOf[Modal.NodeIcon], dispatch)),
      error = model.error
    )(
      "Change Node Icon"
    )(
      model.sourceImage.map(image => divPreview(image._2, model))
        .getOrElse(
          InputFile(
            accept = js.Dictionary("image/*" -> js.Array[String]()),
            message = Fragment(
              "Drag and drop an image file here,",
              br(),
              "or click to select one"
            ),
            onSelect = file => dispatch(Msg.ImageInput(file))
          )
        )
    )

  private def divPreview(
      imageUrl: String,
      model: Model
  )(implicit dispatch: Into[AppMsg] => Unit): ReactElement =
    div(className := "preview")(
      SectionCrop(
        imageUrl = imageUrl,
        dispatch = dispatch
      ),
      div(className := "buttons")(
        button(
          `type` := "button",
          className := "cancel contrast outline",
          onClick := (_ =>
            dispatch(Modal.Msg.CloseModal(classOf[Modal.NodeIcon]))
          )
        )("Cancel"),
        button(
          `type` := "button",
          disabled := !model.readyToSave,
          aria - "busy" := model.saving.toString(),
          onClick := (_ => dispatch(Msg.Save))
        )("OK")
      )
    )

  @react object SectionCrop {
    // https://github.com/scala-js/scala-js-macrotask-executor
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

    case class Props(
        imageUrl: String,
        dispatch: Into[AppMsg] => Unit
    )

    val component = FunctionalComponent[Props] { props =>
      val (crop, setCrop) = useState(FixedAspectCrop.position(0, 0))

      useEffect(
        () => { () => dom.URL.revokeObjectURL(props.imageUrl) },
        Seq.empty
      )

      section(className := "crop")(
        FixedAspectCrop(
          image = props.imageUrl,
          crop = crop,
          onCropChange = setCrop,
          aspect = Some(1.0),
          onCropComplete =
            Some((croppedArea: Area, croppedAreaPixels: Area) => {
              props.dispatch(Msg.CropStarted)
              FixedAspectCrop.getCroppedImg(
                props.imageUrl,
                croppedAreaPixels
              ).onComplete {
                case Success(blob) =>
                  props.dispatch(Msg.ImageCropped(Right(blob)))
                case Failure(t) =>
                  props.dispatch(Msg.ImageCropped(Left(t)))
              }
            })
        )
      )
    }
  }
}
