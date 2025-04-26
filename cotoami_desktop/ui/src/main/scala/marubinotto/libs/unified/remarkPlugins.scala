package marubinotto.libs.unified

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object remarkPlugins {
  // https://github.com/remarkjs/remark-breaks
  @js.native
  @JSImport("remark-breaks", JSImport.Default)
  object Breaks extends js.Object

  // https://github.com/remarkjs/strip-markdown
  @js.native
  @JSImport("strip-markdown", JSImport.Default)
  object StripMarkdown extends js.Object

  // https://github.com/remarkjs/remark-gfm
  @js.native
  @JSImport("remark-gfm", JSImport.Default)
  object RemarkGfm extends js.Object
}
