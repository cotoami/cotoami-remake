package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js
import org.scalajs.dom

import slinky.core._
import slinky.core.facade.{React, ReactElement}
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.components.optionalClasses
import cotoami.components.ReactDropzone._
import cotoami.subparts.Modal

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
      InputFile(model = model, dispatch = dispatch),
      model.image.map(image => {
        val url = dom.URL.createObjectURL(image)
        section(className := "preview")(
          img(
            src := url,
            // Revoke data uri after image is loaded
            onLoad := (_ => dom.URL.revokeObjectURL(url))
          )
        )
      })
    )

  @react object InputFile {
    case class Props(
        model: Model,
        dispatch: AppMsg => Unit
    )

    val component = FunctionalComponent[Props] { props =>
      val onDropCallback: OnDrop = useCallback(
        (accepted, rejected, event) => {
          if (accepted.length > 0) {
            props.dispatch(Msg.ImageInput(accepted(0)).toApp)
          }
        },
        Seq.empty
      )
      val dropzone = useDropzone(new Options {
        override val accept = js.Dictionary("image/*" -> js.Array[String]())
        override val onDrop = onDropCallback
      })

      React.createElement(
        "section",
        dropzone.getRootProps(
          js.Dictionary(
            "className" -> optionalClasses(
              Seq(
                ("input-file", true),
                ("drag-active", dropzone.isDragActive)
              )
            )
          )
        ),
        React.createElement("input", dropzone.getInputProps()),
        p()("Drag and drop an image file here, or click to select one")
      )
    }
  }
}
