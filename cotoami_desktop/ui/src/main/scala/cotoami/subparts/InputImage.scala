package cotoami.subparts

import scala.scalajs.js
import org.scalajs.dom

import slinky.core._
import slinky.core.facade.React
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.{Msg => AppMsg}
import cotoami.components.optionalClasses
import cotoami.components.ReactDropzone._

@react object InputImage {
  case class Props(
      tagger: dom.Blob => AppMsg,
      dispatch: AppMsg => Unit
  )

  val component = FunctionalComponent[Props] { props =>
    val onDropCallback: OnDrop = useCallback(
      (accepted, rejected, event) => {
        if (accepted.length > 0) {
          props.dispatch(props.tagger(accepted(0)))
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
