package cotoami.backend

import scala.scalajs.js

@js.native
trait PluginEventJson extends js.Object {
  import PluginEventJson._

  val Registered: js.UndefOr[Registered] = js.native
  val Error: js.UndefOr[Error] = js.native
}

object PluginEventJson {
  @js.native
  trait Registered extends js.Object {
    val identifier: String = js.native
  }

  @js.native
  trait Error extends js.Object {
    val identifier: String = js.native
    val message: String = js.native
  }
}
