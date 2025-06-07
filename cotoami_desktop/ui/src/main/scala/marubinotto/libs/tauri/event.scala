package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** The event system allows you to emit events to the backend and listen to
  * events from it.
  *
  * https://v2.tauri.app/reference/javascript/api/namespaceevent/
  */
@js.native
@JSImport("@tauri-apps/api/event", JSImport.Namespace)
object event extends js.Object {

  @js.native
  trait Event[T] extends js.Object {

    /** Event name
      */
    val event: String = js.native

    /** Event identifier used to unlisten
      */
    val id: Double = js.native

    /** Event payload
      */
    val payload: T = js.native
  }

  /** Emits an event to all targets.
    *
    * https://v2.tauri.app/reference/javascript/api/namespaceevent/#emit
    *
    * @param event
    *   Event name. Must include only alphanumeric characters, -, /, : and _.
    * @param payload
    *   Object payload.
    * @return
    */
  def emit(event: String, payload: js.Object): js.Promise[Unit] = js.native

  /** Listen to an emitted event to any target.
    *
    * https://v2.tauri.app/reference/javascript/api/namespaceevent/#listen
    *
    * @param event
    *   Event name. Must include only alphanumeric characters, -, /, : and _.
    * @param handler
    *   Event handler callback.
    * @return
    *   A promise resolving to a function to unlisten to the event. Note that
    *   removing the listener is required if your listener goes out of scope
    *   e.g. the component is unmounted.
    */
  def listen[T](
      event: String,
      handler: js.Function1[Event[T], Unit]
  ): js.Promise[js.Function0[Unit]] = js.native

  /** Listens once to an emitted event to any target.
    *
    * https://v2.tauri.app/reference/javascript/api/namespaceevent/#once
    */
  def once[T](
      event: String,
      handler: js.Function1[Event[T], Unit]
  ): js.Promise[js.Function0[Unit]] = js.native
}
