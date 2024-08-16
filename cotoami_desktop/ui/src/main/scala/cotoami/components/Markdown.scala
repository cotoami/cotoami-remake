package cotoami.components

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.ReactElement

@js.native
@JSImport("react-markdown", JSImport.Default)
object ReactMarkdown extends js.Object

@react object Markdown extends ExternalComponent {
  case class Props(
      remarkPlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      rehypePlugins: Seq[js.Object | js.Tuple2[js.Object, js.Object]],
      children: ReactElement*
  )

  override val component = ReactMarkdown
}

object remarkPlugins {
  // https://github.com/remarkjs/remark-breaks
  @js.native
  @JSImport("remark-breaks", JSImport.Default)
  object breaks extends js.Object
}

object rehypePlugins {
  // https://github.com/rehypejs/rehype-external-links
  @js.native
  @JSImport("rehype-external-links", JSImport.Default)
  object externalLinks extends js.Object
}
