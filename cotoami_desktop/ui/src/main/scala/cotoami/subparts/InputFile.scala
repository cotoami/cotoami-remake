package cotoami.subparts

import scala.scalajs.js
import org.scalajs.dom

import slinky.core._
import slinky.core.facade.{React, ReactElement}
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import cotoami.components.optionalClasses
import cotoami.components.ReactDropzone._

@react object InputFile {
  case class Props(
      accept: js.Dictionary[js.Array[String]],
      message: ReactElement,
      onSelect: dom.Blob => Unit
  )

  val component = FunctionalComponent[Props] { props =>
    val onDropCallback: OnDrop = useCallback(
      (accepted, rejected, event) => {
        if (accepted.length > 0) {
          props.onSelect(accepted(0))
        }
      },
      Seq(props.onSelect)
    )
    val dropzone = useDropzone(new Options {
      override val accept = props.accept
      override val maxFiles = 1
      override val multiple = false
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
      p()(props.message)
    )
  }
}
