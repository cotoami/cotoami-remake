package cotoami.libs

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object rehypePlugins {
  // https://github.com/rehypejs/rehype-external-links
  @js.native
  @JSImport("rehype-external-links", JSImport.Default)
  object externalLinks extends js.Object
}
