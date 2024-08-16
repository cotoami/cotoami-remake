package cotoami.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** The event system allows you to emit events to the backend and listen to
  * events from it.
  */
@js.native
@JSImport("@tauri-apps/api/event", JSImport.Namespace)
object event extends js.Object {

  @js.native
  trait Event[T] extends js.Object {
    // Event name
    val event: String = js.native

    // Event identifier used to unlisten
    val id: Double = js.native

    // Event payload
    val payload: T = js.native

    // The label of the window that emitted this event
    val windowLabel: String = js.native
  }

  /** Emits an event to the backend and all Tauri windows.
    *
    * <https://tauri.app/v1/api/js/event/#emit>
    *
    * @param event
    *   Event name. Must include only alphanumeric characters, -, /, : and _.
    * @param payload
    *   Object payload.
    * @return
    */
  def emit(event: String, payload: js.Object): js.Promise[Unit] = js.native

  /** Listen to an event. The event can be either global or window-specific. See
    * windowLabel to check the event source.
    *
    * <https://tauri.app/v1/api/js/event/#listen>
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

  /** Listen to an one-off event. See listen for more information.
    *
    * <https://tauri.app/v1/api/js/event/#once>
    */
  def once[T](
      event: String,
      handler: js.Function1[Event[T], Unit]
  ): js.Promise[js.Function0[Unit]] = js.native
}
