package cotoami.backend

import scala.scalajs.js

@js.native
trait PluginEventJson extends js.Object {
  import PluginEventJson._

  val Registered: js.UndefOr[Registered] = js.native
  val InvalidFile: js.UndefOr[InvalidFile] = js.native
  val Info: js.UndefOr[Info] = js.native
  val Error: js.UndefOr[Error] = js.native
  val Destroyed: js.UndefOr[Destroyed] = js.native
}

object PluginEventJson {
  @js.native
  trait Registered extends js.Object {
    val identifier: String = js.native
    val name: String = js.native
    val version: String = js.native
  }

  @js.native
  trait InvalidFile extends js.Object {
    val path: String = js.native
    val message: String = js.native
  }

  @js.native
  trait Info extends js.Object {
    val identifier: String = js.native
    val message: String = js.native
  }

  @js.native
  trait Error extends js.Object {
    val identifier: String = js.native
    val message: String = js.native
  }

  @js.native
  trait Destroyed extends js.Object {
    val identifier: String = js.native
  }
}
