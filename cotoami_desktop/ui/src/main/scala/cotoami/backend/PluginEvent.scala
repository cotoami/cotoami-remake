package cotoami.backend

import scala.scalajs.js

@js.native
trait PluginEventJson extends js.Object {
  val Registered: js.UndefOr[RegisteredJson] = js.native
  val Error: js.UndefOr[String] = js.native
}

@js.native
trait RegisteredJson extends js.Object {
  val identifier: String = js.native
}
