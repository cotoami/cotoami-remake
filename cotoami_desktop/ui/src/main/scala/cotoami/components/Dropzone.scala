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
    val onDrop: js.UndefOr[
      js.Function3[js.Array[dom.File], js.Array[FileRejection], js.Any, Unit]
    ] = js.undefined
  }

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
}

@js.native
trait Dropzone extends js.Object {
  def getRootProps(): Any = js.native
  def getInputProps(): Any = js.native
  def isDragActive(): Boolean = js.native
}
