package cotoami.backend

import scala.scalajs.js

@js.native
trait BackendEventJson extends js.Object {
  val LocalChange: js.UndefOr[ChangelogEntryJson] = js.native
}
