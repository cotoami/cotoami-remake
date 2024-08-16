package cotoami.libs

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import org.scalajs.dom

// https://react-dropzone.js.org/
@js.native
@JSImport("react-dropzone", JSImport.Namespace)
object reactDropzone extends js.Object {
  def useDropzone(options: Options): Dropzone = js.native

  // https://react-dropzone.js.org/#src
  trait Options extends js.Object {
    // Set accepted file types.
    val accept: js.UndefOr[js.Dictionary[js.Array[String]]] = js.undefined

    // Maximum accepted number of files The default value is 0 which means
    // there is no limitation to how many files are accepted.
    val maxFiles: js.UndefOr[Int] = js.undefined

    // Allow drag 'n' drop (or selection from the file dialog) of multiple files
    val multiple: js.UndefOr[Boolean] = js.undefined

    // Maximum file size (in bytes)
    val maxSize: js.UndefOr[Int] = js.undefined

    // Minimum file size (in bytes)
    val minSize: js.UndefOr[Int] = js.undefined

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
    // Whatever other props you want to add to the element where the props from
    // getRootProps() are set, you should always pass them through that function
    // rather than applying them on the element itself. This is in order to avoid
    // your props being overridden (or overriding the props returned by getRootProps()).
    def getRootProps(
        additionalProps: js.Dictionary[js.Any] = js.Dictionary.empty
    ): js.Dictionary[js.Any] = js.native
    def getInputProps(): js.Dictionary[js.Any] = js.native

    val isFocused: Boolean = js.native
    val isDragActive: Boolean = js.native

    // isDragAccept and isDragReject won't work on Safari
    // https://github.com/react-dropzone/react-dropzone/issues/1266
    val isDragAccept: Boolean = js.native
    val isDragReject: Boolean = js.native
  }
}
