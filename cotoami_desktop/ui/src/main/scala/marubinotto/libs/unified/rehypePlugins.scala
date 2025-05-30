package marubinotto.libs.unified

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object rehypePlugins {
  // https://github.com/rehypejs/rehype-external-links
  @js.native
  @JSImport("rehype-external-links", JSImport.Default)
  object ExternalLinks extends js.Object

  // https://github.com/rehypejs/rehype-highlight
  @js.native
  @JSImport("rehype-highlight", JSImport.Default)
  object Highlight extends js.Object
}
