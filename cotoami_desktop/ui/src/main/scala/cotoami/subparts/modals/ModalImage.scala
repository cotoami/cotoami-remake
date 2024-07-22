package cotoami.subparts.modals

import scala.util.chaining._
import scala.scalajs.js

import slinky.core._
import slinky.core.facade.{React, ReactElement}
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import fui.Cmd
import cotoami.{Msg => AppMsg}
import cotoami.components.ReactDropzone._
import cotoami.subparts.Modal

object ModalImage {

  case class Model(
      title: String
  )

  sealed trait Msg {
    def toApp: AppMsg =
      Modal.Msg.ImageMsg(this).pipe(AppMsg.ModalMsg)
  }

  def update(msg: Msg, model: Model): (Model, Seq[Cmd[AppMsg]]) =
    (model, Seq.empty)

  def apply(model: Model, dispatch: AppMsg => Unit): ReactElement =
    Modal.view(
      elementClasses = "image",
      closeButton = Some((classOf[Modal.Image], dispatch))
    )(
      model.title
    )(
      InputFile(model = model)
    )

  @react object InputFile {
    case class Props(
        model: Model
    )

    val component = FunctionalComponent[Props] { props =>
      val onDropCallback: OnDrop = useCallback(
        (accepted, rejected, event) => {
          println(s"accepted: ${js.JSON.stringify(accepted)}")
        },
        Seq.empty
      )
      val dropzone = useDropzone(new Options {
        override val onDrop = onDropCallback
      })

      React.createElement(
        "section",
        dropzone.getRootProps(),
        React.createElement("input", dropzone.getInputProps()),
        if (dropzone.isDragActive)
          p()("Drop the file here ..")
        else
          p()("Drag 'n' drop an image file here, or click to select one")
      )
    }
  }
}
