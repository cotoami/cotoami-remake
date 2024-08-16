package cotoami.libs

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object remarkPlugins {
  // https://github.com/remarkjs/remark-breaks
  @js.native
  @JSImport("remark-breaks", JSImport.Default)
  object breaks extends js.Object
}
