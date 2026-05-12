package marubinotto.libs.unified

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object htmlToMarkdownPlugins {
  // https://github.com/rehypejs/rehype/tree/main/packages/rehype-parse
  @js.native
  @JSImport("rehype-parse",JSImport.Default)
  object RehypeParse extends js.Object

  // https://github.com/rehypejs/rehype-remark
  @js.native
  @JSImport("rehype-remark",JSImport.Default)
  object RehypeRemark extends js.Object

  // https://github.com/remarkjs/remark/tree/main/packages/remark-stringify
  @js.native
  @JSImport("remark-stringify",JSImport.Default)
  object RemarkStringify extends js.Object
}
