package marubinotto.react

import scala.scalajs.js
import org.scalajs.dom

@js.native
trait SyntheticEvent[+T, +E] extends js.Object {
  val currentTarget: T = js.native
  val target: dom.EventTarget = js.native
  val nativeEvent: E = js.native
  val defaultPrevented: Boolean = js.native
  def preventDefault(): Unit = js.native
  def stopPropagation(): Unit = js.native
}
@js.native
trait SyntheticMouseEvent[+T] extends SyntheticEvent[T, dom.MouseEvent] {
  val clientX: Double = js.native
  val clientY: Double = js.native
  val pageX: Double = js.native
  val pageY: Double = js.native
  val button: Int = js.native
  val buttons: Int = js.native
  val shiftKey: Boolean = js.native
  val ctrlKey: Boolean = js.native
  val metaKey: Boolean = js.native
  val altKey: Boolean = js.native
}
@js.native
trait SyntheticKeyboardEvent[+T] extends SyntheticEvent[T, dom.KeyboardEvent] {
  val key: String = js.native
  val code: String = js.native
  val keyCode: Int = js.native
  val repeat: Boolean = js.native
  val location: Int = js.native
  val shiftKey: Boolean = js.native
  val ctrlKey: Boolean = js.native
  val metaKey: Boolean = js.native
  val altKey: Boolean = js.native
}
