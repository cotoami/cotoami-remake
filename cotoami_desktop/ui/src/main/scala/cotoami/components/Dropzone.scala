package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom

// https://react-dropzone.js.org/
@js.native
@JSImport("react-dropzone", JSImport.Namespace)
object ReactDropzone extends js.Object {
  def useDropzone(options: Options): Dropzone = js.native

  trait Options extends js.Object {
    // Set accepted file types.
    val accept: js.UndefOr[js.Dictionary[js.Array[String]]] = js.undefined

    // Callback for when the drop event occurs.
    val onDrop: js.UndefOr[OnDrop] = js.undefined
  }

  // OnDrop arguments
  // 1. acceptedFiles: Array.<File>
  // 2. fileRejections: Array.<FileRejection>
  // 3. event: (DragEvent | Event) — A drag event or input change event
  //    (if files were selected via the file dialog)
  type OnDrop =
    js.Function3[js.Array[dom.File], js.Array[FileRejection], js.Any, Unit]

  @js.native
  trait FileRejection extends js.Object {
    val file: dom.File = js.native
    val errors: js.Array[Error] = js.native
  }

  @js.native
  trait Error extends js.Object {
    val code: String = js.native
    val message: String = js.native
  }

  @js.native
  trait Dropzone extends js.Object {
    def getRootProps(): js.Dictionary[js.Any] = js.native
    def getInputProps(): js.Dictionary[js.Any] = js.native
    val isDragActive: Boolean = js.native
  }
}
